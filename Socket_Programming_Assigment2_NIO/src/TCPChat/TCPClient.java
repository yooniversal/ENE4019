package TCPChat;

import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.BorderLayout;
import java.io.*;
import java.net.*;
import java.nio.channels.SocketChannel;

public class TCPClient extends JFrame implements ActionListener {

    private static final long serialVersionUID = 2018062733L;

    private JTextField textField;  //text 입력
    private JTextArea textArea;    //text 출력
    private BufferedReader in;     //ClientService로부터 정보 받는 입력스트림
    private PrintWriter out;       //ClientService로 정보 보내는 출력스트림
    private Socket serverSocket;
    private Socket fileSocket;
    private String ip;
    private int port1;
    private int port2;

    //port 설정 및 채팅방 실행
    public TCPClient(String title, String ip, int port1, int port2) throws IOException {
        super(title);
        this.ip = ip;
        this.port1 = port1;
        this.port2 = port2;

        textField = new JTextField();
        textArea = new JTextArea();
        JScrollPane pane = new JScrollPane(textArea); // 스크롤바

        getContentPane().add(textField, BorderLayout.SOUTH);   //텍스트 입력창 -> 하단
        getContentPane().add(pane, BorderLayout.CENTER);       //텍스트 출력창 -> 중앙

        textField.addActionListener(this);   //입력되는 텍스트 인식
        textArea.setFocusable(false);          //입력창에만 쓸 수 있도록 함

        setVisible(true);
        setBounds(300, 50, 400, 300);

        serverSocket = new Socket(ip, port1);
        fileSocket = new Socket(ip, port2);

        in = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
        out = new PrintWriter(serverSocket.getOutputStream(), true);

        //clientService에서 온 msg를 채팅방 화면에 출력
        while(true) {
            String msg = in.readLine();
            String[] msgs = msg.split("\\s+"); //공백을 기준으로 파싱

            if(msgs[0].equals("#GET")) {
                //#GET: client -> server file 다운로드

                String fileName = msgs[1];

                InputStream is = fileSocket.getInputStream();
                FileOutputStream fos = new FileOutputStream("./client/download/" + fileName);

                byte[] fileBuf = new byte[65536];
                int n;
                boolean errorFlag = false;

                while ((n = is.read(fileBuf)) != -1) {
                    String check = new String(fileBuf).trim();
                    if(check.equals("@ERROR@")) {
                        errorFlag = true;
                        break;
                    }

                    fos.write(fileBuf, 0, n);

                    textArea.append("#");
                    textArea.setCaretPosition(textArea.getText().length());

                    int remainSize = is.available();
                    if(remainSize == 0) break;
                }

                fos.flush();
                fos.close();

                if(errorFlag) {
                    File file = new File("./client/download/" + fileName);
                    file.delete();
                } else {
                    //채팅방에 해당 msg 표시
                    textArea.append("\n" + fileName + " 다운로드가 완료되었습니다." + "\n");
                    //채팅량이 많을 때 최신 내용을 먼저 볼 수 있도록 함
                    textArea.setCaretPosition(textArea.getText().length());
                }

            } else if(msg.equals("#EXIT")) {
                //채팅방 종료
                System.exit(0);
            } else if(msg.equals("#JOIN") || msg.equals("#STATUS")) {
                //채팅방 입장 시 내용 초기화
                textArea.setText("");
            } else {
                //채팅방에 해당 msg 표시
                textArea.append(msg + "\n");

                //채팅량이 많을 때 최신 내용을 먼저 볼 수 있도록 함
                textArea.setCaretPosition(textArea.getText().length());
            }
        }
    }

    //client가 chat에서 입력한 text를 clientService로 전달
    @Override
    public void actionPerformed(ActionEvent e) {
        String command = textField.getText();
        textField.setText("");

        //공백 무시
        if(command.equals("")) return;

        String[] commands = command.split("\\s+");
        if(commands[0].equals("#PUT")) {
            //#PUT: client -> server file 업로드

            String fileName = commands[1];

            try {

                FileInputStream fis = new FileInputStream("./client/upload/" + fileName);
                OutputStream os = fileSocket.getOutputStream();

                textArea.append("file 업로드를 시작합니다." + "\n");
                textArea.setCaretPosition(textArea.getText().length());

                byte[] fileBuf = new byte[65536];
                int n;

                while ((n = fis.read(fileBuf)) > 0) {
                    os.write(fileBuf, 0, n);

                    textArea.append("#");
                    textArea.setCaretPosition(textArea.getText().length());
                }

                textArea.append("\n");
                textArea.setCaretPosition(textArea.getText().length());

            } catch (FileNotFoundException fne) {
                fne.printStackTrace();

                textArea.append("일치하는 file이 존재하지 않습니다." + "\n");
                textArea.setCaretPosition(textArea.getText().length());

                OutputStream os;
                try {
                    os = fileSocket.getOutputStream();
                    os.write("@ERROR@".getBytes());
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }

            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        System.out.println("command: " + command);
        out.println(command);
    }

    public static void main(String[] args) throws IOException {
        // 커맨드로 port 입력받기
        String ip = args[0];
        int port1 = Integer.parseInt(args[1]);
        int port2 = Integer.parseInt(args[2]);

        TCPClient chat = new TCPClient("TCP Chat", ip, port1, port2);
    }
}