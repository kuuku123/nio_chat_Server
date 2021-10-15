package domain;

import java.nio.file.Path;

public class MyFile
{
    private int fileNum;
    private String fileName;
    private Path path;
    private int fileSize;
    private int uploadCheck;

    public MyFile(int fileNum, String fileName, Path path, int fileSize)
    {
        this.fileNum = fileNum;
        this.fileName = fileName;
        this.path = path;
        this.fileSize = fileSize;
        this.uploadCheck = fileSize;
    }

    public int getFileNum()
    {
        return fileNum;
    }

    public String getFileName()
    {
        return fileName;
    }

    public Path getPath()
    {
        return path;
    }

    public int getFileSize()
    {
        return fileSize;
    }

    public boolean isItEndOfChunk(int chunk)
    {
        this.uploadCheck -= chunk;
        if(this.uploadCheck == 0) return true;
        else return false;
    }
}
