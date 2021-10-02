package user;

import adminserver.ServerService;
import org.w3c.dom.Text;
import room.Room;
import util.MyLog;
import util.OperationEnum;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Vector;
import java.util.logging.Logger;

import static util.ElseProcess.*;
import static util.ElseProcess.removeZero;

public class Client
{
    private Logger logr;
    private Object for_inviteRoomProcess = new Object();
    private Object for_sendTextProcess = new Object();
    private Object for_enterRoomProcess = new Object();
    ByteBuffer readBuffer = ByteBuffer.allocate(10000);
    ByteBuffer writeBuffer = ByteBuffer.allocate(10000);

    private AsynchronousSocketChannel socketChannel;
    private String userId = "not set yet";
    private List<Room> myRoomList = new Vector<>();
    private Room myCurRoom;

    public Client(AsynchronousSocketChannel socketChannel)
    {
        this.socketChannel = socketChannel;
        this.logr = MyLog.getLogr();
        receive();
    }

    public AsynchronousSocketChannel getSocketChannel()
    {
        return socketChannel;
    }

    public String getUserId()
    {
        return userId;
    }

    public List<Room> getMyRoomList()
    {
        return myRoomList;
    }

    public Room getMyCurRoom()
    {
        return myCurRoom;
    }


    void receive()
    {
        socketChannel.read(readBuffer, null, new CompletionHandler<Integer, ByteBuffer>()
        {
            @Override
            public void completed(Integer result, ByteBuffer attachment)
            {
                try
                {
                    logr.info("[요청 처리: " + socketChannel.getRemoteAddress() + ": " + Thread.currentThread().getName() + "]");
                    processOp(readBuffer);
                    readBuffer = ByteBuffer.allocate(10000);
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
                } catch (IOException e)
                {
                }
            }
        });

    }

    void send(int reqId, int operation, int broadcastNum, int result, ByteBuffer leftover)
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
                    ServerService.removeClient(Client.this);
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


    void processOp(ByteBuffer attachment)
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
        int operation = byteToInt(reqNumReceive);
        attachment.position(8);
        attachment.get(reqUserId);
        byte[] userIdNonzero = removeZero(reqUserId);
        String userId = new String(userIdNonzero, StandardCharsets.UTF_8);
        attachment.position(24);
        attachment.get(reqRoomNum);
        int roomNum = byteToInt(reqRoomNum);
        attachment.position(28);
        OperationEnum op = OperationEnum.fromInteger(operation);
        switch (op)
        {
            case login:
                loginProcess(reqId, operation, userId, attachment);
                logr.info("login process completed");
                return;
            case logout:
                logoutProcess(reqId, operation, userId, attachment);
                logr.info("logout process completed");
                return;
            case sendText:
                sendTextProcess(reqId, operation, userId, attachment);
                return;
            case fileUpload:
            case fileList:
            case fileDownload:
            case fileDelete:
            case createRoom:
                createRoomProcess(reqId, operation, userId, attachment);
                return;
            case quitRoom:
            case inviteRoom:
                inviteRoomProcess(reqId, operation, roomNum, userId, attachment);
                return;
            case roomUserList:
            case roomList:
                roomListProcess(reqId, operation, roomNum, userId, attachment);
                return;
            case enterRoom:
                enterRoomProcess(reqId, operation, roomNum, userId, attachment);
                return;
            case enrollFile:

        }
    }


    private void loginProcess(int reqId, int operation, String userId, ByteBuffer data)
    {
        ByteBuffer addressesBuf = ByteBuffer.allocate(1000);
        if (userId != null)
        {
            List<Client> clientList = ServerService.clientList;
            for (Client client : clientList)
            {
                if (client.userId.equals(userId))
                {
                    if (client.socketChannel.isOpen())
                    {
                        Client newClient = clientList.get(clientList.size() - 1);
                        clientList.remove(newClient);
                        send(reqId, operation, 0, 4, ByteBuffer.allocate(0));
                        logr.info(userId + " already exist");
                    } else if (!client.socketChannel.isOpen()) // 현재 자신이 속한 방 상태를 알려줌
                    {
                        Client newClient = clientList.get(clientList.size() - 1);
                        client.socketChannel = newClient.socketChannel;
                        clientList.remove(newClient);
                        int totalRoomNum = client.myRoomList.size();
                        addressesBuf.putInt(totalRoomNum);
                        for (int i = 0; i < totalRoomNum; i++)
                        {
                            Room room = client.myRoomList.get(i);
                            List<Integer> ipAddress = room.getIpAddress();
                            addressesBuf.putInt(ipAddress.get(0));
                            addressesBuf.putInt(ipAddress.get(1));
                            addressesBuf.putInt(ipAddress.get(2));
                            addressesBuf.putInt(ipAddress.get(3));
                            addressesBuf.putInt(room.getPort());
                        }
                        addressesBuf.flip();
                        send(reqId, operation, 0, 0, addressesBuf);
                        logr.info("[연결 개수: " + clientList.size() + "]");
                        logr.info(userId + " 재로그인 성공");
                    }
                    return;
                }
            }
            addressesBuf.flip();
            Client client1 = clientList.get(clientList.size() - 1);
            client1.userId = userId;
            logr.info(userId + " logged in");
            send(reqId, operation, 0, 0, addressesBuf);

        } else send(reqId, operation, 0, -1, ByteBuffer.allocate(0));
    }

    private void logoutProcess(int reqid, int operation, String userId, ByteBuffer data)
    {
        List<Client> clientList = ServerService.clientList;
        for (Client client : clientList)
        {
            if (client.userId.equals(userId))
            {
                try
                {
                    logr.info(userId + " logged out , connection info deleted");
                    send(reqid, operation, 0, 0, ByteBuffer.allocate(0));
//                        client.socketChannel.close();
                    client.socketChannel = null;
                    return;
                } catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
        send(reqid, operation, 0, 1, ByteBuffer.allocate(0));
    }

    private void sendTextProcess(int reqId, int operation, String userId, ByteBuffer data)
    {
        List<Client> clientList = ServerService.clientList;
        Client currentSender = null;
        for (Client client : clientList)
        {
            if (client.userId.equals(userId))
            {
                currentSender = client;
                if (client.myRoomList.size() == 0 || client.myCurRoom == null)
                {
                    send(reqId, operation, 0, 3, ByteBuffer.allocate(0));
                    return;
                }
            }
        }
        byte[] t = new byte[1000];
        int position = data.position();
        int limit = data.limit();
        data.get(t, 0, limit - position);
        String text = new String(removeZero(t), StandardCharsets.UTF_8);

        ServerService.global_textId++;
        int textId = ServerService.global_textId;
        myCurRoom.createAndAddText(textId, userId, text);
        int notRead = myCurRoom.getNewTextNotRoomRead();

        for (Client client : currentSender.myCurRoom.getUserList())
        {
            if (client == currentSender)
            {
                synchronized (for_sendTextProcess)
                {
                    try
                    {
                        client.send(reqId, operation, 0, 0, ByteBuffer.allocate(0));
                        for_sendTextProcess.wait(100);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }

            ByteBuffer chatData = ByteBuffer.allocate(1000);
            chatData.putInt(currentSender.myCurRoom.getRoomNum());
            chatData.put(currentSender.userId.getBytes(StandardCharsets.UTF_8));
            chatData.position(20);
            chatData.putInt(textId);
            chatData.putInt(notRead);
            chatData.putInt(text.length());
            chatData.put(text.getBytes(StandardCharsets.UTF_8));
            chatData.flip();
            if (client.socketChannel.isOpen())
            {
                synchronized (for_sendTextProcess)
                {
                    try
                    {
                        client.send(-1, operation, 2, 0, chatData);
                        for_sendTextProcess.wait(100);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }

            }
        }
    }

    private void createRoomProcess(int reqId, int operation, String userId, ByteBuffer data)
    {
        List<Client> clientList = ServerService.clientList;
        int roomNum = ServerService.serverRoomList.size();
        byte[] roomNameReceive = new byte[20];
        int position = data.position();
        int limit = data.limit();
        data.get(roomNameReceive, 0, limit - position);
        byte[] roomNameNonzero = removeZero(roomNameReceive);
        String roomName = new String(roomNameNonzero, StandardCharsets.UTF_8);
        Room room = new Room(roomNum);
        room.setRoomName( roomName);
        for (Client client : clientList)
        {
            if (client.userId.equals(userId))
            {
                room.getUserList().add(client);
                client.myRoomList.add(room);
                client.myCurRoom = room;
                break;
            }
        }
        ServerService.serverRoomList.add(room);
        ByteBuffer forRoom = ByteBuffer.allocate(10);
        forRoom.putInt(roomNum);
        forRoom.flip();
        logr.info("[roomName : " + roomName + " roomNum : " + roomNum + " created by " + userId + " ]");
        send(reqId, operation, 0, 0, forRoom);
    }

    void inviteRoomProcess(int reqId, int operation, int roomNum, String userId, ByteBuffer data)
    {

        List<Client> clientList = ServerService.clientList;
        Room invited = null;
        for (Room room : ServerService.serverRoomList)
        {
            if (room.getRoomNum() == roomNum)
            {
                invited = room;
                break;
            }
        }
        int userCount = data.getInt();
        String[] users = new String[userCount];
        int curPos = data.position();
        for (int i = 0; i < userCount; i++)
        {
            byte[] userReceive = new byte[16];
            data.position(i * 16 + curPos);

            data.get(userReceive, 0, 16);
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
                    client.myCurRoom.getUserList().add(client);
                }
            }
        }
        Client invitee = null;
        for (Client c : clientList)
        {
            if (c.userId.equals(userId))
            {
                invitee = c;
                for (Client roomUser : invitee.myCurRoom.getUserList())
                {
                    ByteBuffer infoBuf = ByteBuffer.allocate(1000);
                    infoBuf.putInt(roomNum);
                    infoBuf.put(invitee.userId.getBytes(StandardCharsets.UTF_8));
                    infoBuf.position(20);
                    infoBuf.putInt(userCount);
                    int curPos0 = infoBuf.position();
                    int i = 0;
                    for (i = 0; i < userCount; i++)
                    {
                        infoBuf.position(curPos0 + 16 * i);
                        infoBuf.put(users[i].getBytes(StandardCharsets.UTF_8));
                    }
                    infoBuf.position(curPos0 + 16 * i);
                    infoBuf.flip();
                    synchronized (for_inviteRoomProcess)
                    {
                        try
                        {
                            roomUser.send(-1, operation, 0, 0, infoBuf);
                            for_inviteRoomProcess.wait(100);
                        } catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }

            }
        }
        if (success)
        {
            invitee.send(reqId, operation, 0, 0, ByteBuffer.allocate(0));
        } else
        {
            invitee.send(reqId, operation, 0, 1, ByteBuffer.allocate(0));
        }

    }

    void roomListProcess(int reqId, int operation, int roomNum, String userId, ByteBuffer data)
    {
        List<Client> clientList = ServerService.clientList;
        Client sender = null;
        for (Client c : clientList)
        {
            if (c.userId.equals(userId))
            {
                sender = c;
                break;
            }
        }
        int size = sender.myRoomList.size();
        ByteBuffer byteBuffer = ByteBuffer.allocate(10000);
        byteBuffer.putInt(size);
        for (int i = 0; i < size; i++)
        {
            Room room = sender.myRoomList.get(i);
            int roomNum1 = room.getRoomNum();
            String roomName = room.getRoomName();
            int userSize = room.getUserList().size();
            int notRead = room.getUserNotRoomRead(userId);
            byteBuffer.putInt(roomNum1);
            int prevPos = byteBuffer.position();
            byteBuffer.put(roomName.getBytes(StandardCharsets.UTF_8));
            int curPos = byteBuffer.position();
            int plusPos = 16 - (curPos - prevPos);
            byteBuffer.position(curPos + plusPos);
            byteBuffer.putInt(userSize);
            byteBuffer.putInt(notRead);
        }
        byteBuffer.flip();
        send(reqId, operation, 0, 0, byteBuffer);
    }

    void enterRoomProcess(int reqId, int operation, int roomNum, String userId, ByteBuffer data)
    {
        List<Client> clientList = ServerService.clientList;
        Client sender = null;
        for (Client c : clientList)
        {
            if (c.userId.equals(userId))
            {
                sender = c;
                break;
            }
        }
        Room curRoom = null;
        for (Room r : sender.myRoomList)
        {
            if (r.getRoomNum() == roomNum)
            {
                curRoom = r;
                myCurRoom = r;
                break;
            }
        }
        List<Room.Text> chatLog = curRoom.getChatLog();
        int notRead = curRoom.getUserNotRoomRead(userId);
        List<Room.Text> toSend = curRoom.getUserNotRoomReadTextList(userId);
        boolean everybodyRead = true;
        for (Room.Text text : chatLog)
        {
            if (text.getNotRoomRead() != 0)
            {
                everybodyRead = false;
                break;
            }
        }
        if (everybodyRead) chatLog.clear();


        for (int i = 0; i < curRoom.getUserList().size(); i++)
        {
            ByteBuffer enterRoomBroadcast = ByteBuffer.allocate(1000);
            enterRoomBroadcast.putInt(roomNum);
            enterRoomBroadcast.put(userId.getBytes(StandardCharsets.UTF_8));
            enterRoomBroadcast.position(20);
            if (toSend.size() > 0)
            {
                enterRoomBroadcast.putInt(toSend.get(0).getTextId());
                enterRoomBroadcast.putInt(toSend.get(toSend.size() - 1).getTextId());
            } else if (toSend.size() == 0)
            {
                enterRoomBroadcast.putInt(-1);
                enterRoomBroadcast.putInt(-1);
            }
            enterRoomBroadcast.flip();
            Client client = curRoom.getUserList().get(i);;
            synchronized (for_enterRoomProcess)
            {
                if (client.socketChannel != null)
                {
                    if (client.userId.equals(sender.userId)) continue;
                    try
                    {
                        client.send(-1, operation, 5, 0, enterRoomBroadcast);
                        for_enterRoomProcess.wait(100);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }

        ByteBuffer responseBuf = ByteBuffer.allocate(10000);
        responseBuf.putInt(notRead);
        for (int i = 0; i < notRead; i++)
        {
            Room.Text text = toSend.get(i);
            int length = text.getText().length();
            int curPos = responseBuf.position();
            responseBuf.put(text.getSender().getBytes(StandardCharsets.UTF_8));
            responseBuf.position(curPos + 16);
            int notRoomRead = text.getNotRoomRead();
            responseBuf.putInt(text.getTextId());
            responseBuf.putInt(notRoomRead);
            responseBuf.putInt(length);
            responseBuf.put(text.getText().getBytes(StandardCharsets.UTF_8));
        }
        responseBuf.flip();

        synchronized (for_enterRoomProcess)
        {
            try
            {
                send(reqId, operation, 0, 0, responseBuf);
                for_enterRoomProcess.wait(100);
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }


    }
}
