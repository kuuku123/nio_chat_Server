package service;

import adminserver.ServerService;
import domain.Client;
import util.MyLog;
import util.SendPackage;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.channels.SelectionKey;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

import static util.ElseProcess.getTime;

public class ClientRequestService
{
    private final Logger logr = MyLog.getLogr();
    private final ProcessService processService;

    public ClientRequestService(ProcessService processService)
    {
        this.processService = processService;
    }

    public void receive(SelectionKey selectionKey,ByteBuffer readBuffer, int byteCount)
    {
        SendPackage sendPackage = (SendPackage) selectionKey.attachment();
        Client client = sendPackage.getClient();
        try
        {
            logr.info("[요청 처리: " + client.getSocketChannel().getRemoteAddress() + ": " + Thread.currentThread().getName() + "]");
            processService.processOp(readBuffer);
            readBuffer.clear();
        }
        catch(Exception e)
        {
            processService.closeBroadcast(selectionKey);
        }
    }
}
