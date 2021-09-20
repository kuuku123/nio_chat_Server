import util.Operation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Server
{
    ExecutorService executorService;
    ServerSocketChannel serverSocketChannel;
    List<User> connections = new Vector<>();


    void startServer()
    {
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        try
        {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(true);
            serverSocketChannel.bind(new InetSocketAddress(5001));
        }
        catch(Exception e)
        {
            if(serverSocketChannel.isOpen()) stopServer();
            return;
        }

        Runnable runnable = new Runnable()
        {
            @Override
            public void run()
            {
                System.out.println("[서버 시작]");
                while(true)
                {
                    try
                    {
                        SocketChannel socketChannel = serverSocketChannel.accept();
                        String message ="[연결 수락:" + socketChannel.getRemoteAddress() + ": "+Thread.currentThread().getName()+ "]";
                        System.out.println(message);

                        User user = new User(socketChannel);
                        connections.add(user);
                        System.out.println("[연결 개수: " + connections.size() + "]");
                    }
                    catch(Exception e)
                    {
                        if(serverSocketChannel.isOpen()) stopServer();
                        break;
                    }
                }
            }
        };
        executorService.submit(runnable);
    }
    void stopServer()
    {
        try
        {
            Iterator<User> iterator = connections.iterator();
            while(iterator.hasNext())
            {
                User clientHandler = iterator.next();
                clientHandler.socketChannel.close();
                iterator.remove();
            }
            if(serverSocketChannel!=null && serverSocketChannel.isOpen()) serverSocketChannel.close();
            if(executorService!=null && !executorService.isShutdown()) executorService.shutdown();
        }
        catch(Exception e){}
    }

    class User
    {
        SocketChannel socketChannel;
        String userId = "not set yet";
        List<Room> rooms = new Vector<>();

        User(SocketChannel socketChannel)
        {
            this.socketChannel = socketChannel;
            receive();
        }
        void receive()
        {
            Runnable runnable = () ->
            {
                while(true)
                {
                    try
                    {
                        ByteBuffer byteBuffer = ByteBuffer.allocate((int) Math.pow(2,20));
                        int readByteCount = socketChannel.read(byteBuffer);
                        if(readByteCount == -1) throw new IOException();

                        String message = "[요청 처리: " + socketChannel.getRemoteAddress() + ": "+ Thread.currentThread().getName() + "]";
                        System.out.println(message);
                        byteBuffer.flip();
                        int operation = byteBuffer.get(0);
                        byteBuffer.position(1);
                        Charset charset = Charset.forName("UTF-8");
                        String data = charset.decode(byteBuffer).toString();
                        processOp(operation, data);

//                        for (User client : connections)
//                        {
//                                if(client == ClientHandler.this)
//                                {
//                                    continue;
//                                }
//                            client.send(output);
//                        }
                    }
                    catch(Exception e)
                    {
                        try
                        {
                            connections.remove(User.this);
                            String message = "[클라이언트 통신 안됨: " + socketChannel.getRemoteAddress() + ": "+ Thread.currentThread().getName() + "]";
                            System.out.println(message);
                            socketChannel.close();
                        }
                        catch(IOException e2){}
                        break;
                    }
                }
            };
            executorService.submit(runnable);
        }
        void send(String data)
        {
            Runnable runnable = new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        Charset charset = Charset.forName("UTF-8");
                        ByteBuffer byteBuffer = charset.encode(data);
                        socketChannel.write(byteBuffer);
                    }
                    catch(Exception e)
                    {
                        try
                        {
                            String message = "[클라이언트 통신 안됨: " + socketChannel.getRemoteAddress() + ": " + Thread.currentThread().getName() + "]";
                            System.out.println(message);
                            connections.remove(User.this);
                            socketChannel.close();
                        }
                        catch(IOException e2){}
                    }
                }
            };
            executorService.submit(runnable);
        }

        void processOp(int operation,String data)
        {
            Operation op = Operation.fromInteger(operation);
            switch (op)
            {
                case login:
                    loginProcess(data);
                    System.out.println("login process completed");
                    return;
                case logout:
                    logoutProcess(data);
                    System.out.println("logout process completed");
                    return;
                case sendText:
                case fileUpload:
                case fileList:
                case fileDownload:
                case fileDelete:
                case createRoom:
                case quitRoom:
                case inviteRoom:
                case requestQuitRoom:
                case roomUserList:
            }
        }


        private void loginProcess(String name)
        {
            if (name != null)
            {
                for (User user : connections)
                {
                    if (user.userId.equals(name))
                    {
                        send("duplicate nickname");
                        User user1 = connections.get(connections.size() - 1);
                        try
                        {
                            user1.socketChannel.close();
                        } catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                        connections.remove(user1);
                        System.out.println("이름 중복이니 연결제거");
                        System.out.println("[연결 개수: " + connections.size() + "]");
                        return;
                    }
                }

                User user = connections.get(connections.size() - 1);
                user.userId = name;
                send("login complete");

            }
            else send("login failed");
        }

        private void logoutProcess(String name)
        {
            for (User user : connections)
            {
                if (user.userId.equals(name))
                {
                    try
                    {
                        user.socketChannel.close();
                        break;
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    class Room
    {
        List<User> users = new Vector<>();
    }




    public static void main(String[] args)
    {
        Server server = new Server();
        server.startServer();
    }
}
