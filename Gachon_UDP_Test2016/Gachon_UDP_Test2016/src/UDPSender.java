import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Random;

/*******************************************************************************
 * 
 * Sender Class
 * 
 * - 현재 문제 - 
 * Stop And Wait 방식으로 packet loss를 정확히 발견하여 재전송하나 속도가 너무 느림
 * 또한, 서버, 클라이언트 모두 멀티쓰레드 또는 NIO를 이용하여 구현해야 할 필요가 있음
 * NIO를 사용할거라면 NIO를 어느 부분에서 구현해야 할 지가 문제 
 * 
 * 준영 : 클라이언트인 Sender에서 file을 쓸 때 다수의 쓰레드로 Write할 수 있도록 구현 방법 모색
 * 수경 : UDP에서 Stop And Wait 이외에 전송 시 강제로 발생되는 packet loss를 처리할 수 있는 방안 모색
 * 영광 : NIO 공부하여 서버인 Receiver에 적용할 수 있도록 
 * 
 ********************************************************************************/

public class UDPSender {

    public static void main(String args[]) throws Exception {
        // address, port, file name
        final String hostName = args[0];
        final int port = Integer.parseInt(args[1]);
        final String fileName = args[2];

        createAndSend(hostName, port, fileName);
    }

    public static void createAndSend(String hostName, int port, String fileName) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName(hostName);
        File file = new File(fileName);	

        // fileByteArray : FileInputStream에 저장할 byte array
        InputStream inFromFile = new FileInputStream(file);
        byte[] fileByteArray = new byte[(int)file.length()];
        inFromFile.read(fileByteArray);

        // timer start
        StartTime timer = new StartTime(0);

        int sequenceNumber = 0;
        boolean lastMessageFlag = false;
        int ackSequenceNumber = 0;

        // packet 재전송 시 count할 counter
        int retransmissionCounter = 0;

        // for each message
        for (int i=0; i < fileByteArray.length; i = i+1021 ) {

            sequenceNumber += 1;

            byte[] message = new byte[1024];

            // UDPReceiver에서 받을 sequence number 저장
            message[0] = (byte)(sequenceNumber >> 8);
            message[1] = (byte)(sequenceNumber);

            // last packet이면 message[2]에 1을 저장
            if ((i+1021) >= fileByteArray.length) {
                lastMessageFlag = true;
                message[2] = (byte)(1);
            } 
            else { 
                lastMessageFlag = false;
                message[2] = (byte)(0);
            }

            // Copy the bytes for the message to the message array
            if (!lastMessageFlag) {
                for (int j=0; j <= 1020; j++) {
                    message[j+3] = fileByteArray[i+j];
                }
            }
            else if (lastMessageFlag) { // If it is the last message
                for (int j=0;  j < (fileByteArray.length - i); j++) {
                    message[j+3] = fileByteArray[i+j];			
                }
            }
            
            // send packet
            // randomize -> 95% 확률로 packet 전송
            DatagramPacket sendPacket = new DatagramPacket(message, message.length, address, port);
            Random rand = new Random();
            if (rand.nextInt(100) < 95) {
            	socket.send(sendPacket);
            	System.out.println("Sent: Sequence number = " + sequenceNumber + ", Flag = " + lastMessageFlag);
            }
            else {
            	System.out.println("Packet Loss: Sequence number = " + sequenceNumber);
            }            

            /*
             *  아래 부분은 Stop-And-Wait 구현 부분
             */
            
            // For verifying the acknowledgements
            boolean ackRecievedCorrect = false;
            boolean ackPacketReceived = false;

            while (!ackRecievedCorrect) {
                // Check for an ack
                byte[] ack = new byte[2];
                DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                try {
                    socket.setSoTimeout(50);
                    socket.receive(ackpack);
                    ackSequenceNumber = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                    ackPacketReceived = true;
                } catch (SocketTimeoutException e) {
                    System.out.println("Socket timed out waiting for an ack");
                    ackPacketReceived = false;
                    //e.printStackTrace();
                }

                // Break if there is an ack so that the next packet can be sent
                if ((ackSequenceNumber == sequenceNumber) && (ackPacketReceived)) {	
                    ackRecievedCorrect = true;
                    System.out.println("Ack received: Sequence Number = " + ackSequenceNumber);
                    break;
                } else { // Resend packet
                    socket.send(sendPacket);
                    System.out.println("Resending: Sequence Number = " + sequenceNumber);

                    // Increment retransmission counter
                    retransmissionCounter += 1;
                }
            }
        }

        socket.close();
        System.out.println("File " + fileName + " 전송이 완료되었습니다.");

        // file size, elapsed time, throughput
        int fileSizeKB = (fileByteArray.length) / 1024;
        int transferTime = timer.getTimeElapsed() / 1000;
        double throughput = (double) fileSizeKB / transferTime;
        System.out.println("File size: " + fileSizeKB + "KB, Transfer time: " + transferTime + " seconds. Throughput: " + throughput + "KBps");
        System.out.println("Number of retransmissions: " + retransmissionCounter);	
    }
}
