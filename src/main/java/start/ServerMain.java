package start;

import adminserver.ServerService;
import service.ClientRequestService;
import service.ClientResponseService;
import service.ProcessService;

import java.nio.ByteBuffer;

public class ServerMain
{
    public static void main(String[] args)
    {
        ProcessService processService = new ProcessService();
        ClientRequestService clientRequestService = new ClientRequestService(processService);
        ServerService serverExample = new ServerService(clientRequestService);
        serverExample.startServer();
    }
}
