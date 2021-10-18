package service;

import domain.Client;
import util.MyLog;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
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

    public void receive(Client client)
    {
        ByteBuffer readBuffer = ByteBuffer.allocate(100000);
        client.getSocketChannel().read(readBuffer, readBuffer, new CompletionHandler<Integer, ByteBuffer>()
        {
            @Override
            public void completed(Integer result, ByteBuffer attachment)
            {
                if(result == -1)
                {
                    processService.closeBroadcast(client);
                    return;
                }
                    try
                    {
                        logr.info("[요청 처리: " + client.getSocketChannel().getRemoteAddress() + ": " + Thread.currentThread().getName() + "]");
                        processService.processOp(attachment);
                        ByteBuffer readBuffer = ByteBuffer.allocate(100000);
                        if (client.getSocketChannel() != null) client.getSocketChannel().read(readBuffer, readBuffer, this);
                    }
                    catch (IOException e)
                    {
                    }
                    catch (BufferUnderflowException e)
                    {
                        logr.info("receive 하는중에 BufferUnderflow 발생함");
                    }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment)
            {
                try
                {
                    logr.severe("[receive fail" + client.getSocketChannel().getRemoteAddress() + " : " + Thread.currentThread().getName() + "]");
                    processService.closeBroadcast(client);
                } catch (IOException e)
                {
                }
            }
        });

    }
}
