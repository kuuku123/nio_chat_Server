package service;

import domain.Client;
import util.MyLog;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.Map;
import java.util.logging.Logger;

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
        ByteBuffer readBuffer = ByteBuffer.allocate(10000);
        client.getSocketChannel().read(readBuffer, readBuffer, new CompletionHandler<Integer, ByteBuffer>()
        {
            @Override
            public void completed(Integer result, ByteBuffer attachment)
            {
                    try
                    {
                        logr.info("[요청 처리: " + client.getSocketChannel().getRemoteAddress() + ": " + Thread.currentThread().getName() + "]");
                        processService.processOp(attachment);
                        ByteBuffer readBuffer = ByteBuffer.allocate(10000);
                        if (client.getSocketChannel() != null) client.getSocketChannel().read(readBuffer, readBuffer, this);
                    } catch (IOException e)
                    {
                    } catch (BufferUnderflowException e)
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
                    if(client.getMyCurRoom() != null)
                    {
                        Map<Client, Integer> userStates = client.getMyCurRoom().getUserStates();
                        userStates.put(client,0);
                    }
                    client.setState(2);
                    client.getSocketChannel().close();
                } catch (IOException e)
                {
                }
            }
        });

    }
}
