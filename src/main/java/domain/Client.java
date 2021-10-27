package domain;

import adminserver.ServerService;
import domain.Room;
import util.MyLog;
import util.OperationEnum;
import util.SendPackage;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;

public class Client
{
    private SocketChannel socketChannel;
    private String userId = "not set yet";
    private List<Room> myRoomList = new Vector<>();
    private Room myCurRoom;
    private int State;
    private List<ByteBuffer> notYetReadBuffers = new Vector<>();

    public Client(SocketChannel socketChannel)
    {
        this.socketChannel = socketChannel;
        try
        {
            socketChannel.configureBlocking(false);
            SelectionKey selectionKey = socketChannel.register(ServerService.selector, SelectionKey.OP_READ);
            SendPackage sendPackage = new SendPackage(this, 0, 0, 0, 0, ByteBuffer.allocate(0));
            selectionKey.attach(sendPackage);
            ServerService.selector.wakeup();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public SocketChannel getSocketChannel()
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

    public void setSocketChannel(SocketChannel socketChannel)
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
