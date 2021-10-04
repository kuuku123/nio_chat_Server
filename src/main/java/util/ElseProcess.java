package util;

public class ElseProcess
{
//    public static byte[] intToByte(int value)
//    {
//        byte[] bytes = new byte[4];
//        bytes[0] = (byte) ((value & 0xFF000000) >> 24);
//        bytes[1] = (byte) ((value & 0x00FF0000) >> 16);
//        bytes[2] = (byte) ((value & 0x0000FF00) >> 8);
//        bytes[3] = (byte) (value & 0x000000FF);
//
//        return bytes;
//
//    }
//
//    public static int byteToInt(byte[] src)
//    {
//
//        int newValue = 0;
//
//        newValue |= (((int) src[0]) << 24) & 0xFF000000;
//        newValue |= (((int) src[1]) << 16) & 0xFF0000;
//        newValue |= (((int) src[2]) << 8) & 0xFF00;
//        newValue |= (((int) src[3])) & 0xFF;
//
//
//        return newValue;
//    }

    public static byte[] removeZero(byte[] reqUserId)
    {
        int count = 0;
        for (byte b : reqUserId)
        {
            if (b == (byte) 0) count++;
        }
        int left = reqUserId.length - count;
        byte[] n = new byte[left];
        for (int i = 0; i < left; i++)
        {
            n[i] = reqUserId[i];
        }
        return n;
    }

}
