package service;

import adminserver.ServerService;
import domain.Client;
import domain.MyFile;
import domain.Room;
import domain.Text;
import util.MyLog;
import util.OperationEnum;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static util.ElseProcess.getTime;
import static util.ElseProcess.removeZero;

public class ProcessService
{
    private final ClientResponseService crs;
    private final Logger logr = MyLog.getLogr();
    private Object for_sendTextProcess = new Object();
    private Object for_enterRoomProcess = new Object();
    private Object for_inviteRoomProcess = new Object();
    private Object for_quitRoomProcess = new Object();
    private Object for_uploadFileProcess = new Object();
    private Object for_deleteFileProcess = new Object();

    public ProcessService()
    {
        this.crs = new ClientResponseService(for_sendTextProcess,for_enterRoomProcess,for_inviteRoomProcess,for_quitRoomProcess,for_uploadFileProcess,for_deleteFileProcess);
    }

    int getReqId(ByteBuffer byteBuffer)
    {
        return byteBuffer.getInt();
    }

    int getOperation(ByteBuffer byteBuffer)
    {
        return byteBuffer.getInt();
    }

    String getUserId(ByteBuffer byteBuffer)
    {
        byte[] reqUserId = new byte[16];
        byteBuffer.get(reqUserId,0,16);
        byteBuffer.position(24);
        return new String(removeZero(reqUserId),StandardCharsets.UTF_8);
    }

    int getRoomNum(ByteBuffer byteBuffer)
    {
        return byteBuffer.getInt();
    }


    public void processOp(ByteBuffer attachment)
    {
        attachment.flip();
        int reqId = getReqId(attachment);
        int operation = getOperation(attachment);
        String userId = getUserId(attachment);
        int roomNum =getRoomNum(attachment);
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
                fileUploadProcess(reqId,operation,roomNum,userId,attachment);
                return;
            case fileList:
                fileListProcess(reqId,operation,roomNum,userId,attachment);
                return;
            case fileDownload:
                fileDownloadProcess(reqId,operation,roomNum,userId,attachment);
                return;
            case fileDelete:
                fileDeleteProcess(reqId,operation,roomNum,userId,attachment);
                return;
            case createRoom:
                createRoomProcess(reqId, operation, userId, attachment);
                return;
            case quitRoom:
                quitRoomProcess(reqId,operation,roomNum,userId,attachment);
                return;
            case inviteRoom:
                inviteRoomProcess(reqId, operation, roomNum, userId, attachment);
                return;
            case roomUserList:
                roomUserListProcess(reqId,operation,roomNum,userId,attachment);
                return;
            case roomList:
                roomListProcess(reqId, operation, roomNum, userId, attachment);
                return;
            case enterRoom:
                enterRoomProcess(reqId, operation, roomNum, userId, attachment);
                return;
            case enrollFile:
                enrollFileProcess(reqId,operation,roomNum,userId,attachment);
                return;
            case fileInfo:
                crs.send(reqId,14,0,-1,ByteBuffer.allocate(0),ServerService.getSender(userId));
                return;
            case exitRoom:
                exitRoomProcess(reqId,operation,roomNum,userId,attachment);
                return;

        }
    }


    public void loginProcess(int reqId, int operation, String userId, ByteBuffer data)
    {
        ByteBuffer addressesBuf = ByteBuffer.allocate(1000);
        List<Client> clientList = ServerService.clientList;
        for (Client client : clientList)
        {
            if (client.getUserId().equals(userId))
            {
                if (!client.getSocketChannel().isOpen())
                {
                    Client newClient = clientList.get(clientList.size() - 1);
                    client.setSocketChannel(newClient.getSocketChannel());
                    clientList.remove(newClient);
                    client.setState(1);
                    for (Room room : client.getMyRoomList())
                    {
                        Map<Client, Integer> userStates = room.getUserStates();
                        userStates.put(client,2);
                    }
                    if(client.getNotYetReadBuffers().size()>0)
                    {
                        for (ByteBuffer notYetReadBuffer : client.getNotYetReadBuffers())
                        {
                            synchronized (for_sendTextProcess)
                            {
                                try
                                {
                                    crs.send(-1,0,2,0,notYetReadBuffer,client);
                                    for_sendTextProcess.wait(100);
                                    for_sendTextProcess.wait(100);
                                } catch (InterruptedException e)
                                {
                                    e.printStackTrace();
                                }
                            }
                        }

                        client.getNotYetReadBuffers().clear();
                    }

                    crs.send(reqId, operation, 0, 0, ByteBuffer.allocate(0),client);

                    logr.info("[연결 개수: " + clientList.size() + "]");
                    logr.info(userId + " 재로그인 성공");
                }
                return;
            }
        }
        addressesBuf.flip();
        Client client1 = clientList.get(clientList.size() - 1);
        client1.setUserId(userId);
        client1.setState(1);
        logr.info(userId + " logged in");
        crs.send(reqId, operation, 0, 0, addressesBuf,client1);
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
                    crs.send(reqid, operation, 0, 0, ByteBuffer.allocate(0),client);
                    Map<Client, Integer> userStates = client.getMyCurRoom().getUserStates();
                    userStates.put(client,0);
                    client.setMyCurRoom(null);
                    client.setState(0);
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
                    crs.send(reqId, operation, 0, 3, ByteBuffer.allocate(0),client);
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
        currentSender.getMyCurRoom().createAndAddText(textId, userId, text, currentSender.getMyCurRoom());
        int notRead = currentSender.getMyCurRoom().getNewTextNotRoomRead();

        for (Client client : currentSender.getMyCurRoom().getUserList())
        {
            if (client == currentSender)
            {
                synchronized (for_sendTextProcess)
                {
                    try
                    {
                        crs.send(reqId, operation, 0, 0, ByteBuffer.allocate(0),client);
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
            chatData.put(getTime().getBytes(StandardCharsets.UTF_8));
            chatData.position(32);
            chatData.putInt(textId);
            chatData.putInt(notRead);
            chatData.putInt(text.length());
            chatData.put(text.getBytes(StandardCharsets.UTF_8));
            chatData.flip();
            synchronized (for_sendTextProcess)
            {
                try
                {
                    crs.send(-1, operation, 2, 0, chatData,client);
                    for_sendTextProcess.wait(100);
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
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
                Map<Client, Integer> userStates = room.getUserStates();
                userStates.put(sender,1);
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
        crs.send(reqId, operation, 0, 0, forRoom,sender);
    }

    public void inviteRoomProcess(int reqId, int operation, int roomNum, String userId, ByteBuffer data)
    {
        List<Client> clientList = ServerService.clientList;

        Client invitee = null;
        for (Client c : clientList)
        {
            if (c.getUserId().equals(userId))
            {
                invitee = c;
                break;
            }
        }
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
                    if(client.getMyRoomList().size() == 0)
                    {
                        client.setMyCurRoom(invited);
                        Map<Client, Integer> userStates = invited.getUserStates();
                        userStates.put(client,1);
                    }
                    client.getMyRoomList().add(invited);
                    invitee.getMyCurRoom().getUserList().add(client);
                }
            }
        }


        for (Client roomUser : invitee.getMyCurRoom().getUserList())
        {
            ByteBuffer infoBuf = ByteBuffer.allocate(1000);
            infoBuf.putInt(roomNum);
            infoBuf.put(invitee.getUserId().getBytes(StandardCharsets.UTF_8));
            infoBuf.position(20);
            infoBuf.put(getTime().getBytes(StandardCharsets.UTF_8));
            infoBuf.position(32);
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
                    crs.send(-1, operation, 0, 0, infoBuf,roomUser);
                    for_inviteRoomProcess.wait(100);
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }
        if (success) crs.send(reqId, operation, 0, 0, ByteBuffer.allocate(0),invitee);
        else crs.send(reqId, operation, 0, 1, ByteBuffer.allocate(0),invitee);
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
        crs.send(reqId, operation, 0, 0, byteBuffer,sender);
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
        List<Text> chatLog = curRoom.getChatLog();
        int notRead = curRoom.getUserNotRoomRead(userId);
        List<Text> toSend = curRoom.getUserNotRoomReadTextList(userId);
        Map<Client, Integer> userStates = curRoom.getUserStates();
        userStates.put(sender,1);
        boolean everybodyRead = true;
        for (Text text : chatLog)
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
            enterRoomBroadcast.put(getTime().getBytes(StandardCharsets.UTF_8));
            enterRoomBroadcast.position(32);
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
                if (client.getSocketChannel().isOpen() && curRoom.getUserStates().getOrDefault(client,-1) == 1)
                {
                    try
                    {
                        if (client.getMyCurRoom() == null) continue;
                        crs.send(-1, operation, 5, 0, enterRoomBroadcast,client);
                        for_enterRoomProcess.wait(100);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }

        synchronized (for_enterRoomProcess)
        {
            try
            {
                crs.send(reqId, operation, 0, 0, ByteBuffer.allocate(0),sender);
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
                Map<Client, Integer> userStates = quitRoom.getUserStates();
                userStates.remove(sender);
                sender.getMyRoomList().remove(quitRoom);
                synchronized (for_quitRoomProcess)
                {
                    try
                    {
                        crs.send(reqId,operation,0,0,ByteBuffer.allocate(0),sender);
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
            ByteBuffer allocate = ByteBuffer.allocate(100);
            allocate.putInt(roomNum);
            allocate.put(sender.getUserId().getBytes(StandardCharsets.UTF_8));
            allocate.position(20);
            allocate.put(getTime().getBytes(StandardCharsets.UTF_8));
            allocate.position(32);
            allocate.flip();
            synchronized (for_quitRoomProcess)
            {
                try
                {
                    crs.send(-1, operation, 1, 0, allocate,client);
                    for_quitRoomProcess.wait(100);
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }


    void exitRoomProcess(int reqId, int operation,int roomNum, String userId, ByteBuffer attachment)
    {

        Client sender = ServerService.getSender(userId);

        sender.setMyCurRoom(null);
        for(int i = 0; i<ServerService.serverRoomList.size(); i++)
        {
            Room room = ServerService.serverRoomList.get(i);
            if(room.getRoomNum() == roomNum)
            {
                Map<Client, Integer> userStates = room.getUserStates();
                userStates.put(sender,2);
                crs.send(reqId,operation,0,0,ByteBuffer.allocate(0),sender);
                break;
            }
        }
    }

    void roomUserListProcess(int reqId, int operation,int roomNum, String userId, ByteBuffer attachment)
    {
        Client sender = ServerService.getSender(userId);
        Room myCurRoom = sender.getMyCurRoom();

        ByteBuffer infoBuf = ByteBuffer.allocate(1000);
        int size = myCurRoom.getUserList().size();
        infoBuf.putInt(size);
        for (Map.Entry<Client, Integer> clientIntegerEntry : myCurRoom.getUserStates().entrySet())
        {
            int curPos = infoBuf.position();
            Client key = clientIntegerEntry.getKey();
            Integer value = clientIntegerEntry.getValue();
            infoBuf.put(key.getUserId().getBytes(StandardCharsets.UTF_8));
            infoBuf.position(curPos + 16);
            infoBuf.putInt(value);
        }
        infoBuf.flip();
        crs.send(reqId,operation,0,0,infoBuf,sender);

    }

    void enrollFileProcess(int reqId, int operation,int roomNum, String userId, ByteBuffer attachment)
    {
        Client sender = ServerService.getSender(userId);

        Room myCurRoom = sender.getMyCurRoom();

        attachment.getInt();

        byte[] fileNameReceive = new byte[16];
        attachment.get(fileNameReceive,0,16);
        String fileName = new String(removeZero(fileNameReceive), StandardCharsets.UTF_8);
        String checkedFileName = myCurRoom.checkFileNameCheck(fileName);
        int fileSize = attachment.getInt();

        int fileNum = myCurRoom.getFileNum();

        Path path = Paths.get("./temp_db/" + roomNum + "/" + fileNum + "/" + checkedFileName);
        try
        {
            Files.createDirectories(path.getParent());
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        myCurRoom.incrementFileNum();

        myCurRoom.createNewFile(fileNum,checkedFileName,path,fileSize);
        ByteBuffer infoBuf = ByteBuffer.allocate(100);
        infoBuf.putInt(fileNum);
        infoBuf.put(checkedFileName.getBytes(StandardCharsets.UTF_8));
        infoBuf.position(20);
        infoBuf.putInt(fileSize);
        infoBuf.flip();

        crs.send(reqId,13,0,0,infoBuf,sender);
        try
        {
            Thread.sleep(100);
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    void fileUploadProcess(int reqId, int operation,int roomNum, String userId, ByteBuffer attachment)
    {
        Client sender = ServerService.getSender(userId);
        Room myCurRoom = sender.getMyCurRoom();

        int fileNum = attachment.getInt();
        int filePosition = attachment.getInt();
        int position = attachment.position();
        int limit = attachment.limit();
        int fileSize = limit - position;
        boolean isItEnd = false;
//        byte[] fileReceive = new byte[fileSize];
//        attachment.get(fileReceive,0,fileSize);

        String fileName = "";
        int totalFileSize = -1;
        for (MyFile file : myCurRoom.getFileList())
        {
            if(file.getFileNum() == fileNum)
            {
                isItEnd = file.isItEndOfChunk(fileSize);
                fileName = file.getFileName();
                totalFileSize = file.getFileSize();
                Path path = file.getPath();
                try
                {
                    AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(path,StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    fileChannel.write(attachment, filePosition,null, new CompletionHandler<Integer,Long>()
                    {
                        @Override
                        public void completed(Integer result, Long attachment)
                        {
                            logr.info("[ "+fileSize +" write operation complete]");
                        }

                        @Override
                        public void failed(Throwable exc, Long attachment)
                        {
                        }
                    });
//                    Files.write(path,fileReceive, StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
                break;
            }
        }
        crs.send(reqId,operation,0,0,ByteBuffer.allocate(0),sender);

        if(isItEnd)
        {
            //send broadcast
            for (Client client : myCurRoom.getUserList())
            {
                ByteBuffer infoBuf = ByteBuffer.allocate(100);
                infoBuf.putInt(myCurRoom.getRoomNum());
                infoBuf.put(sender.getUserId().getBytes(StandardCharsets.UTF_8));
                infoBuf.position(20);
                infoBuf.put(getTime().getBytes(StandardCharsets.UTF_8));
                infoBuf.position(32);
                infoBuf.putInt(fileNum);
                infoBuf.put(fileName.getBytes(StandardCharsets.UTF_8));
                infoBuf.position(52);
                infoBuf.putInt(totalFileSize);
                infoBuf.flip();
                synchronized (for_uploadFileProcess)
                {
                    try
                    {
                        crs.send(-1,0,3,0,infoBuf,client);
                        for_uploadFileProcess.wait(100);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    void fileDownloadProcess(int reqId, int operation,int roomNum, String userId, ByteBuffer attachment)
    {
        Client sender = ServerService.getSender(userId);
        Room myCurRoom = sender.getMyCurRoom();

        int fileNum = attachment.getInt();
        int cutSize = attachment.getInt();
        Path path = Paths.get("./temp_db/" + roomNum + "/" + fileNum);
        List<Path> targetFileList = new ArrayList<>();
        try
        {
            targetFileList = Files.list(path).collect(Collectors.toList());
            Path targetFilePath = targetFileList.get(0);
            Path fileName = targetFilePath.getFileName();
            byte[] bytes = Files.readAllBytes(targetFilePath);
            int totalSize = bytes.length;
            int blockCount = totalSize / cutSize;
            int blockLeftover = totalSize % cutSize;

            for(int a = 0; a<=blockCount; a++)
            {
                byte[] small;
                int c = 0;
                if(a == blockCount)
                {
                    small = new byte[blockLeftover];
                    for(int b = a*cutSize; b<a*cutSize+blockLeftover; b++)
                    {
                        small[c] = bytes[b];
                        c++;
                    }
                }
                else
                {
                    small = new byte[cutSize];
                    for(int b = a*cutSize; b<a*cutSize+cutSize; b++)
                    {
                        small[c] = bytes[b];
                        c++;
                    }
                }
                ByteBuffer fileBuf = ByteBuffer.allocate(1000);
                fileBuf.putInt(fileNum);
                fileBuf.put(fileName.toString().getBytes(StandardCharsets.UTF_8));
                fileBuf.position(20);
                fileBuf.put(small);
                fileBuf.flip();
                synchronized (for_uploadFileProcess)
                {
                    try
                    {
                        crs.send(reqId,5,0, 0, fileBuf,sender);
                        for_uploadFileProcess.wait(500);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }


        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    void fileListProcess(int reqId, int operation,int roomNum, String userId, ByteBuffer attachment)
    {
        Client sender = ServerService.getSender(userId);
        Room myCurRoom = sender.getMyCurRoom();

        ByteBuffer buffer = ByteBuffer.allocate(1000);
        List<MyFile> fileList = myCurRoom.getFileList();
        int totalSize = fileList.size();
        buffer.putInt(totalSize);

        for(int i = 0; i<totalSize; i++)
        {
            MyFile file = fileList.get(i);
            int fileNum = file.getFileNum();
            buffer.putInt(fileNum);

            int curPos = buffer.position();
            String fileName = file.getFileName();
            buffer.put(fileName.getBytes(StandardCharsets.UTF_8));
            buffer.position(curPos+16);
            int fileSize = file.getFileSize();
            buffer.putInt(fileSize);
        }

        buffer.flip();
        crs.send(reqId,operation,0,0,buffer,sender);
    }

    void fileDeleteProcess(int reqId, int operation,int roomNum, String userId, ByteBuffer attachment)
    {
        Client sender = ServerService.getSender(userId);
        Room myCurRoom = sender.getMyCurRoom();

        int fileNum = attachment.getInt();
        List<MyFile> fileList = myCurRoom.getFileList();
        String fileName = "";
        int fileSize = 0;
        for (MyFile file : fileList)
        {
            if(file.getFileNum() == fileNum)
            {
                fileName = file.getFileName();
                fileSize = file.getFileSize();
                Map<String, Integer> fileNameCheck = myCurRoom.getFileNameCheck();
                fileNameCheck.remove(file.getFileName());
                Path path = file.getPath();
                try
                {
                    Files.walk(path)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
                fileList.remove(file);
                break;
            }
        }
        crs.send(reqId,operation,0,0,ByteBuffer.allocate(0),sender);

        for (Client client : myCurRoom.getUserList())
        {
            ByteBuffer infoBuf = ByteBuffer.allocate(100);
            infoBuf.putInt(myCurRoom.getRoomNum());
            infoBuf.put(sender.getUserId().getBytes(StandardCharsets.UTF_8));
            infoBuf.position(20);
            infoBuf.put(getTime().getBytes(StandardCharsets.UTF_8));
            infoBuf.position(32);
            infoBuf.putInt(fileNum);
            infoBuf.put(fileName.getBytes(StandardCharsets.UTF_8));
            infoBuf.position(52);
            infoBuf.putInt(fileSize);
            infoBuf.flip();
            synchronized (for_deleteFileProcess)
            {
                try
                {
                    crs.send(-1,0,4,0,infoBuf,client);
                    for_deleteFileProcess.wait(100);
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    public void closeBroadcast(Client client)
    {
        if(client.getState() == 0)
        {
            return;
        }

        if(client.getMyCurRoom() != null)
        {
            Map<Client, Integer> userStates = client.getMyCurRoom().getUserStates();
            userStates.put(client,3);
            for (Client client1 : client.getMyCurRoom().getUserList())
            {
                ByteBuffer allocate = ByteBuffer.allocate(100);
                allocate.putInt(client.getMyCurRoom().getRoomNum());
                allocate.put(client.getUserId().getBytes(StandardCharsets.UTF_8));
                allocate.position(20);
                allocate.put(getTime().getBytes(StandardCharsets.UTF_8));
                allocate.position(32);
                allocate.flip();
                crs.send(-1,0,6,0,allocate,client1);
            }
        }
        client.setState(2);
        try
        {
            client.getSocketChannel().close();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
