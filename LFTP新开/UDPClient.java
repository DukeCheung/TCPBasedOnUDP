import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Scanner;


public class UDPClient {

    private static String SEND_FILE_PATH = "2018.txt";
    private static String IP_ADDRESS = "172.18.33.211";
    private static DatagramPacket dpk;
    private static String UpOrDown = null;
	public static void main(String[] args) {
		DatagramSocket dsk = null;
	    RandomAccessFile accessFile = null;
	    byte[] buf = new byte[UDPUtils.BUFFER_SIZE];
	    byte[] Buf = new byte[UDPUtils.BUFFER_SIZE+6];
		byte[] receiveBuf = new byte[1];
	    int readSize = -1;
		Scanner sc = new Scanner(System.in);
		System.out.println("LFTP client start...");
		String command;

		
		while (true){
			System.out.println("please input commond");
			command = sc.nextLine();
			CommandRegex cr = new CommandRegex(command);
			if(cr.getIsValid()) {
				System.out.println(cr.getUpOrDownLoad());
				UpOrDown = cr.getUpOrDownLoad();
				System.out.println(cr.getIpAddress());
				IP_ADDRESS = cr.getIpAddress();
				System.out.println(cr.getFilePath());
				SEND_FILE_PATH = cr.getFilePath();
				break;
			}
			else{
				System.out.println("Wrong Command!!!");
			}
		}
		try {
            dpk = new DatagramPacket(Buf, Buf.length, new InetSocketAddress(InetAddress.getByName(IP_ADDRESS), UDPUtils.PORT + 1));
            dsk = new DatagramSocket(UDPUtils.PORT+3);

			if(ClientConnect(dsk,dpk)){
				System.out.println("Connect Success");
			}else{
				System.out.println("Connect Fail");
				return;
			}
			while(true){
					if(UpOrDown.equals("lsend")){
						dpk.setData(UDPUtils.upload,0,UDPUtils.upload.length);
						dsk.send(dpk);
						dpk.setData(SEND_FILE_PATH.getBytes(),0,SEND_FILE_PATH.getBytes().length);
						System.out.println("Send file name: "+new String(dpk.getData()).trim());
						dsk.send(dpk);
						File file = new File(SEND_FILE_PATH);
		                if(!file.exists()){
		                    System.out.println("File is not Exist");
		                    dpk.setData(UDPUtils.fileNotExist);
		                    dsk.send(dpk);
		                    return;
		                }
						UpLoad(dsk);
						break;
					}
					else if(UpOrDown.equals("lget")){
						dpk.setData(UDPUtils.download,0,UDPUtils.download.length);
						dsk.send(dpk);
						dpk.setData(SEND_FILE_PATH.getBytes(),0,SEND_FILE_PATH.getBytes().length);
						System.out.println("Download file name: "+new String(dpk.getData()).trim());
						dsk.send(dpk);

						SEND_FILE_PATH = new String(dpk.getData()).trim();
						DownLoad(dsk);
						break;
					}
				}
        } catch (Exception e) {
            e.printStackTrace();
        }finally{
            try {
                if(accessFile != null)
                    accessFile.close();
                if(dsk != null)
                    dsk.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
	}

	private static boolean ClientConnect(DatagramSocket inputDSK,DatagramPacket inputDPK) {
		try{
			System.out.println("Send first SYN from client");
			inputDPK.setData(UDPUtils.connectClient,0,UDPUtils.connectClient.length);
			inputDSK.send(inputDPK);

			byte[] tempReceive = new byte[100];
			inputDPK.setData(tempReceive,0,tempReceive.length);
			inputDSK.receive(inputDPK);
			if(UDPUtils.isEqualsByteArray(UDPUtils.connectServer, tempReceive, inputDPK.getLength())){
				System.out.println("Receive second SYN from server");
				System.out.println("Server Port: "+inputDPK.getPort());
				return true;
			}
			return false;
		}
		catch (Exception e){
			e.printStackTrace();
		}
		return false;
	}

	public static void UpLoad(DatagramSocket dsk){
		try{
			int sendCount = 1;
			int seqnum = 1;
			int readSize = -1;
			byte[] Buf = new byte[UDPUtils.BUFFER_SIZE+6];
			byte[] receiveBuf = new byte[1];
			byte[] buf = new byte[UDPUtils.BUFFER_SIZE];
			dpk.setData(Buf, 0, Buf.length);
			RandomAccessFile accessFile = new RandomAccessFile(SEND_FILE_PATH, "r");

     		while ((readSize = accessFile.read(buf, 0, buf.length)) != -1) {
				ReliablePacket packet;
				if(readSize==UDPUtils.BUFFER_SIZE){
					packet = new ReliablePacket((byte)seqnum, (byte)0, (byte)0, buf);
					dpk.setData(packet.getBuf(), 0, packet.getBuf().length);
				}
				else{
					byte[] b = Arrays.copyOfRange(buf,0,readSize);
					packet = new ReliablePacket((byte)sendCount, (byte)0, (byte)0, b);
					dpk.setData(packet.getBuf(), 0, packet.getBuf().length);
				}

				dsk.send(dpk);
				dsk.setSoTimeout(1000);
				while(true){
					try {
						dpk.setData(receiveBuf, 0, receiveBuf.length);
						dsk.receive(dpk);
						if(receiveBuf[0]!=1){
							System.out.println("Resend the wrong packet.");
							System.out.println(packet.getCheckSum());
							dpk.setData(packet.getBuf(), 0, packet.getBuf().length);
							dsk.send(dpk);
						}
						else
							break;
					} catch (SocketTimeoutException e) {
						System.out.println("Resend the lost packet.");
						dpk.setData(packet.getBuf(), 0, packet.getBuf().length);
						dsk.send(dpk);
						continue;
					}
				}

				System.out.println("Send count of " + (sendCount++) + "!");
				seqnum++;
				if(seqnum>127){
					seqnum %= 127;
				}
				//Thread.sleep(10);
			}
			
			dpk.setData(UDPUtils.end,0,UDPUtils.end.length);
			dsk.send(dpk);
			dpk.setData(receiveBuf,0,receiveBuf.length);
			dsk.setSoTimeout(1000);
			while(true){
				try {
					dsk.receive(dpk);
					if(receiveBuf[0] == 1){
						break;
					}
					else{
						System.out.println("Resend the wrong packet.");
						dpk.setData(UDPUtils.end,0,UDPUtils.end.length);
						dsk.send(dpk);
						dpk.setData(receiveBuf,0,receiveBuf.length);
					}
				} catch (SocketTimeoutException e) {
					System.out.println("Resend the lost packet.");
					dpk.setData(UDPUtils.end,0,UDPUtils.end.length);
					dsk.send(dpk);
					dpk.setData(receiveBuf,0,receiveBuf.length);
					continue;
				}
				
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}

	public static void DownLoad(DatagramSocket dsk){
		try{
		    byte[] Buf = new byte[UDPUtils.BUFFER_SIZE+6];
		    
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(SEND_FILE_PATH));
			dpk.setData(Buf, 0, Buf.length);
			dsk.receive(dpk);

			int readSize = 0;
			int readCount = 1;
			int flushSize = 0;
			int flag = 0;
			int first = 0;
			while((readSize = dpk.getLength()) != 0){
				if (first==0){
					if(UDPUtils.isEqualsByteArray(UDPUtils.fileNotExist, Buf, dpk.getLength())){
						System.out.println("File is not Exist");
						break;
					}
					first=1;
				}

				if(UDPUtils.isEqualsByteArray(UDPUtils.end,Buf,dpk.getLength())){
					byte[] a = new byte[1];
					a[0] = 1;
					dpk.setData(a, 0, 1);
					dsk.send(dpk);
					break;
				}
				ReliablePacket packet = new ReliablePacket(Buf);
				int t = packet.getSeqNum()&0xff;

				if((packet.check()&&t==readCount)||readSize!=UDPUtils.BUFFER_SIZE){
					if(t==2&&flag==0){
						dsk.receive(dpk);
						flag = 1;
						continue;
					}
					bos.write(packet.getData(), 0, readSize-6);
					if(++flushSize % 1000 == 0){
						flushSize = 0;
						bos.flush();
					}
					byte[] a = new byte[1];
					a[0] = 1;
					dpk.setData(a, 0, 1);
					dsk.send(dpk);
					dpk.setData(Buf,0, Buf.length);
					System.out.println("Receive count of "+ ( readCount++ ) +" !");
					dsk.receive(dpk);
				}
				else{
					byte[] a = new byte[1];
					a[0] = 0;
					dpk.setData(a, 0, 1);
					dsk.send(dpk);

					dpk.setData(Buf,0, Buf.length);
					System.out.println("Failed count of "+ (readCount) +" !");
					dsk.receive(dpk);
				}
			}
		}catch (Exception e) {
			// TODO: handle exception
		}
		
	}
}

/*
LFTP lsend 172.18.33.211 2018.flv
*/