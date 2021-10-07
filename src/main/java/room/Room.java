package room;

import user.Client;

import java.nio.file.Path;
import java.util.*;

public class Room
{
    private int roomNum;
    private String roomName;
    private List<Client> userList = new Vector<>();
    private List<Text> chatLog = new ArrayList<>();
    private List<File> fileList = new Vector<>();
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

    public List<File> getFileList()
    {
        return fileList;
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
            String checkedFileName = split[0] + "("+integer+")" + split[1];
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

    public void createAndAddText(int textId, String sender, String text)
    {
        Text newText = new Text(textId, sender, text);
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

    public File createNewFile(int fileNum, String fileName, Path path)
    {
        File file = new File(fileNum, fileName, path);
        fileList.add(file);

        return file;
    }


    public class Text
    {
        int textId;
        String sender;
        String text;
        Map<String, Integer> readCheck = new HashMap<>();
        int notRoomRead = 0;

        public Text(int textId, String sender, String text)
        {
            this.textId = textId;
            this.sender = sender;
            this.text = text;
            for (Client user : Room.this.userList)
            {
                if (user.getSocketChannel().isOpen() && user.getMyCurRoom() != null && userStates.getOrDefault(user,-1) == 1 && user.getState() == 1) readCheck.put(user.getUserId(), 1);
                else
                {
                    readCheck.put(user.getUserId(), 0);
                    notRoomRead++;
                }
            }
        }

        public int getTextId()
        {
            return textId;
        }

        public String getSender()
        {
            return sender;
        }

        public String getText()
        {
            return text;
        }

        public Map<String, Integer> getReadCheck()
        {
            return readCheck;
        }

        public int getNotRoomRead()
        {
            return notRoomRead;
        }
    }

    public class File
    {
        private int fileNum;
        private String fileName;
        private Path path;

        public File(int fileNum, String fileName, Path path)
        {
            this.fileNum = fileNum;
            this.fileName = fileName;
            this.path = path;
        }

        public int getFileNum()
        {
            return fileNum;
        }

        public String getFileName()
        {
            return fileName;
        }

        public Path getPath()
        {
            return path;
        }
    }

}
