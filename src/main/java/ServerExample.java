import util.Operation;

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
        ByteBuffer writeBuffer = ByteBuffer.allocate(1000);
        String userId = "not set yet";

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
                        byte[] reqIdReceive = new byte[4];
                        byte[] reqNumReceive = new byte[4];
                        byte[] reqUserId = new byte[16];
                        byte[] reqRoomNum = new byte[4];
                        ByteBuffer reqIdByte = attachment.get(reqIdReceive, 0, 4);
                        int reqId =Integer.parseInt( reqIdByte.toString());
                        attachment.position(4);
                        ByteBuffer reqNumByte = attachment.get(reqNumReceive, 4, 4);
                        int reqNum = Integer.parseInt(reqNumByte.toString());
                        attachment.position(8);
                        ByteBuffer reqUserIdByte = attachment.get(reqUserId, 8, 16);
                        String userId = reqUserIdByte.toString();
                        attachment.position(24);
                        ByteBuffer reqRoomNumByte = attachment.get(reqRoomNum, 24, 4);
                        int roomNum = Integer.parseInt(reqRoomNumByte.toString());
                        attachment.position(28);

                        processOp(reqNum,reqId,userId,roomNum,attachment);

//                        for(Client client : connections) client.send(data);
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
        void send(int reqId , int result)
        {
            Charset charset = Charset.forName("UTF-8");
            writeBuffer.put((byte) reqId);
            writeBuffer.position(4);
            writeBuffer.put((byte) result);
            writeBuffer.position(8);
            writeBuffer.flip();

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


        void processOp(int operation,int reqId,String userId, int roomNum, ByteBuffer data)
        {
            Operation op = Operation.fromInteger(operation);
            switch (op)
            {
                case login:
                    loginProcess(reqId,userId, data);
                    System.out.println("login process completed");
                    return;
                case logout:
                    logoutProcess(reqId,userId,data);
                    System.out.println("logout process completed");
                    return;
                case sendText:
                case fileUpload:
                case fileList:
                case fileDownload:
                case fileDelete:
                case createRoom:
                case quitRoom:
                case inviteRoom:
                case requestQuitRoom:
                case roomUserList:
            }
        }


        private void loginProcess(int reqId,String userId, ByteBuffer data)
        {
            if (userId != null)
            {
                for (Client client : connections)
                {
                    if (client.userId.equals(userId))
                    {
                        send(reqId,0);
                        Client user1 = connections.get(connections.size() - 1);
                        connections.remove(user1);
                        return;
                    }
                }

                Client client1 = connections.get(connections.size() - 1);
                client1.userId = userId;
                send(reqId,-1);

            }
            else send(-1,0);
        }

        private void logoutProcess(int reqid, String userId, ByteBuffer data)
        {
            for (Client client : connections)
            {
                if (client.userId.equals(userId))
                {
                    try
                    {
                        client.socketChannel.close();
                        break;
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    public static void main(String[] args)
    {
        ServerExample serverExample = new ServerExample();
        serverExample.startServer();
    }
}
