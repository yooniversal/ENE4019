package TCPChat;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.Iterator;
import java.util.Vector;

public class TCPServer {

    private ServerSocketChannel serverSocketChannel;
    private ServerSocketChannel fileSocketChannel;
    private Vector<ChatRoom> rooms;
    private int port1;
    private int port2;

    public TCPServer(int port1, int port2) {
        rooms = new Vector<>();
        this.port1 = port1;
        this.port2 = port2;

        try {

            //ServerSocketChannel 설정
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(port1));
            fileSocketChannel = ServerSocketChannel.open();
            fileSocketChannel.bind(new InetSocketAddress(port2));
            //non-blocking 모드로 설정
            serverSocketChannel.configureBlocking(false);
            fileSocketChannel.configureBlocking(false);

            //selector 생성 및 채널 등록
            Selector selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            fileSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while(true) {

                //채널에서 이벤트 발생할 때까지 기다림
                selector.select();
                //해당되는 채널들 불러오기 (아래 while loop에서 연달아 처리)
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();

                while(it.hasNext()) {
                    //현재 처리할 이벤트 고르고 목록에서 삭제
                    SelectionKey key = it.next();
                    it.remove();

                    if(key.isAcceptable()) {
                        //client 연결 요청

                        //연결 요청한 client의 SocketChannel 생성
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel clientSocket = server.accept();

                        SocketChannel serverSocket = null;
                        SocketChannel fileSocket = null;

                        if(server == serverSocketChannel)
                            serverSocket = clientSocket;
                        else if(server == fileSocketChannel)
                            fileSocket = clientSocket;

                        while(serverSocket == null || fileSocket == null) {
                            selector.select();
                            Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                            if(!iter.hasNext()) continue;
                            SelectionKey k = iter.next();
                            if(!k.isAcceptable()) continue;
                            iter.remove();

                            ServerSocketChannel s = (ServerSocketChannel) k.channel();
                            SocketChannel sc = s.accept();

                            if(s == serverSocketChannel)
                                serverSocket = sc;
                            else if(s == fileSocketChannel)
                                fileSocket = sc;
                        }

                        //non-blcoking 모드 설정
                        serverSocket.configureBlocking(false);
                        fileSocket.configureBlocking(false);

                        //client class 초기화 및 selector에 읽기모드로 등록
                        serverSocket.register(selector, SelectionKey.OP_READ, new Client(serverSocket, fileSocket));

                    } else if(key.isReadable()) {
                        //읽기 요청한 client의 SocketChannel 및 정보
                        SocketChannel clientSocket = (SocketChannel) key.channel();
                        Client client = (Client) key.attachment();

                        //client로부터 입력된 msg 확인
                        ByteBuffer in = ByteBuffer.allocate(1024);
                        try {
                            clientSocket.read(in);
                        } catch (Exception e) {
                            //접속 종료 -> selector에서 삭제
                            key.cancel();
                            continue;
                        }

                        String clientMsg = new String(in.array()).trim();

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
                                    String msg = "'#CREATE (생성할 채팅방의 이름) (사용자 이름)'으로 입력해야 합니다." + "\n";
                                    client.msgToMyChatRoom(msg);
                                    continue;
                                }

                                String roomname = msgs[1];
                                String username = msgs[2];

                                //이름이 입력되지 않은 경우
                                if(username.equals("")) {
                                    String msg = "이름은 0자 이상이어야 합니다." + "\n";
                                    client.msgToMyChatRoom(msg);
                                    continue;
                                }

                                //같은 이름의 채팅방이 이미 존재하는 경우
                                if(findChatRoom(roomname) != null) {
                                    String msg = "같은 이름의 채팅방이 존재합니다." + "\n";
                                    client.msgToMyChatRoom(msg);
                                    continue;
                                }

                                //사용자 이름 등록
                                client.setUsername(username);

                                //채팅방 생성 및 설정
                                ChatRoom chatRoom = new ChatRoom(roomname);
                                client.setChatRoom(chatRoom);
                                rooms.add(chatRoom);
                                String msg = "#STATUS" + "\n";
                                client.msgToMyChatRoom(msg);

                                //채팅방에 client 정보 등록
                                chatRoom.clients.add(client);

                                //채팅방에 속한 clients에게 접속 알림
                                msg = username + " 님이 접속하셨습니다." + "\n";
                                client.msgToChatRoom(msg);

                            } else if(msgs[0].equals("#JOIN")) {
                                //#JOIN (채팅방 이름) (사용자 이름)

                                //형식에 맞지 않은 케이스 처리
                                if(msgs.length != 3) {
                                    String msg = "'#JOIN (채팅방 이름) (사용자 이름)'으로 입력해야 합니다." + "\n";
                                    client.msgToMyChatRoom(msg);
                                    continue;
                                }

                                String roomname = msgs[1];
                                String username = msgs[2];

                                //이름이 입력되지 않은 경우
                                if(username.equals("")) {
                                    String msg = "이름은 0자 이상이어야 합니다." + "\n";
                                    client.msgToMyChatRoom(msg);
                                    continue;
                                }

                                //이미 채팅방에 들어가 있는 경우
                                if(client.getChatRoom() != null) {
                                    client.msgToChatRoom(client.getUsername() + " 님이 퇴장하셨습니다." + "\n");
                                    ChatRoom chatRoom = client.getChatRoom();
                                    chatRoom.clients.remove(client);
                                }

                                //사용자 이름 등록
                                client.setUsername(username);

                                //채팅방 설정
                                ChatRoom chatRoom = findChatRoom(roomname);

                                if(chatRoom == null) {
                                    String msg = "같은 이름의 채팅방이 존재하지 않습니다." + "\n";
                                    client.msgToMyChatRoom(msg);
                                    continue;
                                }

                                //사용자가 접속한 채팅방 설정
                                client.setChatRoom(chatRoom);

                                //채팅방에 client 정보 등록
                                String msg = "#JOIN" + "\n";
                                client.msgToMyChatRoom(msg);
                                chatRoom.clients.add(client);

                                //채팅방에 속한 clients에게 접속 알림
                                msg = username + " 님이 접속하셨습니다." + "\n";
                                client.msgToChatRoom(msg);

                            } else if(msgs[0].equals("#EXIT")) {
                                //#EXIT

                                if(client.getChatRoom() != null) {
                                    client.msgToChatRoom(client.getUsername() + " 님이 퇴장하셨습니다." + "\n");

                                    //채팅방에 client 정보 삭제
                                    ChatRoom chatRoom = client.getChatRoom();
                                    chatRoom.clients.remove(client);
                                }

                                if(client.getUsername() != null)
                                    System.out.println(client.getUsername() + "님이 접속을 종료했습니다.");

                                String msg = "#EXIT" + "\n";
                                client.msgToMyChatRoom(msg);

                            } else if(msgs[0].equals("#PUT")) {
                                //#PUT (FileName)
                                //file 전송: client -> server

                                String fileName = msgs[1];

                                //client와 연결된 SocketChannel
                                SocketChannel clientFileSocket = client.getFileSocket();

                                //server에 저장하도록 하는 FileChannel
                                Path path = Paths.get("./server/" + fileName);
                                FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

                                ByteBuffer inputBuffer = ByteBuffer.allocate(65536);
                                int n;
                                boolean errorFlag = false;

                                //전송: SocktChannel -> FileChannel
                                while ((n = clientFileSocket.read(inputBuffer)) > 0) {
                                    String check = new String(inputBuffer.array()).trim();
                                    if(check.equals("@ERROR@")) {
                                        errorFlag = true;
                                        break;
                                    }

                                    inputBuffer.flip();
                                    fileChannel.write(inputBuffer);
                                    clientSocket.write(ByteBuffer.wrap("#".getBytes()));

                                    inputBuffer.clear();
                                }

                                fileChannel.close();

                                if(errorFlag) {
                                    File file = new File("./server/" + fileName);
                                    file.delete();
                                } else {
                                    String msg = "\n" + fileName + " 업로드가 완료되었습니다." + "\n";
                                    client.msgToMyChatRoom(msg);
                                }

                            } else if(msgs[0].equals("#GET")) {
                                //#GET (FileName)
                                //file 전송: server -> client

                                String fileName = msgs[1];

                                //client로 file 정보 보낼 SocketChannel
                                SocketChannel clientFileSocket = client.getFileSocket();

                                try {
                                    //server에서 file 불러오는 FileChannel
                                    Path path = Paths.get("./server/" + fileName);
                                    FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ);

                                    String msg = "file 다운로드를 시작합니다." + "\n";
                                    client.msgToMyChatRoom(msg);

                                    ByteBuffer inputBuffer = ByteBuffer.allocate(65536);
                                    int n;

                                    while ((n = fileChannel.read(inputBuffer)) > 0) {
                                        inputBuffer.flip();
                                        clientFileSocket.write(inputBuffer);
                                        clientSocket.write(ByteBuffer.wrap("#".getBytes()));

                                        inputBuffer.clear();
                                    }

                                    fileChannel.close();

                                } catch (NoSuchFileException e) {
                                    e.printStackTrace();
                                    String msg = "일치하는 file이 존재하지 않습니다." + "\n";
                                    client.msgToMyChatRoom(msg);
                                    clientFileSocket.write(ByteBuffer.wrap("@ERROR@".getBytes()));
                                }

                                //전송된 file 정보를 client에서 처리하도록 메시지 전송
                                client.msgToMyChatRoom("\n");
                                client.msgToMyChatRoom(clientMsg + "\n");

                            } else if(msgs[0].equals("#STATUS")) {
                                //#STATUS

                                //형식에 맞지 않은 케이스 처리
                                if(msgs.length != 1) {
                                    String msg = "'#STATUS'로 입력해야 합니다." + "\n";
                                    client.msgToMyChatRoom(msg);
                                    continue;
                                }

                                //채팅방에 접속해 있지 않은 경우
                                if(client.getChatRoom() == null) {
                                    String msg = "채팅방에 접속하고 있지 않습니다." + "\n";
                                    client.msgToMyChatRoom(msg);
                                    continue;
                                }

                                String msg = client.getChatRoom().getClients();
                                client.msgToMyChatRoom(msg);

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
                                client.msgToMyChatRoom(msg);
                            }

                        } else {
                            //채팅방에 입장하지 않은 경우
                            if(client.getChatRoom() == null) {
                                String msg = """
                                    방을 생성하려면 #CREATE (생성할 채팅방의 이름) (사용자 이름)을,
                                    입장하려면 #JOIN (채팅방 이름) (사용자 이름)을,
                                    채팅방 내 file 업로드를 원한다면 #PUT (FileName)을,
                                    채팅방 내 file 다운로드를 원한다면 #GET (FileName)을,
                                    나가려면 #EXIT를,
                                    접속한 채팅방 상태를 확인하려면 #STATUS를 입력해주세요.
                                    """;
                                client.msgToMyChatRoom(msg);
                                continue;
                            }

                            //채팅방에 속한 모든 clients에게 msg 전달
                            String msg = client.getUsername() + ": " + clientMsg + "\n";
                            client.msgToChatRoom(msg);
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("[Error] Connetion error");
        }
    }

    public class Client {

        private ChatRoom chatRoom;          //현재 속해있는 채팅방
        private String username;            //사용자 이름
        private SocketChannel serverSocket; //msg 전달용
        private SocketChannel fileSocket;   //file 전달용

        public Client(SocketChannel serverSocket, SocketChannel fileSocket) throws IOException {
            this.serverSocket = serverSocket;
            this.fileSocket = fileSocket;
        }

        public void setChatRoom(ChatRoom chatRoom) {
            this.chatRoom = chatRoom;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public ChatRoom getChatRoom() {
            return chatRoom;
        }

        public String getUsername() {
            return username;
        }

        public SocketChannel getFileSocket() {
            return fileSocket;
        }

        //같은 채팅방에 있는 모든 client에게 전송
        public void msgToChatRoom(String msg) throws IOException {
            for(Client c : chatRoom.clients)
                c.msgToMyChatRoom(msg);
        }

        //내 채팅방에만 표시
        public void msgToMyChatRoom(String msg) throws IOException {
            serverSocket.write(ByteBuffer.wrap(msg.getBytes()));
        }
    }

    public class ChatRoom {

        private String title;
        Vector<Client> clients;

        public ChatRoom(String title) {
            this.title = title;
            clients = new Vector<>();
        }

        public String getClients() {

            String ret = "채팅방 이름: " + title + "\n현재 사용자: ";

            for(int i=0; i<clients.size(); i++) {
                Client client = clients.get(i);
                ret += client.getUsername();

                if(i < clients.size()-1)
                    ret += ", ";
            }
            ret += "\n";

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