package chatroom;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.rmi.CORBA.Util;

public class Client implements Runnable {
	private static final Charset CHARSET =Charset.forName("UTF-8");
	private Selector selector ;
	private SocketChannel socketChannel;
	
	private Thread thread=new Thread(this);
	
	//���ڶ�ȡ�Ļ�����buffer
	private ByteBuffer buffer=ByteBuffer.allocate(10240);
	
	//��������Ϣ �� ����
	private Queue<String> queue=new ConcurrentLinkedQueue<String>();
	
	private volatile boolean live=true;
	
	public void start() throws IOException{
		selector=Selector.open();
		socketChannel=SocketChannel.open();
		socketChannel.configureBlocking(false);
		socketChannel.connect(new InetSocketAddress("127.0.0.1",9000));
		if (socketChannel.finishConnect()) {
			//Ϊͨ��ע��ѡ����
			socketChannel.register(selector, SelectionKey.OP_READ|SelectionKey.OP_WRITE);
			thread.start();
		}
		
		
	}
	
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		try {
			while (live&&!Thread.interrupted()) {
				if (selector.select(1000)==0) { //���û��ͨ����selector��ע�ᣬ��ô�������̣߳����1000���룩
					continue;
				}
				
				//����selector�ϵ����е�sectionKey,һ��sectionKey��Ӧһ��ͨ����ѡ����
				//���������е��¼�
				Set<SelectionKey> set=selector.selectedKeys();
				Iterator<SelectionKey> iterator=set.iterator();
				while (iterator.hasNext()) {
					//��ȡҪ������¼�
					SelectionKey key = iterator.next();
					//��ȡ���¼��󽫻�ȡ���¼����Ƴ�
					iterator.remove();
					
					SocketChannel sc=null;
					
					int r=0;	//r��ÿ�ζ�ȡ�������ݵ��������߷��͵�������
					String s=null; //s�ǽ�����ɵĽ������ݻ��߷�������
					
					//�����ǰ��ͨ���ǿɶ��¼��������¼���Ч����ô��ȡ���������͵��ַ���
					if (key.isValid()&&key.isReadable()) {
						
						sc=(SocketChannel) key.channel();
						StringBuilder sb=new StringBuilder();
						buffer.clear();
						while ((r=sc.read(buffer))>0) {
							
							System.out.println("Received"+r+"bytes from "+sc.getRemoteAddress());
							//flip������buffer��postion������0λ�ã�limt���������ݵĽ�β��
							buffer.flip();
							
							sb.append(CHARSET.decode(buffer));
							buffer.clear();
							s=sb.toString();
							//���s�Ľ�β�ǻس�������ô�ʹ����Ѿ����������е�������
							if (s.endsWith("\n")) {
								break;
							}
						}
						
						//��һ�����������������ݣ����������п��ܻᷢ��ճ���������յ����ݿ��ܺ��ж����"\n"��β�����ݣ�������Ҫ�ٴ���һ��
						String[] sa=s.split("\n");
						for (String a : sa) {
							if (!"".equals(a)) {
								System.out.println(a);
							}
						}	//���ˣ��ͻ��˶�ȡ���ݽ���
					}
					//�ͻ��˷��ͽ׶�,
					//�������¼���д���¼���������Ϣ��������������Ҫ���ͣ�����ͨ������д����ô�ͷ�������
					if (key.isValid()&&key.isWritable()&&!queue.isEmpty()) {
						s=queue.poll();
						sc=(SocketChannel) key.channel();
						
						//ʹ��wrap����������buffer������byte�����װ��buffer��ʵ��
						ByteBuffer buf=ByteBuffer.wrap(s.getBytes("UTF-8"));
						buf.limit(s.length()); //ʹ��wrap������buffer��limitΪ0��������Ҫ�޸�һ��
						
						//��buf������д�뵽socketͨ����,hasRemaining�����жϻ������Ƿ������ݿɶ�
						while (buf.hasRemaining()&&(r=socketChannel.write(buf))>0) {
							System.out.println("write "+r+"bytes to Server");
						}//���ˣ������¼�������ѭ������ ��һ�������뷢���¼�
					}
				}
			}
			
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}finally{
			try {
				selector.close();
				socketChannel.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public void close() {
		live=false;
		try {
			thread.join();
			selector.close();
			socketChannel.close();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	
	//isAlive�����ж��߳��Ƿ��ڻ�Ծ״̬������������˯�ߣ����л��߾���״̬��
	public boolean isAlive(){
		return thread.isAlive();
	}
	
	public void sent(String s){
		queue.add(s);
	}
	
	//�ͻ����������ӷ�����
	public static void main(String[] args) throws IOException {
		BufferedReader ir=null;
		Client client=new Client();
		try {
			
			client.start();
			String cmd=null; //cmdΪ���������Ҫ���͵�����
			ir=new BufferedReader(new InputStreamReader(System.in));
			System.out.println("Say 'bye' to exit");
			while ((cmd=ir.readLine())!=null&&client.isAlive()) {
				if (cmd!=null&&!"".equals(cmd)) {
					client.sent(cmd.concat("\n")); //������ɺ��ڽ�β������\n��Ϊ�����֮��ı�ʶ
					if ("bye".equalsIgnoreCase(cmd)) {
						client.close();
					}
				}
			}
			
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}finally{
			ir.close();
			client.close();
		}
		System.out.println("communication over");
	}
	

}
