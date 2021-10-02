package adminserver;

import room.Room;
import user.Client;
import util.MyLog;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class ServerService
{
    private Logger logr;

    AsynchronousChannelGroup channelGroup;
    AsynchronousServerSocketChannel serverSocketChannel;
    public static List<Client> clientList = new Vector<>();
    public static List<Room> serverRoomList = new Vector<>();
    public static List<RemoteRoomServer> remoteRoomSeverList = new Vector<>();
    public static int global_textId = -1;

    public ServerService()
    {
        this.logr = MyLog.getLogr();
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
        } catch (Exception e)
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
                    String portInfo = socketChannel.getRemoteAddress().toString();
                    serverConnection(portInfo,socketChannel);
                    logr.info("[연결 수락: " + socketChannel.getRemoteAddress() + ": " + Thread.currentThread().getName() + "]");
                } catch (IOException e)
                {
                    logr.severe("[Client 연결 도중에 끊김 accept IOException fail]");
                }
                serverSocketChannel.accept(null, this);
            }

            @Override
            public void failed(Throwable exc, Void attachment)
            {
                if (serverSocketChannel.isOpen()) stopServer();
                logr.severe("[Client 연결 안됨 accept fail]");
            }

            void serverConnection(String portInfo,AsynchronousSocketChannel socketChannel)
            {
                String[] portInfos = portInfo.split(":");
                int port = Integer.parseInt(portInfos[1]);
                if (port == 10001 || port == 10002)
                {
                    RemoteRoomServer remoteRoomServer = new RemoteRoomServer(socketChannel, port);
                    remoteRoomSeverList.add(remoteRoomServer);
                    logr.info("[remote RoomServer port :" + port + " connected]");
                    logr.info("[remote RoomServer 연결 개수: " + remoteRoomSeverList.size() + "]");
                }
                else
                {
                    Client client = new Client(socketChannel);
                    clientList.add(client);
                    logr.info("[연결 개수: " + clientList.size() + "]");
                }
            }
        });
    }

    void stopServer()
    {
        try
        {
            clientList.clear();
            if (channelGroup != null && !channelGroup.isShutdown()) channelGroup.shutdown();
            logr.info("[서버 전체 종료]");
        } catch (Exception e)
        {
            logr.severe("[서버 전체 종료 실패]");
        }
    }

    public static void removeClient(Client client)
    {
        clientList.remove(client);
    }



    class RemoteRoomServer
    {
        ByteBuffer remoteReadBuf = ByteBuffer.allocate(10000);
        AsynchronousSocketChannel remoteRoomServerChannel;
        int ip0;
        int ip1;
        int ip2;
        int ip3;
        int port;

        public RemoteRoomServer(AsynchronousSocketChannel remoteRoomServerChannel, int port)
        {
            this.remoteRoomServerChannel = remoteRoomServerChannel;
            this.port = port;
            receive();
        }

        void receive()
        {
            remoteRoomServerChannel.read(remoteReadBuf, remoteReadBuf, new CompletionHandler<Integer, ByteBuffer>()
            {
                @Override
                public void completed(Integer result, ByteBuffer attachment)
                {
                    remoteReadBuf = ByteBuffer.allocate(10000);
                    remoteReadBuf.clear();
                    remoteRoomServerChannel.read(remoteReadBuf, remoteReadBuf, this);
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment)
                {
                    try
                    {
                        String portInfo = remoteRoomServerChannel.getRemoteAddress().toString();
                        closeRemoteSever(portInfo);
                        logr.severe("[Remote RoomServer receive fail" + remoteRoomServerChannel.getRemoteAddress() + " : " + Thread.currentThread().getName() + "]");
                    } catch (IOException e)
                    {
                    }
                }

                void closeRemoteSever(String portInfo) throws IOException
                {
                    String[] portInfos = portInfo.split(":");
                    int port = Integer.parseInt(portInfos[1]);
                    if (port == 10001 || port == 10002)
                    {
                        for (int i = 0; i < remoteRoomSeverList.size(); i++)
                        {
                            RemoteRoomServer remoteRoomServer = remoteRoomSeverList.get(i);
                            if (remoteRoomServer.port == port)
                            {
                                remoteRoomServer.remoteRoomServerChannel.close();
                                remoteRoomSeverList.remove(remoteRoomServer);
                                logr.info("[remote room server port : " + port + " removed]");
                                logr.info("[remote RoomServer 연결 개수: " + remoteRoomSeverList.size() + "]");
                                break;
                            }
                        }
                    }

                }

            });
        }
    }

    public static void main(String[] args)
    {
        ServerService serverExample = new ServerService();
        serverExample.startServer();
    }
}
