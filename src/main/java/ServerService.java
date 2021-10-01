import util.LogFormatter;
import util.OperationEnum;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class ServerService
{
    private final static Logger logr = Logger.getGlobal();
    private Object for_inviteRoomProcess = new Object();
    private Object for_sendTextProcess = new Object();
    private Object for_enterRoomProcess = new Object();
    AsynchronousChannelGroup channelGroup;
    AsynchronousServerSocketChannel serverSocketChannel;
    List<Client> clientList = new Vector<>();
    List<Room> serverRoomList = new Vector<>();
    private int global_textId = -1;

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
                    logr.info("[연결 수락: " + socketChannel.getRemoteAddress() + ": " + Thread.currentThread().getName() + "]");
                } catch (IOException e)
                {
                    logr.severe("[Client 연결 도중에 끊김 accept IOException fail]");
                }

                Client client = new Client(socketChannel);
                clientList.add(client);
                logr.info("[연결 개수: " + clientList.size() + "]");

                serverSocketChannel.accept(null, this);
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
            clientList.clear();
            if (channelGroup != null && !channelGroup.isShutdown()) channelGroup.shutdown();
            logr.info("[서버 전체 종료]");
        } catch (Exception e)
        {
            logr.severe("[서버 전체 종료 실패]");
        }
    }

    class Client
    {
        AsynchronousSocketChannel socketChannel = null;
        ByteBuffer readBuffer = ByteBuffer.allocate(10000);
        ByteBuffer writeBuffer = ByteBuffer.allocate(10000);
        String userId = "not set yet";
        List<Room> myRoomList = new Vector<>();
        Room myCurRoom;


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
                        int reqId = byteToInt(reqIdReceive);
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
                        processOp(reqNum, reqId, userId, roomNum, attachment);

//                        for(Client client : connections) client.send(data);
                        readBuffer = ByteBuffer.allocate(10000);
                        readBuffer.clear();
                        if (socketChannel != null) socketChannel.read(readBuffer, readBuffer, this);
                    } catch (IOException e)
                    {
                    } catch (BufferUnderflowException e)
                    {
                        logr.info("receive 하는중에 BufferUnderflow 발생함");
                        readBuffer = ByteBuffer.allocate(10000);
                        readBuffer.clear();
                    }
                }


                @Override
                public void failed(Throwable exc, ByteBuffer attachment)
                {
                    try
                    {
                        logr.severe("[receive fail" + socketChannel.getRemoteAddress() + " : " + Thread.currentThread().getName() + "]");
                        socketChannel.close();
                        socketChannel = null;
                    } catch (IOException e)
                    {
                    }
                }
            });

        }

        void send(int reqId,int operation, int broadcastNum, int result, ByteBuffer leftover)
        {
            if (reqId != -1)
            {
                writeBuffer.put(intToByte(reqId));
                writeBuffer.position(4);
                writeBuffer.put(intToByte(operation));
                writeBuffer.position(8);
                writeBuffer.put(intToByte(result));
                writeBuffer.position(12);
                writeBuffer.put(leftover);
            } else if (reqId == -1)
            {
                writeBuffer.put(intToByte(reqId));
                writeBuffer.position(4);
                writeBuffer.put(intToByte(broadcastNum));
                writeBuffer.position(8);
                writeBuffer.put(leftover);
            }
            writeBuffer.flip();
            socketChannel.write(writeBuffer, null, new CompletionHandler<Integer, Object>()
            {
                @Override
                public void completed(Integer result, Object attachment)
                {

                    writeBuffer = ByteBuffer.allocate(10000);
                    writeBuffer.clear();
                    synchronized (for_inviteRoomProcess)
                    {
                        for_inviteRoomProcess.notify();
                    }
                    synchronized (for_sendTextProcess)
                    {
                        for_sendTextProcess.notify();
                    }
                    synchronized (for_enterRoomProcess)
                    {
                        for_enterRoomProcess.notify();
                    }

                }

                @Override
                public void failed(Throwable exc, Object attachment)
                {
                    try
                    {
                        logr.severe("[accept fail" + socketChannel.getRemoteAddress() + " : " + Thread.currentThread().getName() + "]");
                        clientList.remove(Client.this);
                        socketChannel.close();
                        synchronized (for_inviteRoomProcess)
                        {
                            for_inviteRoomProcess.notify();
                        }
                        synchronized (for_sendTextProcess)
                        {
                            for_sendTextProcess.notify();
                        }
                        synchronized (for_enterRoomProcess)
                        {
                            for_enterRoomProcess.notify();
                        }
                    } catch (IOException e)
                    {
                    }
                }
            });
        }


        void processOp(int operation, int reqId, String userId, int roomNum, ByteBuffer data)
        {
            OperationEnum op = OperationEnum.fromInteger(operation);
            switch (op)
            {
                case login:
                    loginProcess(reqId,operation, userId, data);
                    logr.info("login process completed");
                    return;
                case logout:
                    logoutProcess(reqId,operation, userId, data);
                    logr.info("logout process completed");
                    return;
                case sendText:
                    sendTextProcess(reqId,operation, userId, data);
                    return;
                case fileUpload:
                case fileList:
                case fileDownload:
                case fileDelete:
                case createRoom:
                    createRoomProcess(reqId,operation ,userId, data);
                    return;
                case quitRoom:
                case inviteRoom:
                    inviteRoomProcess(reqId,operation ,roomNum, userId, data);
                    return;
                case roomUserList:
                case roomList:
                    roomListProcess(reqId,operation,roomNum,userId,data);
                    return;
                case enterRoom:
                    enterRoomProcess(reqId,operation,roomNum,userId,data);
                    return;
                case enrollFile:

            }
        }


        private void loginProcess(int reqId,int operation, String userId, ByteBuffer data)
        {
            ByteBuffer addressesBuf = ByteBuffer.allocate(1000);
            if (userId != null)
            {
                for (Client client : clientList)
                {
                    if (client.userId.equals(userId))
                    {
                        if (client.socketChannel != null)
                        {
                            Client newClient = clientList.get(clientList.size() - 1);
                            clientList.remove(newClient);
                            send(reqId,operation, 0, 4, ByteBuffer.allocate(0));
                            logr.info(userId + " already exist");
                        }
                        else if (client.socketChannel == null) // 현재 자신이 속한 방 상태를 알려줌
                        {
                            Client newClient = clientList.get(clientList.size() - 1);
                            client.socketChannel = newClient.socketChannel;
                            clientList.remove(newClient);
                            int totalRoomNum = client.myRoomList.size();
                            addressesBuf.putInt(totalRoomNum);
                            for(int i = 0; i<totalRoomNum; i++)
                            {
                                Room room = client.myRoomList.get(i);
                                addressesBuf.putInt(room.ip0);
                                addressesBuf.putInt(room.ip1);
                                addressesBuf.putInt(room.ip2);
                                addressesBuf.putInt(room.ip3);
                                addressesBuf.putInt(room.port);
                            }

                            send(reqId,operation, 0, 0, addressesBuf);
                            logr.info("[연결 개수: " + clientList.size() + "]");
                            logr.info(userId + " 재로그인 성공");
                        }
                        return;
                    }
                }
                Client client1 = clientList.get(clientList.size() - 1);
                client1.userId = userId;
                logr.info(userId + " logged in");
                send(reqId,operation, 0, 0, addressesBuf);

            } else send(reqId,operation ,0, -1, ByteBuffer.allocate(0));
        }

        private void logoutProcess(int reqid,int operation, String userId, ByteBuffer data)
        {
            for (Client client : clientList)
            {
                if (client.userId.equals(userId))
                {
                    try
                    {
                        logr.info(userId + " logged out , connection info deleted");
                        send(reqid,operation, 0, 0, ByteBuffer.allocate(0));
//                        client.socketChannel.close();
                        client.socketChannel = null;
                        return;
                    } catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }
            send(reqid,operation, 0, 1, ByteBuffer.allocate(0));
        }

        private void sendTextProcess(int reqId,int operation ,String userId, ByteBuffer data)
        {
            Client currentSender = null;
            for (Client client : clientList)
            {
                if (client.userId.equals(userId))
                {
                    currentSender = client;
                    if (client.myRoomList.size() == 0 || client.myCurRoom == null)
                    {
                        send(reqId,operation, 0, 3, ByteBuffer.allocate(0));
                        return;
                    }
                }
            }
            byte[] t = new byte[1000];
            int position = data.position();
            int limit = data.limit();
            data.get(t, 0, limit - position);
            String text = new String(removeZero(t), StandardCharsets.UTF_8);

            int textId = ++global_textId;
            Text newText = new Text(textId, userId, text);
            currentSender.myCurRoom.chatLog.add(newText);
            int notRead = newText.notRoomRead;

            for (Client client : currentSender.myCurRoom.userList)
            {
                if (client == currentSender)
                {
                    synchronized (for_sendTextProcess)
                    {
                        try
                        {
                            client.send(reqId,operation, 0, 0, ByteBuffer.allocate(0));
                            for_sendTextProcess.wait();
                        } catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }

                ByteBuffer chatData = ByteBuffer.allocate(1000);
                chatData.putInt(currentSender.myCurRoom.roomNum);
                chatData.put(currentSender.userId.getBytes(StandardCharsets.UTF_8));
                chatData.position(20);
                chatData.putInt(textId);
                chatData.putInt(notRead);
                chatData.putInt(text.length());
                chatData.put(text.getBytes(StandardCharsets.UTF_8));
                chatData.flip();
                if(client.socketChannel != null)
                {
                    synchronized (for_sendTextProcess)
                    {
                        try
                        {
                            client.send(-1,operation, 2, 0, chatData);
                            for_sendTextProcess.wait();
                        } catch(InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }

                }
            }
        }

        private void createRoomProcess(int reqId,int operation, String userId, ByteBuffer data)
        {
            int roomNum = serverRoomList.size();
            byte[] roomNameReceive = new byte[20];
            int position = data.position();
            int limit = data.limit();
            data.get(roomNameReceive, 0, limit - position);
            byte[] roomNameNonzero = removeZero(roomNameReceive);
            String roomName = new String(roomNameNonzero, StandardCharsets.UTF_8);
            Room room = new Room(roomNum);
            room.roomName = roomName;
            for (Client client : clientList)
            {
                if (client.userId.equals(userId))
                {
                    room.userList.add(client);
                    client.myRoomList.add(room);
                    client.myCurRoom = room;
                    break;
                }
            }
            serverRoomList.add(room);
            ByteBuffer forRoom = ByteBuffer.allocate(10);
            forRoom.putInt(roomNum);
            forRoom.flip();
            logr.info("[roomName : " + roomName + " roomNum : " + roomNum + " created by " + userId + " ]");
            send(reqId,operation, 0, 0, forRoom);
        }

        void inviteRoomProcess(int reqId,int operation ,int roomNum, String userId, ByteBuffer data)
        {
            Room invited = null;
            for (Room room : serverRoomList)
            {
                if (room.roomNum == roomNum)
                {
                    invited = room;
                    break;
                }
            }
            int userCount = data.getInt();
            String[] users = new String[userCount];
            int curPos = data.position();
            for(int i = 0; i<userCount; i++)
            {
                byte[] userReceive = new byte[16];
                data.position(i*16+curPos);

                data.get(userReceive,0,16);
                String user = new String(removeZero(userReceive), StandardCharsets.UTF_8);
                users[i] = user;
            }
            boolean success = false;
            for (String user : users)
            {
                for (Client client : clientList)
                {
                    if (client.userId.equals(user))
                    {
                        for (Room room : client.myRoomList)
                        {
                            if (room == invited)
                            {
                                continue;
                            }
                        }
                        success = true;
                        client.myCurRoom = invited;
                        client.myRoomList.add(invited);
                        client.myCurRoom.userList.add(client);
                    }
                }
            }
            Client invitee = null;
            for (Client c : clientList)
            {
                if(c.userId.equals(userId))
                {
                    invitee = c;
                    for(Client roomUser : invitee.myCurRoom.userList)
                    {
                        ByteBuffer infoBuf = ByteBuffer.allocate(1000);
                        infoBuf.putInt(roomNum);
                        infoBuf.put(invitee.userId.getBytes(StandardCharsets.UTF_8));
                        infoBuf.position(20);
                        infoBuf.putInt(userCount);
                        int curPos0 = infoBuf.position();
                        int i = 0;
                        for(i = 0; i<userCount; i++)
                        {
                            infoBuf.position(curPos0+16*i);
                            infoBuf.put(users[i].getBytes(StandardCharsets.UTF_8));
                        }
                        infoBuf.position(curPos0+16*i);
                        infoBuf.flip();
                        synchronized (for_inviteRoomProcess)
                        {
                            try
                            {
                                roomUser.send(-1,operation,0,0, infoBuf);
                                for_inviteRoomProcess.wait();
                            } catch (InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                        }
                    }

                }
            }
            if(success)
            {
                invitee.send(reqId,operation, 0, 0, ByteBuffer.allocate(0));
            }
            else
            {
                invitee.send(reqId,operation,0,1, ByteBuffer.allocate(0));
            }

        }

        void roomListProcess(int reqId,int operation, int roomNum, String userId, ByteBuffer data)
        {
            Client sender = null;
            for(Client c : clientList)
            {
                if(c.userId.equals(userId))
                {
                    sender = c;
                    break;
                }
            }
            int size = sender.myRoomList.size();
            ByteBuffer byteBuffer = ByteBuffer.allocate(10000);
            byteBuffer.putInt(size);
            for(int i = 0; i<size; i++)
            {
                Room room = sender.myRoomList.get(i);
                int roomNum1 = room.roomNum;
                String roomName = room.roomName;
                int userSize = room.userList.size();
                int notRead = 0;
                for (int j = 0; j<room.chatLog.size(); j++)
                {
                    Text text = room.chatLog.get(j);
                    Integer check = text.readCheck.getOrDefault(userId, -1);
                    if ( check == 0) notRead++;
                }
                byteBuffer.putInt(roomNum1);
                int prevPos = byteBuffer.position();
                byteBuffer.put(roomName.getBytes(StandardCharsets.UTF_8));
                int curPos = byteBuffer.position();
                int plusPos = 16 - (curPos-prevPos);
                byteBuffer.position(curPos+plusPos);
                byteBuffer.putInt(userSize);
                byteBuffer.putInt(notRead);
            }
            byteBuffer.flip();
            send(reqId,operation,0,0,byteBuffer);
        }

        void enterRoomProcess(int reqId,int operation, int roomNum, String userId, ByteBuffer data)
        {
            Client sender = null;
            for(Client c : clientList)
            {
                if(c.userId.equals(userId))
                {
                    sender = c;
                    break;
                }
            }
            Room curRoom = null;
            for(Room r : sender.myRoomList)
            {
                if(r.roomNum == roomNum)
                {
                    curRoom = r;
                    myCurRoom = r;
                    break;
                }
            }
            List<Text> chatLog = curRoom.chatLog;
            int notRead = 0;
            List<Text> toSend = new ArrayList<>();
            for (Text text : chatLog)
            {
                Map<String, Integer> readCheck = text.readCheck;
                if(readCheck.getOrDefault(sender.userId,-1) == 0)
                {
                    notRead++;
                    text.notRoomRead--;
                    readCheck.put(sender.userId,1);
                    toSend.add(text);
                }
            }
            boolean everybodyRead = true;
            for (Text text: chatLog)
            {
                if(text.notRoomRead != 0)
                {
                    everybodyRead = false;
                    break;
                }
            }
            if(everybodyRead) chatLog.clear();


            for(int i = 0; i<curRoom.userList.size(); i++)
            {
                ByteBuffer enterRoomBroadcast = ByteBuffer.allocate(1000);
                enterRoomBroadcast.putInt(roomNum);
                enterRoomBroadcast.put(userId.getBytes(StandardCharsets.UTF_8));
                enterRoomBroadcast.position(20);
                if (toSend.size() > 0)
                {
                    enterRoomBroadcast.putInt(toSend.get(0).textId);
                    enterRoomBroadcast.putInt(toSend.get(toSend.size()-1).textId);
                }
                else if(toSend.size() == 0)
                {
                    enterRoomBroadcast.putInt(-1);
                    enterRoomBroadcast.putInt(-1);
                }
                enterRoomBroadcast.flip();
                Client client = curRoom.userList.get(i);
                synchronized (for_enterRoomProcess)
                {
                    if(client.socketChannel != null)
                    {
                        if(client.userId.equals(sender.userId)) continue;
                        try
                        {
                            client.send(-1,operation,5,0,enterRoomBroadcast);
                            for_enterRoomProcess.wait();
                        } catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            }

            ByteBuffer responseBuf = ByteBuffer.allocate(10000);
            responseBuf.putInt(notRead);
            for(int i = 0; i<notRead; i++)
            {
                Text text = toSend.get(i);
                int length = text.text.length();
                int curPos = responseBuf.position();
                responseBuf.put(text.sender.getBytes(StandardCharsets.UTF_8));
                responseBuf.position(curPos+16);
                int notRoomRead = text.notRoomRead;
                responseBuf.putInt(text.textId);
                responseBuf.putInt(notRoomRead);
                responseBuf.putInt(length);
                responseBuf.put(text.text.getBytes(StandardCharsets.UTF_8));
            }
            responseBuf.flip();

            synchronized (for_enterRoomProcess)
            {
                try
                {
                    send(reqId,operation,0,0,responseBuf);
                    for_enterRoomProcess.wait();
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }


        }
    }

    class Room
    {
        int roomNum;
        String roomName;
        List<Client> userList = new Vector<>();
        List<Text> chatLog = new ArrayList<>();
        int ip0;
        int ip1;
        int ip2;
        int ip3;
        int port;

        Room(int roomNum)
        {
            this.roomNum = roomNum;
        }

    }

    class Text
    {
        int textId;
        String sender;
        String text;
        Map<String,Integer> readCheck = new HashMap<>();
        int notRoomRead = 0;

        public Text(int textId, String sender, String text)
        {
            this.textId = textId;
            this.sender = sender;
            this.text = text;
            for(Client c : clientList)
            {
                if(c.userId.equals(sender))
                {
                    for(Client user : c.myCurRoom.userList)
                    {
                        if (user.socketChannel != null) readCheck.put(user.userId,1);
                        else
                        {
                            readCheck.put(user.userId,0);
                            notRoomRead++;
                        }
                    }
                }
            }
        }
    }


    public byte[] intToByte(int value)
    {
        byte[] bytes = new byte[4];
        bytes[0] = (byte) ((value & 0xFF000000) >> 24);
        bytes[1] = (byte) ((value & 0x00FF0000) >> 16);
        bytes[2] = (byte) ((value & 0x0000FF00) >> 8);
        bytes[3] = (byte) (value & 0x000000FF);

        return bytes;

    }

    public int byteToInt(byte[] src)
    {

        int newValue = 0;

        newValue |= (((int) src[0]) << 24) & 0xFF000000;
        newValue |= (((int) src[1]) << 16) & 0xFF0000;
        newValue |= (((int) src[2]) << 8) & 0xFF00;
        newValue |= (((int) src[3])) & 0xFF;


        return newValue;
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
        for (int i = 0; i < left; i++)
        {
            n[i] = reqUserId[i];
        }
        return n;
    }

    public static void main(String[] args)
    {
        setupLogger();
        ServerService serverExample = new ServerService();
        serverExample.startServer();
    }
}
