package adminserver;

import remoteroomserver.RemoteRoomServer;
import domain.Room;
import domain.Client;
import service.ClientRequestService;
import service.ClientResponseService;
import service.ProcessService;
import util.MyLog;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.logging.Logger;

public class ServerService
{
    private Logger logr;

    public static Selector selector;
    ServerSocketChannel serverSocketChannel;
    public static List<Client> clientList = new Vector<>();
    public static List<Room> serverRoomList = new Vector<>();
    public static List<RemoteRoomServer> remoteRoomSeverList = new Vector<>();
    public static int global_textId = -1;
    private final ClientRequestService crs;
    private final ClientResponseService clientResponseService;
    private final ProcessService processService;
    ExecutorService executorService = Executors.newFixedThreadPool(4);
    public static Object connectLock = new Object();

    public ServerService(ClientRequestService crs, ClientResponseService clientResponseService, ProcessService processService)
    {
        this.crs = crs;
        this.processService = processService;
        this.logr = MyLog.getLogr();
        this.clientResponseService = clientResponseService;
    }


    public void startServer() {
        try {
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            serverSocketChannel.bind(new InetSocketAddress(5001));
            logr.info("[서버 연결됨]");
        } catch (Exception e) {
            if (serverSocketChannel.isOpen()) stopServer();
            logr.severe("[서버 연결 실패 startServer]");
            return;
        }

        while (true) {
            try {
                int keyCount = selector.select();
                if (keyCount == 0) continue;
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    if (selectionKey.isAcceptable()) {
                        accept(selectionKey);
                    } else if (selectionKey.isReadable()) {
                        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                        ByteBuffer readBuffer = ByteBuffer.allocate(10000);
                        try {
                            int byteCount = socketChannel.read(readBuffer);
                            executorService.submit(() ->
                                    crs.receive(selectionKey, readBuffer, byteCount)
                            );
                        } catch (IOException e) {
                            e.printStackTrace();
                            processService.closeBroadcast(selectionKey);
                        }

                    } else if (selectionKey.isWritable()) {
                        executorService.submit(() ->
                                clientResponseService.send(selectionKey));
                    }
                    iterator.remove();
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (serverSocketChannel.isOpen()) stopServer();
                break;
            }
        }
    }

    void stopServer() {

        try {
            Iterator<Client> iterator = clientList.iterator();
            while (iterator.hasNext()) {
                Client client = iterator.next();
                client.getSocketChannel().close();
                iterator.remove();
            }
            if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
                serverSocketChannel.close();
                logr.info("[서버 전체 종료]");
            }
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
        } catch (Exception e) {
            logr.severe("[서버 전체 종료 실패]");

        }
    }

    void accept(SelectionKey selectionKey) {
        try {
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectionKey.channel();
            SocketChannel socketChannel = serverSocketChannel.accept();
            String portInfo = socketChannel.getRemoteAddress().toString();
            serverConnection(portInfo, socketChannel);
            logr.info("[연결 수락: " + socketChannel.getRemoteAddress() + ": " + Thread.currentThread().getName() + "]");
        } catch (IOException e) {
            logr.severe("[Client 연결 도중에 끊김 accept IOException fail]");
            if (serverSocketChannel.isOpen()) {
                stopServer();
            }
        }


    }

    void serverConnection(String portInfo, SocketChannel socketChannel) {
        String[] portInfos = portInfo.split(":");
        int port = Integer.parseInt(portInfos[1]);
        if (port == 10001 || port == 10002) {
//            RemoteRoomServer remoteRoomServer = new RemoteRoomServer(socketChannel, port);
//            remoteRoomSeverList.add(remoteRoomServer);
//            logr.info("[remote RoomServer port :" + port + " connected]");
//            logr.info("[remote RoomServer 연결 개수: " + remoteRoomSeverList.size() + "]");
        } else {
            Client client = new Client(socketChannel);
            client.getSocketChannel().keyFor(selector);
            clientList.add(client);
            logr.info("[연결 개수: " + clientList.size() + "]");
        }
    }

    public static Client getSender(String userId) {
        List<Client> clientList = ServerService.clientList;
        Client sender = null;
        for (Client c : clientList) {
            if (c.getUserId().equals(userId)) {
                sender = c;
                break;
            }
        }
        return sender;
    }


}
