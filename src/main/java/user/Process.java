package user;

import adminserver.ServerService;
import room.Room;
import util.MyLog;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

import static util.ElseProcess.removeZero;

public class Process
{
    ByteBuffer byteBuffer;
    private Logger logr;
    private Object for_sendTextProcess;
    private Object for_enterRoomProcess;
    private Object for_inviteRoomProcess;
    private Object for_quitRoomProcess;

    Process(ByteBuffer byteBuffer,Object for_sendTextProcess, Object for_enterRoomProcess, Object for_inviteRoomProcess,Object for_quitRoomProcess)
    {
        this.byteBuffer = byteBuffer;
        logr = MyLog.getLogr();
        this.for_sendTextProcess  = for_sendTextProcess;
        this.for_enterRoomProcess = for_enterRoomProcess;
        this.for_inviteRoomProcess = for_inviteRoomProcess;
        this.for_quitRoomProcess = for_quitRoomProcess;
    }
    int getReqId()
    {
        return byteBuffer.getInt();
    }

    int getOperation()
    {
        return byteBuffer.getInt();
    }

    String getUserId()
    {
        byte[] reqUserId = new byte[16];
        byteBuffer.get(reqUserId,0,16);
        byteBuffer.position(24);
        return new String(removeZero(reqUserId),StandardCharsets.UTF_8);
    }

    int getRoomNum()
    {
        return byteBuffer.getInt();
    }

    public void loginProcess(int reqId, int operation, String userId, ByteBuffer data)
    {
        ByteBuffer addressesBuf = ByteBuffer.allocate(1000);
        List<Client> clientList = ServerService.clientList;
        for (Client client : clientList)
        {
            if (client.getUserId().equals(userId))
            {
                if (client.getSocketChannel().isOpen())
                {
                    Client newClient = clientList.get(clientList.size() - 1);
                    clientList.remove(newClient);
                    client.send(reqId, operation, 0, 4, ByteBuffer.allocate(0));
                    logr.info(userId + " already exist");
                }
                else if (!client.getSocketChannel().isOpen())
                {
                    Client newClient = clientList.get(clientList.size() - 1);
                    client.setSocketChannel(newClient.getSocketChannel());
                    clientList.remove(newClient);
                    int totalRoomNum = client.getMyRoomList().size();
                    addressesBuf.putInt(totalRoomNum);
                    for (int i = 0; i < totalRoomNum; i++)
                    {
                        Room room = client.getMyRoomList().get(i);
                        List<Integer> ipAddress = room.getIpAddress();
                        addressesBuf.putInt(ipAddress.get(0));
                        addressesBuf.putInt(ipAddress.get(1));
                        addressesBuf.putInt(ipAddress.get(2));
                        addressesBuf.putInt(ipAddress.get(3));
                        addressesBuf.putInt(room.getPort());
                    }
                    addressesBuf.flip();
                    client.send(reqId, operation, 0, 0, addressesBuf);
                    logr.info("[연결 개수: " + clientList.size() + "]");
                    logr.info(userId + " 재로그인 성공");
                }
                return;
            }
        }
        addressesBuf.flip();
        Client client1 = clientList.get(clientList.size() - 1);
        client1.setUserId(userId);
        logr.info(userId + " logged in");
        client1.send(reqId, operation, 0, 0, addressesBuf);
    }


    public void logoutProcess(int reqid, int operation, String userId, ByteBuffer data)
    {
        List<Client> clientList = ServerService.clientList;
        for (Client client : clientList)
        {
            if (client.getUserId().equals(userId))
            {
                try
                {
                    logr.info(userId + " logged out , connection info deleted");
                    client.send(reqid, operation, 0, 0, ByteBuffer.allocate(0));
                    client.setMyCurRoom(null);
                    client.getSocketChannel().close();
                    return;
                } catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    public void sendTextProcess(int reqId, int operation, String userId, ByteBuffer data)
    {
        List<Client> clientList = ServerService.clientList;
        Client currentSender = null;
        for (Client client : clientList)
        {
            if (client.getUserId().equals(userId))
            {
                currentSender = client;
                if (client.getMyRoomList().size() == 0 || client.getMyCurRoom() == null)
                {
                    client.send(reqId, operation, 0, 3, ByteBuffer.allocate(0));
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
        currentSender.getMyCurRoom().createAndAddText(textId, userId, text);
        int notRead = currentSender.getMyCurRoom().getNewTextNotRoomRead();

        for (Client client : currentSender.getMyCurRoom().getUserList())
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
            chatData.putInt(currentSender.getMyCurRoom().getRoomNum());
            chatData.put(currentSender.getUserId().getBytes(StandardCharsets.UTF_8));
            chatData.position(20);
            chatData.putInt(textId);
            chatData.putInt(notRead);
            chatData.putInt(text.length());
            chatData.put(text.getBytes(StandardCharsets.UTF_8));
            chatData.flip();
            if (client.getSocketChannel().isOpen() && client.getMyCurRoom() != null)
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


    public void createRoomProcess(int reqId, int operation, String userId, ByteBuffer data)
    {
        List<Client> clientList = ServerService.clientList;
        Client sender = null;
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
            if (client.getUserId().equals(userId))
            {
                sender = client;
                room.getUserList().add(client);
                client.getMyRoomList().add(room);
                client.setMyCurRoom(room);
                break;
            }
        }
        ServerService.serverRoomList.add(room);
        ByteBuffer forRoom = ByteBuffer.allocate(10);
        forRoom.putInt(roomNum);
        forRoom.flip();
        logr.info("[roomName : " + roomName + " roomNum : " + roomNum + " created by " + userId + " ]");
        sender.send(reqId, operation, 0, 0, forRoom);
    }

    public void inviteRoomProcess(int reqId, int operation, int roomNum, String userId, ByteBuffer data)
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
                if (client.getUserId().equals(user))
                {
                    for (Room room : client.getMyRoomList())
                    {
                        if (room == invited)
                        {
                            continue;
                        }
                    }
                    success = true;
                    client.setMyCurRoom(invited);
                    client.getMyRoomList().add(invited);
                    client.getMyCurRoom().getUserList().add(client);
                }
            }
        }
        Client invitee = null;
        for (Client c : clientList)
        {
            if (c.getUserId().equals(userId))
            {
                invitee = c;
                for (Client roomUser : invitee.getMyCurRoom().getUserList())
                {
                    ByteBuffer infoBuf = ByteBuffer.allocate(1000);
                    infoBuf.putInt(roomNum);
                    infoBuf.put(invitee.getUserId().getBytes(StandardCharsets.UTF_8));
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
        if (success) invitee.send(reqId, operation, 0, 0, ByteBuffer.allocate(0));
        else invitee.send(reqId, operation, 0, 1, ByteBuffer.allocate(0));
    }


    public void roomListProcess(int reqId, int operation, int roomNum, String userId, ByteBuffer data)
    {
        Client sender = ServerService.getSender(userId);
        int size = sender.getMyRoomList().size();
        ByteBuffer byteBuffer = ByteBuffer.allocate(10000);
        byteBuffer.putInt(size);
        for (int i = 0; i < size; i++)
        {
            Room room = sender.getMyRoomList().get(i);
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
        sender.send(reqId, operation, 0, 0, byteBuffer);
    }


    void enterRoomProcess(int reqId, int operation, int roomNum, String userId, ByteBuffer data)
    {
        Client sender = ServerService.getSender(userId);
        Room curRoom = null;
        for (Room r : sender.getMyRoomList())
        {
            if (r.getRoomNum() == roomNum)
            {
                curRoom = r;
                sender.setMyCurRoom(r);
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
                if (client.getSocketChannel().isOpen())
                {
                    try
                    {
                        if (client.getUserId().equals(sender.getUserId())) continue;
                        if (client.getMyCurRoom() == null) continue;
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
                sender.send(reqId, operation, 0, 0, responseBuf);
                for_enterRoomProcess.wait(100);
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }


    void quitRoomProcess(int reqId, int operation,int roomNum, String userId, ByteBuffer attachment)
    {
        Client sender = ServerService.getSender(userId);

        sender.setMyCurRoom(null);
        Room quitRoom = null;
        for(int i = 0; i<ServerService.serverRoomList.size(); i++)
        {
            Room room = ServerService.serverRoomList.get(i);
            if(room.getRoomNum() == roomNum)
            {
                quitRoom = room;
                quitRoom.removeUser(sender);
                synchronized (for_quitRoomProcess)
                {
                    try
                    {
                        sender.send(reqId,operation,0,0,ByteBuffer.allocate(0));
                        for_quitRoomProcess.wait(100);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
                break;
            }
        }

        List<Client> userList = quitRoom.getUserList();
        for (Client client : userList)
        {
            if(client.getUserId().equals(sender.getUserId())) continue;
            ByteBuffer allocate = ByteBuffer.allocate(20);
            allocate.putInt(roomNum);
            allocate.put(sender.getUserId().getBytes(StandardCharsets.UTF_8));
            allocate.position(20);
            allocate.flip();
            synchronized (for_quitRoomProcess)
            {
                try
                {
                    client.send(-1, operation, 1, 0, allocate);
                    for_quitRoomProcess.wait(100);
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }


    }
}
