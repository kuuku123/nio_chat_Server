package util;

public enum OperationEnum
{
    login , logout, sendText,fileUpload, fileList,fileDownload,fileDelete, createRoom,
    quitRoom,inviteRoom, roomUserList , roomList, enterRoom, enrollFile;

    public static OperationEnum fromInteger(int x)
    {
        switch (x)
        {
            case 0:
                return login;
            case 1:
                return logout;
            case 2:
                return sendText;
            case 3:
                return fileUpload;
            case 4:
                return fileList;
            case 5:
                return fileDownload;
            case 6:
                return fileDelete;
            case 7:
                return createRoom;
            case 8:
                return quitRoom;
            case 9:
                return inviteRoom;
            case 10:
                return roomUserList;
            case 11:
                return roomList;
            case 12:
                return enterRoom;
            case 13:
                return enrollFile;
        }
        return null;
    }

}
