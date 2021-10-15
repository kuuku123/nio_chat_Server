package domain;

import java.nio.file.Path;
import java.util.*;

public class Room
{
    private int roomNum;
    private String roomName;
    private List<Client> userList = new Vector<>();
    private List<Text> chatLog = new ArrayList<>();
    private List<MyFile> fileList = new Vector<>();
    private Map<Client,Integer> userStates = new HashMap<>();
    private int fileNum = 0;
    private Map<String,Integer> fileNameCheck = new HashMap<>();

    private int ip0;
    private int ip1;
    private int ip2;
    private int ip3;
    private int port;

    public Room(int roomNum)
    {
        this.roomNum = roomNum;
    }

    public String getRoomName()
    {
        return roomName;
    }

    public int getRoomNum()
    {
        return roomNum;
    }

    public List<Client> getUserList()
    {
        return userList;
    }

    public List<Text> getChatLog()
    {
        return chatLog;
    }

    public int getPort()
    {
        return port;
    }

    public List<Integer> getIpAddress()
    {
        List<Integer> IpAddress = new ArrayList<>();
        IpAddress.add(ip0);
        IpAddress.add(ip1);
        IpAddress.add(ip2);
        IpAddress.add(ip3);
        return IpAddress;
    }

    public Map<Client, Integer> getUserStates()
    {
        return userStates;
    }

    public int getFileNum()
    {
        return fileNum;
    }

    public List<MyFile> getFileList()
    {
        return fileList;
    }

    public Map<String, Integer> getFileNameCheck()
    {
        return fileNameCheck;
    }

    public String checkFileNameCheck(String fileName)
    {
        if (fileNameCheck.getOrDefault(fileName,-1) == -1)
        {
            fileNameCheck.put(fileName,0);
            return fileName;
        }
        else
        {
            Integer integer = fileNameCheck.get(fileName);
            integer++;
            String[] split = fileName.split("\\.");
            String checkedFileName = split[0] + "("+integer+")." + split[1];
            fileNameCheck.put(fileName,integer);
            return checkedFileName;
        }
    }

    public void setRoomName(String roomName)
    {
        this.roomName = roomName;
    }

    public void incrementFileNum()
    {
        fileNum++;
    }

    public void removeUser(Client user)
    {
        userList.remove(user);
        for (Text text : chatLog)
        {
            Map<String, Integer> readCheck = text.readCheck;
            readCheck.remove(user.getUserId());
        }
    }

    public void createAndAddText(int textId, String sender, String text,Room room)
    {
        Text newText = new Text(textId, sender, text,room);
        this.chatLog.add(newText);
    }

    public int getNewTextNotRoomRead()
    {
        Text text = chatLog.get(this.chatLog.size() - 1);
        return text.notRoomRead;
    }

    public int getUserNotRoomRead(String userId)
    {
        int notRead = 0;
        for (Text text : chatLog)
        {
            Integer check = text.readCheck.getOrDefault(userId, -1);
            if (check == 0) notRead++;
        }
        return notRead;
    }

    public List<Text> getUserNotRoomReadTextList(String userId)
    {
        List<Text> notReadList = new ArrayList<>();
        for (Text text : chatLog)
        {
            Map<String, Integer> readCheck = text.readCheck;
            if (readCheck.getOrDefault(userId, -1) == 0)
            {
                text.notRoomRead--;
                readCheck.put(userId, 1);
                notReadList.add(text);
            }
        }
        return notReadList;
    }

    public MyFile createNewFile(int fileNum, String fileName, Path path, int fileSize)
    {
        MyFile file = new MyFile(fileNum, fileName, path,fileSize);
        fileList.add(file);

        return file;
    }
}
