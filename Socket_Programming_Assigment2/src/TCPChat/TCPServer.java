package TCPChat;

import java.io.*;
import java.net.*;
import java.util.Vector;

public class TCPServer {

    private ServerSocket serverSocket;
    private ServerSocket fileSocket;
    private Vector<ChatRoom> rooms;
    private int port1;
    private int port2;

    //port 설정 및 채팅방 실행
    public TCPServer(int port1, int port2) {
        rooms = new Vector<>();
        this.port1 = port1;
        this.port2 = port2;

        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {

                    //socket 설정
                    serverSocket = new ServerSocket(port1);
                    fileSocket = new ServerSocket(port2);

                    //다른 client의 msg를 받아서 채팅방에 표시
                    while(true) {
                        //client-server 연결
                        Socket clientSocket = serverSocket.accept();
                        Socket clientFileSocket = fileSocket.accept();

                        //1명의 client에 대한 service 시작
                        ClientService clientService = new ClientService(clientSocket, clientFileSocket);

                        //각 client에 대한 service가 동시에 진행될 수 있도록 thread로써 동작
                        clientService.start();
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

    public class ClientService extends Thread {

        private Socket socket;       // server의 clientSocket과 연결
        private Socket fileSocket;   // server의 fileSocket과 연결
        private ChatRoom chatRoom;   // 현재 속해있는 채팅방
        private BufferedReader in;   // client(chat)  -> ClientService
        private PrintWriter out;     // ClientService -> client(chat)
        private String username;

        public ClientService(Socket clientSocket, Socket fileSocket) throws IOException {

            //필드 초기화
            socket = clientSocket;
            this.fileSocket = fileSocket;

            //serverSocket과 input/output 연결
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        }

        //커맨드에 따라 처리
        @Override
        public void run() {

            try {

                //client가 입력한 msg를 받아서 처리
                while(true) {
                    //client(chat) -> ClientService msg 전달
                    String clientMsg = in.readLine();

                    System.out.println("Client-msg: " + clientMsg);

                    //의미없는 msg 무시
                    if(clientMsg.equals("")) continue;

                    if(clientMsg.charAt(0) == '#') {
                        String[] msgs = clientMsg.split("\\s+"); //공백을 기준으로 파싱

                        //예외 케이스 -> 전달 불가능
                        if(msgs.length > 4) continue;

                        if(msgs[0].equals("#CREATE")) {
                            //#CREATE (생성할 채팅방의 이름) (사용자 이름)

                            //형식에 맞지 않은 케이스 처리
                            if(msgs.length != 3) {
                                String msg = "'#CREATE (생성할 채팅방의 이름) (사용자 이름)'으로 입력해야 합니다.";
                                out.println(msg);
                                continue;
                            }

                            String roomname = msgs[1];
                            username = msgs[2];

                            //이름이 입력되지 않은 경우
                            if(username.equals("")) {
                                String msg = "이름은 0자 이상이어야 합니다.";
                                out.println(msg);
                                continue;
                            }

                            //같은 이름의 채팅방이 이미 존재하는 경우
                            if(findChatRoom(roomname) != null) {
                                String msg = "같은 이름의 채팅방이 존재합니다.";
                                out.println(msg);
                                continue;
                            }

                            //채팅방 생성 및 설정
                            chatRoom = new ChatRoom(roomname);
                            rooms.add(chatRoom);
                            out.println("#STATUS");

                            //채팅방에 client 정보 등록
                            chatRoom.clients.add(this);

                            //채팅방에 속한 clients에게 접속 알림
                            String msg = username + " 님이 접속하셨습니다.";
                            msgToChatRoom(msg);

                        } else if(msgs[0].equals("#JOIN")) {
                            //#JOIN (채팅방 이름) (사용자 이름)

                            //형식에 맞지 않은 케이스 처리
                            if(msgs.length != 3) {
                                String msg = "'#JOIN (채팅방 이름) (사용자 이름)'으로 입력해야 합니다.";
                                out.println(msg);
                                continue;
                            }

                            String roomname = msgs[1];
                            username = msgs[2];

                            //이름이 입력되지 않은 경우
                            if(username.equals("")) {
                                String msg = "이름은 0자 이상이어야 합니다.";
                                out.println(msg);
                                continue;
                            }

                            //이미 채팅방에 들어가 있는 경우
                            if(chatRoom != null)
                                chatRoom.clients.remove(this);

                            //채팅방 설정
                            chatRoom = findChatRoom(roomname);

                            if(chatRoom == null) {
                                String msg = "같은 이름의 채팅방이 존재하지 않습니다.";
                                out.println(msg);
                                continue;
                            }

                            //채팅방에 client 정보 등록
                            out.println("#JOIN");
                            chatRoom.clients.add(this);

                            //채팅방에 속한 clients에게 접속 알림
                            String msg = username + " 님이 접속하셨습니다.";
                            msgToChatRoom(msg);

                        } else if(msgs[0].equals("#EXIT")) {
                            //#EXIT

                            if(chatRoom != null) {
                                msgToChatRoom(username + " 님이 퇴장하셨습니다.");

                                //채팅방에 client 정보 삭제
                                chatRoom.clients.remove(this);
                            }

                            if(username != null)
                                System.out.println(username + "님이 접속을 종료했습니다.");

                            String msg = "#EXIT";
                            out.println(msg);

                        } else if(msgs[0].equals("#PUT")) {
                            //#PUT (FileName)
                            //file 전송: client -> server

                            String fileName = msgs[1];

                            //client에게 file 전송 요청
                            out.println(clientMsg);

                            //client와 연결된 입력스트림
                            InputStream is = fileSocket.getInputStream();
                            //server에 저장하도록 하는 출력스트림
                            FileOutputStream fos = new FileOutputStream("./server/" + fileName);

                            byte[] fileBuf = new byte[65536];
                            int n;
                            boolean errorFlag = false;

                            //출력스트림을 통해 입력스트림에서 정보 전송
                            while ((n = is.read(fileBuf)) != -1) {
                                String check = new String(fileBuf).trim();
                                if(check.equals("@ERROR@")) {
                                    errorFlag = true;
                                    break;
                                }

                                fos.write(fileBuf, 0, n);

                                out.print("#");

                                int remainSize = is.available();
                                if(remainSize == 0) break;
                            }

                            fos.flush();
                            fos.close();

                            if(errorFlag) {
                                File file = new File("./server/" + fileName);
                                file.delete();
                            } else {
                                out.println("\n" + fileName + " 업로드가 완료되었습니다.");
                            }

                        } else if(msgs[0].equals("#GET")) {
                            //#GET (FileName)
                            //file 전송: server -> client

                            String fileName = msgs[1];

                            try {
                                //server에서 file 불러오는 입력스트림
                                FileInputStream fis = new FileInputStream("./server/" + fileName);
                                //client로 file 정보 보낼 출력스트림
                                OutputStream os = fileSocket.getOutputStream();

                                out.println("file 다운로드를 시작합니다.");

                                byte[] fileBuf = new byte[65536];
                                int n;

                                while ((n = fis.read(fileBuf)) != -1) {
                                    os.write(fileBuf, 0, n);
                                    out.print("#");
                                }

                                os.flush();

                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                                out.println("일치하는 file이 존재하지 않습니다.");

                                OutputStream os = fileSocket.getOutputStream();
                                os.write("@ERROR@".getBytes());
                            }

                            //os로 전송한 file을 client에서 처리하도록 메시지 전송
                            out.println();
                            out.println(clientMsg);

                        } else if(msgs[0].equals("#STATUS")) {
                            //#STATUS

                            //형식에 맞지 않은 케이스 처리
                            if(msgs.length != 1) {
                                String msg = "'#STATUS'로 입력해야 합니다.";
                                out.println(msg);
                                continue;
                            }

                            //채팅방에 접속해 있지 않은 경우
                            if(chatRoom == null) {
                                String msg = "채팅방에 접속하고 있지 않습니다.";
                                out.println(msg);
                                continue;
                            }

                            String msg = chatRoom.getClients();
                            out.println(msg);
                        } else {
                            //예외 케이스 -> 전달 불가능
                            String msg = """
                                    방을 생성하려면 #CREATE (생성할 채팅방의 이름) (사용자 이름)을,
                                    입장하려면 #JOIN (채팅방 이름) (사용자 이름)을,
                                    채팅방 내 file 업로드를 원한다면 #PUT (FileName)을,
                                    채팅방 내 file 다운로드를 원한다면 #GET (FileName)을,
                                    나가려면 #EXIT를,
                                    접속한 채팅방 상태를 확인하려면 #STATUS를 입력해주세요.
                                    """;
                            out.println(msg);
                        }

                    } else {
                        //채팅방에 입장하지 않은 경우
                        if(chatRoom == null) {
                            String msg = """
                                    방을 생성하려면 #CREATE (생성할 채팅방의 이름) (사용자 이름)을,
                                    입장하려면 #JOIN (채팅방 이름) (사용자 이름)을,
                                    채팅방 내 file 업로드를 원한다면 #PUT (FileName)을,
                                    채팅방 내 file 다운로드를 원한다면 #GET (FileName)을,
                                    나가려면 #EXIT를,
                                    접속한 채팅방 상태를 확인하려면 #STATUS를 입력해주세요.
                                    """;
                            out.println(msg);

                            continue;
                        }

                        //채팅방에 속한 모든 clients에게 msg 전달
                        String msg = username + ": " + clientMsg;
                        msgToChatRoom(msg);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("[Error] Connetion error from client");
            }
        }

        //같은 채팅방에 있는 모든 client에게 전송
        public void msgToChatRoom(String msg) throws IOException {
            for(ClientService client : chatRoom.clients) {
                client.out.println(msg);
            }
        }
    }

    public class ChatRoom {

        private String title;
        Vector<ClientService> clients;

        public ChatRoom(String title) {
            this.title = title;
            clients = new Vector<>();
        }

        public String getClients() {

            String ret = "채팅방 이름: " + title + "\n현재 사용자: ";

            for(int i=0; i<clients.size(); i++) {
                ClientService client = clients.get(i);
                ret += client.username;

                if(i < clients.size()-1)
                    ret += ", ";
            }

            return ret;
        }
    }

    //이름이 title인 채팅방 찾음
    public ChatRoom findChatRoom(String title) {
        for(ChatRoom room : rooms) {
            //채팅방 이름이 title인 케이스 존재
            if(room.title.equals(title)) {
                return room;
            }
        }

        return null;
    }

    public static void main(String[] args) {
        //커맨드로 port 입력받기
        int port1 = Integer.parseInt(args[0]);
        int port2 = Integer.parseInt(args[1]);

        TCPServer server = new TCPServer(port1, port2);
    }
}