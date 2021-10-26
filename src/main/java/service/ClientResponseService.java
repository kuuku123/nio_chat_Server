package service;

import adminserver.ServerService;
import domain.Client;
import util.MyLog;
import util.SendPackage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.logging.Logger;

import static service.ProcessService.*;

public class ClientResponseService
{
    private final Logger logr = MyLog.getLogr();


    public ClientResponseService()
    {
    }

    public void send(SelectionKey selectionKey)
    {
        SendPackage sendPackage = (SendPackage) selectionKey.attachment();
        Client client = sendPackage.getClient();
        int reqId = sendPackage.getReqId();
        int operation = sendPackage.getOperation();
        int result = sendPackage.getResult();
        ByteBuffer leftover = sendPackage.getLeftover();
        int broadcastNum = sendPackage.getBroadcastNum();
        ByteBuffer writeBuffer = ByteBuffer.allocate(100000);
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
        try
        {
            client.getSocketChannel().write(writeBuffer);
            selectionKey.interestOps(SelectionKey.OP_READ);
            ServerService.selector.wakeup();
        } catch (IOException e)
        {
            if (reqId == -1 && broadcastNum == 2)
            {
                leftover.flip();
                client.getNotYetReadBuffers().add(leftover);
            }
            logr.info("send failed, buffer saved in server for later use");
            return;
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
        synchronized (for_uploadFileProcess)
        {
            for_uploadFileProcess.notify();
        }
    }
}
