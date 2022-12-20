package UDPChat;

import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.BorderLayout;
import java.net.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class UDPChat extends JFrame implements ActionListener {

    private static final long serialVersionUID = 2018062733L;
    
    private JTextField textField;  //text 입력
    private JTextArea textArea;    //text 출력
    private String username;
    private MulticastSocket serverSocket;
    private InetAddress ip;
    private int port;

    //port 설정 및 채팅방 실행
    public UDPChat(String title, int port) throws IOException {
        super(title);
        this.port = port;
        serverSocket = new MulticastSocket(port);

        textField = new JTextField();
        textArea = new JTextArea();
        JScrollPane pane = new JScrollPane(textArea); // 스크롤바
        
        getContentPane().add(textField, BorderLayout.SOUTH);   //텍스트 입력창 -> 하단
        getContentPane().add(pane, BorderLayout.CENTER);        //텍스트 출력창 -> 중앙

        textField.addActionListener(this);   //입력되는 텍스트 인식
        textArea.setFocusable(false);          //입력창에만 쓸 수 있도록 함

        setVisible(true);
        setBounds(300, 50, 400, 300);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    //client가 입력한 text 처리
    @Override
    public void actionPerformed(ActionEvent e) {
        String command = textField.getText();
        textField.setText("");

        if(command.equals("")) return;

        if(command.charAt(0) == '#') {
            String[] msgs = command.split("\\s+"); //공백을 기준으로 파싱

            //예외 케이스 -> 전달 불가능
            if(msgs.length > 4) return;

            if(msgs[0].equals("#JOIN")) {
                //#JOIN (채팅방 이름) (사용자 이름)
                if(msgs.length != 3) {
                    textArea.append("'#JOIN (채팅방 이름) (사용자 이름)'으로 입력해야 합니다." + "\n");

                    //채팅량이 많을 때 최신 내용을 먼저 볼 수 있도록 함
                    textArea.setCaretPosition(textArea.getText().length());

                    return;
                }

                String roomname = msgs[1];
                username = msgs[2];

                if(username.equals("")) {
                    textArea.append("이름은 0자 이상이어야 합니다." + "\n");

                    //채팅량이 많을 때 최신 내용을 먼저 볼 수 있도록 함
                    textArea.setCaretPosition(textArea.getText().length());

                    return;
                }

                //채팅방 이름 -> IP로 변환
                int[] ipNumbers = {225, 0, 0, 0};
                try {
                    MessageDigest SHA256 = MessageDigest.getInstance("SHA-256");
                    SHA256.update(roomname.getBytes(StandardCharsets.UTF_8));
                    byte[] sha256Hash = SHA256.digest();

                    int x = Byte.toUnsignedInt(sha256Hash[sha256Hash.length-3]);
                    int y = Byte.toUnsignedInt(sha256Hash[sha256Hash.length-2]);
                    int z = Byte.toUnsignedInt(sha256Hash[sha256Hash.length-1]);

                    ipNumbers[1] = x;
                    ipNumbers[2] = y;
                    ipNumbers[3] = z;

                } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
                    noSuchAlgorithmException.printStackTrace();
                }

                //255.x.y.z
                String ipNumber = Integer.toString(ipNumbers[0]) + "." + Integer.toString(ipNumbers[1]) + "." +
                        Integer.toString(ipNumbers[2]) + "." + Integer.toString(ipNumbers[3]);

                try {
                    ip = InetAddress.getByName(ipNumber);
                    server();

                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }

            } else if(msgs[0].equals("#EXIT")) {
                //#EXIT
                try {

                    if(ip != null) {
                        send(serverSocket, username + " 님이 퇴장하셨습니다.");
                        System.out.println(ip.getHostAddress() + " 에서 접속을 종료했습니다.");
                        serverSocket.leaveGroup(ip);
                    }

                    System.exit(0);

                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }

            } else {
                //예외 케이스 -> 전달 불가능
                textArea.append("입장하려면 #JOIN (채팅방 이름) (사용자 이름)을," + "\n");
                textArea.append("나가려면 #EXIT를 입력해주세요." + "\n");

                //채팅량이 많을 때 최신 내용을 먼저 볼 수 있도록 함
                textArea.setCaretPosition(textArea.getText().length());
            }

        } else {
            //채팅방에 입장하지 않은 경우
            if(ip == null) {
                textArea.append("입장하려면 #JOIN (채팅방 이름) (사용자 이름)을," + "\n");
                textArea.append("나가려면 #EXIT를 입력해주세요." + "\n");

                //채팅량이 많을 때 최신 내용을 먼저 볼 수 있도록 함
                textArea.setCaretPosition(textArea.getText().length());

                return;
            }

            //server로 msg 전달
            try {
                send(serverSocket, username + ": " + command);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    public void server() throws IOException {

        //thread가 돌면서 다른 client가 보낸 text를 채팅방에 뿌려주는 역할을 반복함
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {

                    //채팅방 설정
                    serverSocket.joinGroup(ip);

                    //client 입장 msg
                    send(serverSocket, username + " 님이 접속하셨습니다.");

                    //다른 client의 msg를 받아서 채팅방에 표시
                    while(true) {
                        //client -> server로의 msg 전달
                        String clientMsg = receive(serverSocket);

                        //채팅방에 해당 msg 표시
                        textArea.append(clientMsg + "\n");

                        //채팅량이 많을 때 최신 내용을 먼저 볼 수 있도록 함
                        textArea.setCaretPosition(textArea.getText().length());
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("[Error] Connetion error");
                }
            }
        };

        Thread thd = new Thread(r);
        thd.start();
    }

    //입력한 msg를 모든 client에게 전송
    public void send(MulticastSocket socket, String msg) throws IOException {
        byte[] chunk = new byte[512];
        chunk = msg.getBytes();
        DatagramPacket packet = new DatagramPacket(chunk, chunk.length, ip, port);
        socket.send(packet);
    }

    //client로부터 오는 msg 받고 String으로 변환
    public String receive(MulticastSocket socket) throws IOException {
        byte[] chunk = new byte[512];
        DatagramPacket packet = new DatagramPacket(chunk, chunk.length);
        socket.receive(packet);
        return new String(packet.getData(), 0, packet.getLength());
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(args[0]); // 커맨드로 port 입력받기
        UDPChat chat = new UDPChat("UDP Chatroom", port);
    }
}
