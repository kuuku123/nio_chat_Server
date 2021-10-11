package domain;

import domain.Room;
import util.MyLog;
import util.OperationEnum;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;

public class Client
{
    private AsynchronousSocketChannel socketChannel;
    private String userId = "not set yet";
    private List<Room> myRoomList = new Vector<>();
    private Room myCurRoom;
    private int State;
    private List<ByteBuffer> notYetReadBuffers = new Vector<>();

    public Client(AsynchronousSocketChannel socketChannel)
    {
        this.socketChannel = socketChannel;
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

    public int getState()
    {
        return State;
    }

    public List<ByteBuffer> getNotYetReadBuffers()
    {
        return notYetReadBuffers;
    }

    public void setSocketChannel(AsynchronousSocketChannel socketChannel)
    {
        this.socketChannel = socketChannel;
    }

    public void setUserId(String userId)
    {
        this.userId = userId;
    }

    public void setMyCurRoom(Room myCurRoom)
    {
        this.myCurRoom = myCurRoom;
    }

    public void setState(int state)
    {
        State = state;
    }

}
