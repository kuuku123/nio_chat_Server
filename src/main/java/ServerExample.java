import util.LogFormatter;
import util.Operation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class ServerExample
{
    private final static Logger logr = Logger.getGlobal();
    AsynchronousChannelGroup channelGroup;
    AsynchronousServerSocketChannel serverSocketChannel;
    List<Client> connections = new Vector<>();

    private static void setupLogger()
    {
        LogFormatter formatter = new LogFormatter();
        LogManager.getLogManager().reset();
        logr.setLevel(Level.ALL);

        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.INFO);
        ch.setFormatter(formatter);
        logr.addHandler(ch);
    }
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
           logr.info("[서버 연결됨]");
       }
       catch (Exception e)
       {
           if (serverSocketChannel.isOpen()) stopServer();
           logr.severe("[서버 연결 실패 startServer]");
           return;
       }

       serverSocketChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>()
       {
           @Override
           public void completed(AsynchronousSocketChannel socketChannel, Void attachment)
           {
               try
               {
                   logr.info("[연결 수락: " + socketChannel.getRemoteAddress() + ": " + Thread.currentThread().getName() + "]");
               }
               catch (IOException e)
               {
                   logr.severe("[Client 연결 도중에 끊김 accept IOException fail]");
               }

               Client client = new Client(socketChannel);
               connections.add(client);
               logr.info("[연결 개수: " + connections.size() + "]");

               serverSocketChannel.accept(null,this);
           }

           @Override
           public void failed(Throwable exc, Void attachment)
           {
                if (serverSocketChannel.isOpen()) stopServer();
               logr.severe("[Client 연결 안됨 accept fail]");
           }
       });
    }

    void stopServer()
    {
        try
        {
            connections.clear();
            if(channelGroup != null && !channelGroup.isShutdown()) channelGroup.shutdown();
            logr.info("[서버 전체 종료]");
        }
        catch (Exception e)
        {
            logr.severe("[서버 전체 종료 실패]");
        }
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
                        attachment.flip();
                        byte[] reqIdReceive = new byte[4];
                        byte[] reqNumReceive = new byte[4];
                        byte[] reqUserId = new byte[16];
                        byte[] reqRoomNum = new byte[4];
                        attachment.get(reqIdReceive);
                        int reqId =byteToInt(reqIdReceive);
                        attachment.position(4);
                        attachment.get(reqNumReceive);
                        int reqNum = byteToInt(reqNumReceive);
                        attachment.position(8);
                        attachment.get(reqUserId);
                        byte[] userIdNonzero = removeZero(reqUserId);
                        String userId = new String(userIdNonzero, StandardCharsets.UTF_8);
                        attachment.position(24);
                        attachment.get(reqRoomNum);
                        int roomNum = byteToInt(reqRoomNum);
                        attachment.position(28);

                        logr.info("[요청 처리: " + socketChannel.getRemoteAddress() + ": " + Thread.currentThread().getName() + "]");
                        processOp(reqNum,reqId,userId,roomNum,attachment);

//                        for(Client client : connections) client.send(data);
                        readBuffer.clear();
                        socketChannel.read(readBuffer,readBuffer,this);
                    }
                    catch (IOException e) {}
                    catch (BufferUnderflowException e)
                    {
                        logr.info("Client가 연결을 끊음");
                        readBuffer.clear();
                    }
                }


                @Override
                public void failed(Throwable exc, ByteBuffer attachment)
                {
                    try
                    {
                        logr.severe("[receive fail" + socketChannel.getRemoteAddress()+ " : " + Thread.currentThread().getName()+ "]");
                        connections.remove(Client.this);
                        socketChannel.close();
                    }
                    catch (IOException e){}
                }
                private byte[] removeZero(byte[] reqUserId)
                {
                    int count = 0;
                    for (byte b : reqUserId)
                    {
                        if (b == (byte) 0) count++;
                    }
                    int left = reqUserId.length - count;
                    byte[] n = new byte[left];
                    for (int i = 0; i<left; i++)
                    {
                        n[i] = reqUserId[i];
                    }
                    return n;
                }
            });

        }
        void send(int reqId , int result)
        {
            writeBuffer.put(intTobyte(reqId));
            writeBuffer.position(4);
            writeBuffer.put(intTobyte( result));
            writeBuffer.position(8);
            writeBuffer.put("hi".getBytes(StandardCharsets.UTF_8));
            writeBuffer.position(12);
            writeBuffer.flip();

            socketChannel.write(writeBuffer, null, new CompletionHandler<Integer, Object>()
            {
                @Override
                public void completed(Integer result, Object attachment)
                {
                    writeBuffer.clear();
                }

                @Override
                public void failed(Throwable exc, Object attachment)
                {
                    try
                    {
                        logr.severe("[accept fail" + socketChannel.getRemoteAddress()+ " : " + Thread.currentThread().getName()+ "]");
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
                    logr.info("login process completed");
                    return;
                case logout:
                    logoutProcess(reqId,userId,data);
                    logr.info("logout process completed");
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
                        send(reqId,-1);
                        logr.info(userId +" already exist");
                        return;
                    }
                }

                Client client1 = connections.get(connections.size() - 1);
                client1.userId = userId;
                logr.info(userId + " logged in");
                send(reqId,0);

            }
            else send(reqId,-1);
        }

        private void logoutProcess(int reqid, String userId, ByteBuffer data)
        {
            for (Client client : connections)
            {
                if (client.userId.equals(userId))
                {
                    try
                    {
                        logr.info(userId + " logged out , info deleted");
                        send(reqid,0);
//                        client.socketChannel.close();
                        connections.remove(client);
                        return;
                    } catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }
            send(reqid,-1);
        }

    }


    public byte[] intTobyte(int value) {
        byte[] bytes=new byte[4];
        bytes[0]=(byte)((value&0xFF000000)>>24);
        bytes[1]=(byte)((value&0x00FF0000)>>16);
        bytes[2]=(byte)((value&0x0000FF00)>>8);
        bytes[3]=(byte) (value&0x000000FF);

        return bytes;

    }
    public int byteToInt(byte[] src) {

        int newValue = 0;

        newValue |= (((int)src[0])<<24)&0xFF000000;
        newValue |= (((int)src[1])<<16)&0xFF0000;
        newValue |= (((int)src[2])<<8)&0xFF00;
        newValue |= (((int)src[3]))&0xFF;


        return newValue;
    }

    public static void main(String[] args)
    {
        setupLogger();
        ServerExample serverExample = new ServerExample();
        serverExample.startServer();
    }
}
