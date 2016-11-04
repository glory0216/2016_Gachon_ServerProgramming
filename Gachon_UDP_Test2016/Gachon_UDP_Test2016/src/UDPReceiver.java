import java.io.*;
import java.net.*;

public class UDPReceiver {

    public static void main(String args[]) throws Exception {
        System.out.println("File 다운로드 준비 완료. 전송을 시작하세요.");

        // port, file name
        final int port = Integer.parseInt(args[0]);
        final String fileName = args[1];

        receiveAndCreate(port, fileName);
    }

    public static void receiveAndCreate(int port, String fileName) throws IOException {
        DatagramSocket socket = new DatagramSocket(port);
        InetAddress address;
        File file = new File(fileName);
        FileOutputStream outToFile = new FileOutputStream(file);

        // last message임을 알리기위한 Flag
        boolean lastMessageFlag = false;
        boolean lastMessage = false;

        // sequence number
        int sequenceNumber = 0;
        int lastSequenceNumber = 0;

        // for each message
        while (!lastMessage) {
        	// message : full message를 담음
        	// fileByteArray : header 부분 제외한 순수 file data 
            byte[] message = new byte[1024];
            byte[] fileByteArray = new byte[1021];

            // 패킷 받기, message에 받은 패킷의 데이터 다시 저장
            DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
            socket.setSoTimeout(0);
            socket.receive(receivedPacket);
            message = receivedPacket.getData();

            // ACK를 보내기 위해 받은 packet의 IP address와 port 정보 저장
            address = receivedPacket.getAddress();
            port = receivedPacket.getPort();

            // sequence number 저장
            // message가 byte array라 '& 0xff' 연산을 통해 int 형식으로 type casting
            // message[0], message[1], message[2]까지 UDPSender에서 보낸 헤더 부분에 해당          
            sequenceNumber = ((message[0] & 0xff) << 8) + (message[1] & 0xff);

            // last message인지 확인 후 flag 설정
            if ((message[2] & 0xff) == 1) {
                lastMessageFlag = true;
            } else {
                lastMessageFlag = false;
            }

            if (sequenceNumber == (lastSequenceNumber + 1)) {
                lastSequenceNumber = sequenceNumber;

                // 헤더 부분 제외한 순수 file data 저장
                for (int i=3; i < 1024 ; i++) {
                    fileByteArray[i-3] = message[i];
                }

                // 받은 file data만큼 FileOutputStream에 저장
                outToFile.write(fileByteArray);
                System.out.println("Received: Sequence number = " + lastSequenceNumber +", Flag = " + lastMessageFlag);

                // Send ACK
                sendAck(lastSequenceNumber, socket, address, port);

            } 
            // packet loss인 경우
            else {
                System.out.println("Expected sequence number: " + (lastSequenceNumber + 1) + " but received " + sequenceNumber + ". DISCARDING");

                //Resend ACK
                sendAck(lastSequenceNumber, socket, address, port);
            }

            // last message면 FileOutputStream 닫기
            if (lastMessageFlag) {
                outToFile.close();
                lastMessage = false;
                break;
            }
        }
        
        socket.close();
        System.out.println("File " + fileName + " 다운로드가 완료되었습니다.");
    }

    public static void sendAck(int lastSequenceNumber, DatagramSocket socket, InetAddress address, int port) throws IOException {
        
        byte[] ackPacket = new byte[2];
        ackPacket[0] = (byte)(lastSequenceNumber >> 8);
        ackPacket[1] = (byte)(lastSequenceNumber);
        DatagramPacket acknowledgement = new  DatagramPacket(ackPacket, ackPacket.length, address, port);
        socket.send(acknowledgement);
        System.out.println("Sent ack: Sequence Number = " + lastSequenceNumber);
    }
}
