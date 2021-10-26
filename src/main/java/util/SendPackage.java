package util;

import domain.Client;

import java.nio.ByteBuffer;

public class SendPackage
{
    Client client;
    int reqId;
    int operation;
    int broadcastNum;
    int result;
    ByteBuffer leftover;

    public SendPackage(Client client, int reqId, int operation, int broadcastNum, int result, ByteBuffer leftover)
    {
        this.client = client;
        this.reqId = reqId;
        this.operation = operation;
        this.broadcastNum = broadcastNum;
        this.result = result;
        this.leftover = leftover;
    }

    public Client getClient()
    {
        return client;
    }

    public void setClient(Client client)
    {
        this.client = client;
    }

    public int getReqId()
    {
        return reqId;
    }

    public void setReqId(int reqId)
    {
        this.reqId = reqId;
    }

    public int getOperation()
    {
        return operation;
    }

    public void setOperation(int operation)
    {
        this.operation = operation;
    }

    public int getBroadcastNum()
    {
        return broadcastNum;
    }

    public void setBroadcastNum(int broadcastNum)
    {
        this.broadcastNum = broadcastNum;
    }

    public int getResult()
    {
        return result;
    }

    public void setResult(int result)
    {
        this.result = result;
    }

    public ByteBuffer getLeftover()
    {
        return leftover;
    }

    public void setLeftover(ByteBuffer leftover)
    {
        this.leftover = leftover;
    }
}
