package start;

import adminserver.ServerService;
import service.ClientRequestService;
import service.ClientResponseService;
import service.ProcessService;

public class ServerMain
{
    public static void main(String[] args)
    {
        ProcessService processService = new ProcessService();
        ClientRequestService clientRequestService = new ClientRequestService(processService);
        ClientResponseService clientResponseService = new ClientResponseService();
        ServerService serverExample = new ServerService(clientRequestService,clientResponseService, processService);
        serverExample.startServer();
    }
}
