package remoteroomserver;

import adminserver.ServerService;
import util.MyLog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.logging.Logger;

public class RemoteRoomServer
{
    private Logger logr;
    ByteBuffer remoteReadBuf = ByteBuffer.allocate(10000);
    AsynchronousSocketChannel remoteRoomServerChannel;
    int ip0;
    int ip1;
    int ip2;
    int ip3;
    int port;

    public RemoteRoomServer(AsynchronousSocketChannel remoteRoomServerChannel, int port)
    {
        this.remoteRoomServerChannel = remoteRoomServerChannel;
        this.port = port;
        this.logr = MyLog.getLogr();
        receive();
    }

    void receive()
    {
        remoteRoomServerChannel.read(remoteReadBuf, remoteReadBuf, new CompletionHandler<Integer, ByteBuffer>()
        {
            @Override
            public void completed(Integer result, ByteBuffer attachment)
            {
                remoteReadBuf = ByteBuffer.allocate(10000);
                remoteReadBuf.clear();
                remoteRoomServerChannel.read(remoteReadBuf, remoteReadBuf, this);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment)
            {
                try
                {
                    String portInfo = remoteRoomServerChannel.getRemoteAddress().toString();
                    closeRemoteSever(portInfo);
                    logr.severe("[Remote RoomServer receive fail" + remoteRoomServerChannel.getRemoteAddress() + " : " + Thread.currentThread().getName() + "]");
                } catch (IOException e)
                {
                }
            }

            void closeRemoteSever(String portInfo) throws IOException
            {
                String[] portInfos = portInfo.split(":");
                int port = Integer.parseInt(portInfos[1]);
                if (port == 10001 || port == 10002)
                {
                    for (int i = 0; i < ServerService.remoteRoomSeverList.size(); i++)
                    {
                        RemoteRoomServer remoteRoomServer = ServerService.remoteRoomSeverList.get(i);
                        if (remoteRoomServer.port == port)
                        {
                            remoteRoomServer.remoteRoomServerChannel.close();
                            ServerService.remoteRoomSeverList.remove(remoteRoomServer);
                            logr.info("[remote room server port : " + port + " removed]");
                            logr.info("[remote RoomServer 연결 개수: " + ServerService.remoteRoomSeverList.size() + "]");
                            break;
                        }
                    }
                }

            }

        });
    }
}