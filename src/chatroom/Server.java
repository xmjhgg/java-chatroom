package chatroom;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Server implements Runnable{
	
	private static final Charset CHARSET=Charset.forName("UTF-8");
	private Selector selector=null;
	private ServerSocketChannel ssc=null;
	private Thread thread=new Thread(this);
	
	//ConcurrentLinkedQueue基于链表的线程安全队列，用于缓存客户端发送的消息，以便广播发送给所有客户端
	private Queue<String> queue=new ConcurrentLinkedQueue<String>();
	
	private volatile boolean live=true;
	public void start() throws IOException {
		selector=Selector.open();
		ssc=ServerSocketChannel.open();
		ssc.socket().bind(new InetSocketAddress(9000));
		ssc.configureBlocking(false);
		ssc.register(selector, SelectionKey.OP_ACCEPT);
		thread.start();
	}
	
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		try {
			while (live&&!Thread.interrupted()) {
				
				//selector轮询查询通道，如果没有通道准备就绪，那么就直接结束这一轮处理，查询的时间设置在1000毫秒
				if (selector.select(1000)==0) {
					continue;
				}
				
				//取出消息队列中的消息，并读入outBuf中缓存
				ByteBuffer outBuf=null;
				String outMsg=queue.poll();
				if (outMsg!=null&&"".equalsIgnoreCase("")) {
					outBuf=ByteBuffer.wrap(outMsg.getBytes("UTF-8"));
					outBuf.limit(outMsg.length());
				}

				//根据key的事件不同进行处理
				Set<SelectionKey> set=selector.selectedKeys();
				Iterator<SelectionKey> iterator=set.iterator();
				while (iterator.hasNext()) {
					SelectionKey selectionKey = (SelectionKey) iterator.next();
					iterator.remove();
					if (selectionKey.isValid()&&selectionKey.isAcceptable()) {
						this.onAcceptable(selectionKey);
					}
					if (selectionKey.isValid()&&selectionKey.isReadable()) {
						this.onReadable(selectionKey);
					}
					
					//当前通道可写，并且有消息要发送，那么就发送
					if (selectionKey.isValid()&&selectionKey.isWritable()&&outBuf!=null) {
						SocketChannel sc=(SocketChannel) selectionKey.channel();
						this.write(sc, outBuf);
					}
					
				}
				
			}
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	
	//连接事件
	public void onAcceptable(SelectionKey key) throws IOException {
		//通过key获取到通道
		ServerSocketChannel ssc=(ServerSocketChannel) key.channel();
		
		SocketChannel sc=null;
		try {
			sc=ssc.accept();
			if (sc!=null) {
				System.out.println("Client"+sc.getRemoteAddress()+"connnected");
				sc.configureBlocking(false);
				
				//为新建立的连接注册通道与可读可写事件
				//并为这个客户端创建一个读取用的缓冲区（避免重复创建）
				sc.register(selector, SelectionKey.OP_READ|SelectionKey.OP_WRITE,ByteBuffer.allocate(1024));
			}
		} catch (Exception e) {
			// TODO: handle exception
			System.out.println("error on accept connection");
			sc.close();
		}
	}
	
	
	public void onReadable(SelectionKey key) throws IOException {
		
		SocketChannel sc=(SocketChannel) key.channel();
		ByteBuffer buf=(ByteBuffer) key.attachment(); //获取属于客户端的缓冲区
		
		int r=0;
		StringBuilder sb=new StringBuilder();
		String rs=null; //客户端发送的消息
		String remote=null;	//客户端的地址
		buf.clear();
		try {
			remote=sc.getRemoteAddress().toString();
			
			//读取客户端缓冲区的数据
			while ((r=sc.read(buf))>0) {
				System.out.println("Received"+r+"bytes from"+sc.getRemoteAddress());
				buf.flip();
				sb.append(CHARSET.decode(buf));
				buf.clear();
				rs=sb.toString();
				if (rs.endsWith("\n")) {
					break;
				}
			}
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			sc.close();
		}
		
		//接收完消息后，保存到消息队列中
		if (rs!=null&&"".equalsIgnoreCase(rs)) {
			String[] sa=rs.split("\n");
			for (String a : sa) {
				System.out.println(sc.getRemoteAddress()+":"+a);
				//服务器端接收到bye后断开与该客户的连接
				if ("bye".equalsIgnoreCase(a)) {
					sc.close();
				}
			}
		}
	}
	
	
	private void write(SocketChannel sc,ByteBuffer buf) throws IOException {
		buf.position(0);
		int r=0;
		try {
			while (buf.hasRemaining()&&(r=sc.write(buf))>0) {
				System.out.println("write back"+r+" to "+sc.getRemoteAddress());
			}
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			System.out.println("error in write socket");
			sc.close();
			return;
		}
	}
	
	public void close() throws IOException {
		live=false;
		try {
			thread.join();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		selector.close();
		ssc.close();
	}
	
	public static void main(String[] args) throws IOException {
		BufferedReader br=null;
		Server server=new Server();
		try {
			server.start();
			String cmd=null;
			System.out.println("enter 'exit' to exit");
			br=new BufferedReader(new InputStreamReader(System.in));
			while ((cmd=br.readLine())!=null) {
				if ("exit".equalsIgnoreCase(cmd)) {
					server.close();
				}
			}
			
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}finally{
			br.close();
			server.close();
		}
		System.out.println("bye");
		
		
	}
	
	
	
}
