import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Executors;

public class ServerExample
{
    AsynchronousChannelGroup channelGroup;
    AsynchronousServerSocketChannel serverSocketChannel;
    List<Client> connections = new Vector<>();

    void startServer()
    {
       try
       {
           channelGroup = AsynchronousChannelGroup.withFixedThreadPool(
                   Runtime.getRuntime().availableProcessors(),
                   Executors.defaultThreadFactory()
           );
           serverSocketChannel = AsynchronousServerSocketChannel.open(channelGroup);
           serverSocketChannel.bind(new InetSocketAddress(5001));
           System.out.println("[서버 연결됨]");
       }
       catch (Exception e)
       {
           if (serverSocketChannel.isOpen()) stopServer();
           System.out.println("[서버 연결 실패 startServer]");
           return;
       }

       serverSocketChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>()
       {
           @Override
           public void completed(AsynchronousSocketChannel socketChannel, Void attachment)
           {
               try
               {
                   String message = "[연결 수락: " + socketChannel.getRemoteAddress() + ": " + Thread.currentThread().getName() + "]";
                   System.out.println(message);
               }
               catch (IOException e)
               {
                   System.out.println("[Client 연결 안됨 accpet]");
               }

               Client client = new Client(socketChannel);
               connections.add(client);
               System.out.println("[연결 개수: " + connections.size() + "]");

               serverSocketChannel.accept(null,this);
           }

           @Override
           public void failed(Throwable exc, Void attachment)
           {
                if (serverSocketChannel.isOpen()) stopServer();
               System.out.println("[Client 연결 안됨 accpet]");
           }
       });
    }

    void stopServer()
    {
        try
        {
            connections.clear();
            if(channelGroup != null && !channelGroup.isShutdown()) channelGroup.shutdown();
            System.out.println("[서버 전체 종료]");
        }
        catch (Exception e){}
    }

    class Client
    {
        AsynchronousSocketChannel socketChannel;
        ByteBuffer readBuffer = ByteBuffer.allocate(1000);
        Client(AsynchronousSocketChannel socketChannel)
        {
            this.socketChannel = socketChannel;
            receive();
        }
        void receive()
        {
            socketChannel.read(readBuffer, readBuffer, new CompletionHandler<Integer, ByteBuffer>()
            {
                @Override
                public void completed(Integer result, ByteBuffer attachment)
                {
                    try
                    {
                        String message = "[요청 처리: " + socketChannel.getRemoteAddress() + ": " + Thread.currentThread().getName() + "]";
                        System.out.println(message);
                        attachment.flip();
                        Charset charset = Charset.forName("UTF-8");
                        String data = charset.decode(attachment).toString();
                        System.out.println(data);

                        for(Client client : connections) client.send(data);
                        readBuffer.clear();
                        socketChannel.read(readBuffer,readBuffer,this);
                    }
                    catch (IOException e) {}
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment)
                {
                    try
                    {
                        String message = "[ 클라이언트 통신 안됨: " + socketChannel.getRemoteAddress() + ": " + Thread.currentThread().getName() + "]";
                        connections.remove(Client.this);
                        socketChannel.close();
                    }
                    catch (IOException e){}
                }
            });

        }
        void send(String data)
        {
            Charset charset = Charset.forName("UTF-8");
            ByteBuffer writeBuffer = charset.encode(data);
            socketChannel.write(writeBuffer, null, new CompletionHandler<Integer, Object>()
            {
                @Override
                public void completed(Integer result, Object attachment)
                {
                }

                @Override
                public void failed(Throwable exc, Object attachment)
                {
                    try
                    {
                        String message = "[ 클라이언트 통신 안됨 " + socketChannel.getRemoteAddress() + ": " + Thread.currentThread().getName() + "]";
                        connections.remove(Client.this);
                        socketChannel.close();
                    }
                    catch (IOException e){}
                }
            });
        }

    }

    public static void main(String[] args)
    {
        ServerExample serverExample = new ServerExample();
        serverExample.startServer();
    }
}
