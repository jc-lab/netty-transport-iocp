package kr.jclab.netty.channel.iocp;

public class OverlappedEntry {
    private long completionKey = 0;
    private long overlappedPointer = 0;
    private int numberOfBytesTransferred = 0;

    // overlapped
    private boolean overlappedValid = false;
    private long    fileHandle = 0;
    private long    eventHandle = 0;
    private int     bufferSize = 0;

    public long getCompletionKey() {
        return completionKey;
    }

    public long getOverlappedPointer() {
        return overlappedPointer;
    }

    public int getNumberOfBytesTransferred() {
        return numberOfBytesTransferred;
    }

    public boolean isOverlappedValid() {
        return overlappedValid;
    }

    public long getFileHandle() {
        return fileHandle;
    }

    public long getEventHandle() {
        return eventHandle;
    }

    public int getBufferSize() {
        return bufferSize;
    }
}
