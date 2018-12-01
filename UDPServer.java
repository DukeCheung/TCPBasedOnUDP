import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
 

public class UDPServer {
	
	private static final String SAVE_FILE_PATH = "2019.flv";
	
	public static void main(String[] args) {
		
		System.out.println("LFTP server start....");
		byte[] buf = new byte[UDPUtils.BUFFER_SIZE];
		
		DatagramPacket dpk = null;
		DatagramSocket dsk = null;
		BufferedOutputStream bos = null;
		try {
		
            dpk = new DatagramPacket(buf, buf.length,new InetSocketAddress(InetAddress.getByName("localhost"), UDPUtils.PORT));
			dsk = new DatagramSocket(UDPUtils.PORT + 1, InetAddress.getByName("localhost"));
//			if(ServerConnect(dsk,dpk)){
//				System.out.println("Connect Success");
//				dpk.setData(buf,0,buf.length);
//			}
//			else{
//				System.out.println("Connect Fail");
//				return;
//			}


			bos = new BufferedOutputStream(new FileOutputStream(SAVE_FILE_PATH));
			dsk.receive(dpk);
			
			int readSize = 0;
			int readCount = 0;
			int flushSize = 0;
			while((readSize = dpk.getLength()) != 0){  
				if(UDPUtils.isEqualsByteArray(UDPUtils.exitData, buf, readSize)){
					System.out.println("Server Exit ...");
					dpk.setData(UDPUtils.exitData, 0, UDPUtils.exitData.length);
					dsk.send(dpk);
					break;
				}
				bos.write(buf, 0, readSize);
				if(++flushSize % 1000 == 0){ 
					flushSize = 0;
					bos.flush();
				}
				dpk.setData(UDPUtils.successData, 0, UDPUtils.successData.length);
				dsk.send(dpk);

				dpk.setData(buf,0, buf.length);
				System.out.println("Receive count of "+ ( ++readCount ) +" !");
				dsk.receive(dpk);
			}
			bos.flush();
		} catch (Exception e) {
			e.printStackTrace();
		} finally{
			try {
				if(bos != null)
					bos.close();
				if(dsk != null)
					dsk.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		
	}

	private static boolean ServerConnect(DatagramSocket inputDSK,DatagramPacket inputDPK) {
		try{
			byte[] tempReceive = new byte[100];
			inputDPK.setData(tempReceive,0,tempReceive.length);
			inputDSK.receive(inputDPK);
			if(UDPUtils.isEqualsByteArray(UDPUtils.connectClient, tempReceive, inputDPK.getLength())){
				System.out.println("Receive first SYN from client");
				System.out.println("Send second SYN from server");
				inputDPK.setData(UDPUtils.connectServer,0,UDPUtils.connectServer.length);
				inputDSK.send(inputDPK);
				return true;
			}
			return false;
		}
		catch (Exception e){
			e.printStackTrace();
		}
		return false;
	}
}