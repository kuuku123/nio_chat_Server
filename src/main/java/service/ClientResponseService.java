package service;

import domain.Client;
import util.MyLog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.logging.Logger;

public class ClientResponseService
{
    private final Logger logr = MyLog.getLogr();
    private final Object for_sendTextProcess;
    private final Object for_enterRoomProcess;
    private final Object for_inviteRoomProcess;
    private final Object for_quitRoomProcess;
    private final Object for_uploadFileProcess;
    private final Object for_deleteFileProcess;

    public ClientResponseService(Object for_sendTextProcess, Object for_enterRoomProcess, Object for_inviteRoomProcess, Object for_quitRoomProcess, Object for_uploadFileProcess, Object for_deleteFileProcess)
    {
        this.for_sendTextProcess = for_sendTextProcess;
        this.for_enterRoomProcess = for_enterRoomProcess;
        this.for_inviteRoomProcess = for_inviteRoomProcess;
        this.for_quitRoomProcess = for_quitRoomProcess;
        this.for_uploadFileProcess = for_uploadFileProcess;
        this.for_deleteFileProcess = for_deleteFileProcess;
    }

    public void send(int reqId, int operation, int broadcastNum, int result, ByteBuffer leftover, Client client)
    {
        ByteBuffer writeBuffer = ByteBuffer.allocate(10000);
        if (reqId != -1)
        {
            writeBuffer.putInt(reqId);
            writeBuffer.putInt(operation);
            writeBuffer.putInt(result);
            writeBuffer.put(leftover);
        } else if (reqId == -1)
        {
            writeBuffer.putInt(reqId);
            writeBuffer.putInt(broadcastNum);
            writeBuffer.put(leftover);
        }
        writeBuffer.flip();
        client.getSocketChannel().write(writeBuffer, null, new CompletionHandler<Integer, Object>()
        {
            @Override
            public void completed(Integer result, Object attachment)
            {
                synchronized (for_inviteRoomProcess)
                {
                    for_inviteRoomProcess.notify();
                }
                synchronized (for_enterRoomProcess)
                {
                    for_enterRoomProcess.notify();
                }
                synchronized (for_sendTextProcess)
                {
                    for_sendTextProcess.notify();
                }
                synchronized (for_quitRoomProcess)
                {
                    for_quitRoomProcess.notify();
                }
            }

            @Override
            public void failed(Throwable exc, Object attachment)
            {

                if (reqId == -1 && broadcastNum == 2)
                {
                    leftover.flip();
                    client.getNotYetReadBuffers().add(leftover);
                }
                synchronized (for_inviteRoomProcess)
                {
                    for_inviteRoomProcess.notify();
                }
                synchronized (for_enterRoomProcess)
                {
                    for_enterRoomProcess.notify();
                }
                synchronized (for_sendTextProcess)
                {
                    for_sendTextProcess.notify();
                }
                synchronized (for_quitRoomProcess)
                {
                    for_quitRoomProcess.notify();
                }
                try
                {
                    logr.severe("[send fail" + client.getSocketChannel().getRemoteAddress() + " : " + Thread.currentThread().getName() + "]");
                    client.getSocketChannel().close();
                } catch (IOException e)
                {
                }
            }
        });
    }
}
