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
	
	//用于读取的缓冲区buffer
	private ByteBuffer buffer=ByteBuffer.allocate(10240);
	
	//待发送消息 的 队列
	private Queue<String> queue=new ConcurrentLinkedQueue<String>();
	
	private volatile boolean live=true;
	
	public void start() throws IOException{
		selector=Selector.open();
		socketChannel=SocketChannel.open();
		socketChannel.configureBlocking(false);
		socketChannel.connect(new InetSocketAddress("127.0.0.1",9000));
		if (socketChannel.finishConnect()) {
			//为通道注册选择器
			socketChannel.register(selector, SelectionKey.OP_READ|SelectionKey.OP_WRITE);
			thread.start();
		}
		
		
	}
	
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		try {
			while (live&&!Thread.interrupted()) {
				if (selector.select(1000)==0) { //如果没有通道在selector上注册，那么就阻塞线程（最多1000毫秒）
					continue;
				}
				
				//遍历selector上的所有的sectionKey,一个sectionKey对应一个通道和选择器
				//即遍历所有的事件
				Set<SelectionKey> set=selector.selectedKeys();
				Iterator<SelectionKey> iterator=set.iterator();
				while (iterator.hasNext()) {
					//获取要处理的事件
					SelectionKey key = iterator.next();
					//获取到事件后将获取的事件中移除
					iterator.remove();
					
					SocketChannel sc=null;
					
					int r=0;	//r是每次读取接收数据的数量或者发送的数据量
					String s=null; //s是解析完成的接收数据或者发送数据
					
					//如果当前的通道是可读事件，并且事件有效，那么读取服务器发送的字符串
					if (key.isValid()&&key.isReadable()) {
						
						sc=(SocketChannel) key.channel();
						StringBuilder sb=new StringBuilder();
						buffer.clear();
						while ((r=sc.read(buffer))>0) {
							
							System.out.println("Received"+r+"bytes from "+sc.getRemoteAddress());
							//flip方法将buffer的postion调整至0位置，limt调整至数据的结尾处
							buffer.flip();
							
							sb.append(CHARSET.decode(buffer));
							buffer.clear();
							s=sb.toString();
							//如果s的结尾是回车符，那么就代表已经接收完所有的数据了
							if (s.endsWith("\n")) {
								break;
							}
						}
						
						//上一步接收完了所有数据，但是数据有可能会发生粘包，及接收的数据可能含有多个以"\n"结尾的数据，所以需要再处理一下
						String[] sa=s.split("\n");
						for (String a : sa) {
							if (!"".equals(a)) {
								System.out.println(a);
							}
						}	//至此，客户端读取数据结束
					}
					//客户端发送阶段,
					//如果如果事件是写出事件，并且消息队列中有数据需要发送，并且通道可以写，那么就发送数据
					if (key.isValid()&&key.isWritable()&&!queue.isEmpty()) {
						s=queue.poll();
						sc=(SocketChannel) key.channel();
						
						//使用wrap方法创建的buffer，将会byte数组包装成buffer的实例
						ByteBuffer buf=ByteBuffer.wrap(s.getBytes("UTF-8"));
						buf.limit(s.length()); //使用wrap方法后buffer的limit为0，所以需要修改一下
						
						//将buf的数据写入到socket通道中,hasRemaining方法判断缓冲区是否还有数据可读
						while (buf.hasRemaining()&&(r=socketChannel.write(buf))>0) {
							System.out.println("write "+r+"bytes to Server");
						}//至此，发送事件结束，循环处理 下一个接收与发送事件
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
	
	//isAlive方法判断线程是否处于活跃状态（非阻塞，非睡眠，运行或者就绪状态）
	public boolean isAlive(){
		return thread.isAlive();
	}
	
	public void sent(String s){
		queue.add(s);
	}
	
	//客户端启动连接服务器
	public static void main(String[] args) throws IOException {
		BufferedReader ir=null;
		Client client=new Client();
		try {
			
			client.start();
			String cmd=null; //cmd为键盘输入的要发送的数据
			ir=new BufferedReader(new InputStreamReader(System.in));
			System.out.println("Say 'bye' to exit");
			while ((cmd=ir.readLine())!=null&&client.isAlive()) {
				if (cmd!=null&&!"".equals(cmd)) {
					client.sent(cmd.concat("\n")); //输入完成后在结尾处加上\n作为包与包之间的标识
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
