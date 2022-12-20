# ENE4019 : Computer Networks
**UDP**, **TCP** 통신 채팅방 구현 과제입니다.<br>
TCP는 blocking, non-blocking 2가지 방식으로 구현했습니다.<br>

## Assignment 01. UDPChat
UDP 통신으로 동작하는 채팅방입니다. (과제 명세에 따라) IP주소는 별도로 입력받지 않는 대신에 입력받은 채팅방 이름을 *SHA-256* 해시를 적용해 얻은 값으로 변환해 사용합니다.
임의로 입력받는 값은 port 번호뿐이며, IP주소 입력없이 채팅방 이름을 입력해 접속하므로 server와 client를 분리하지 않았습니다.
- [source code](https://github.com/yooniversal/ENE4019/blob/main/Socket_Programming_Assigment1/src/UDPChat/UDPChat.java)
- [more details](https://quixotic-humerus-81e.notion.site/Programming-Assingment-1-507640b6bcc14a72a3d84f4bf489045a)

## Assignment 02. TCPChat (blocking)
TCP 통신으로 동작하는 채팅방입니다. **blocking 방식**으로 동작하며, *UDPChat*과 다르게 (과제 명세에 따라) IP주소를 입력받고 채팅방을 실행해야 하므로 server와 client(채팅방) 
코드가 분리되어 있습니다.
- [source code](https://github.com/yooniversal/ENE4019/tree/main/Socket_Programming_Assigment2/src/TCPChat)
- [more detail](https://quixotic-humerus-81e.notion.site/Programming-Assingment-2-ad6694d986074abb9a5cb19e44daecff)

## Assignment 03. TCPChat (non-blocking)
TCP 통신으로 동작하는 채팅방입니다. **non-blocking 방식**으로 동작합니다.
- [source code](https://github.com/yooniversal/ENE4019/tree/main/Socket_Programming_Assigment2_NIO/src/TCPChat)
- [more detail](https://quixotic-humerus-81e.notion.site/Programming-Assingment-3-950b3a6a189a4ac7963baa6b5f7b961a)
