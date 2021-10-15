package domain;

import java.util.HashMap;
import java.util.Map;

public class Text
{
    int textId;
    String sender;
    String text;
    Map<String, Integer> readCheck = new HashMap<>();
    int notRoomRead = 0;
    Room room;

    public Text(int textId, String sender, String text,Room room)
    {
        this.textId = textId;
        this.sender = sender;
        this.text = text;
        this.room = room;
        for (Client user : room.getUserList())
        {
            if (user.getSocketChannel().isOpen() && user.getMyCurRoom() != null && room.getUserStates().getOrDefault(user,-1) == 1 && user.getState() == 1) readCheck.put(user.getUserId(), 1);
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
