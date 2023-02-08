package kr.jclab.netty.channel.iocp;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * typedef struct _netty_iocp_native_overlapped {
 *     OVERLAPPED overlapped;
 *     HANDLE     fileHandle;
 *     DWORD      bufferSize;
 * } netty_iocp_native_overlapped_t;
 */
public class NativeOverlapped {
    public static final int SIZE_OF_HEADER = NativeStaticallyReferencedJniMethods.sizeOfNativeOverlappedStruct();

    private final int bufferSize;

    private final ByteBuffer memory;
    private long memoryAddress;

    private final WinHandle event;


    public NativeOverlapped(int bufferSize) throws Errors.NativeIoException {
        this(null, bufferSize);
    }


    public NativeOverlapped(AbstractWinHandle handle, int bufferSize) throws Errors.NativeIoException {
        this.bufferSize = bufferSize;

        memory = Buffer.allocateDirectWithNativeOrder(SIZE_OF_HEADER + bufferSize);
        memoryAddress = Buffer.memoryAddress(memory);
        event = Native.createEvent(true, false);
        Native.overlappedInitialize0(memoryAddress, event.longValue(), (handle != null) ? handle.longValue() : 0, bufferSize);
    }

    public void initialize(AbstractWinHandle handle) {
        Native.overlappedInitialize0(memoryAddress, event.longValue(), handle.longValue(), bufferSize);
    }

    public long memoryAddress() {
        return memoryAddress;
    }

    public ByteBuffer memory() {
        return memory;
    }

    public void free() {
        if (memoryAddress != 0) {
            Buffer.free(memory);
            memoryAddress = 0;
        }
        if (event.isOpen()) {
            try {
                event.close();
            } catch (IOException e) {
            }
        }
    }

    public ByteBuffer sliceData(int length) {
        return memory.slice(SIZE_OF_HEADER, length);
    }

    public int writeData(ByteBuf byteBuf) {
        int available = Math.min(byteBuf.readableBytes(), bufferSize);
        if (byteBuf.hasArray()) {
            memory.put(SIZE_OF_HEADER, byteBuf.array(), byteBuf.arrayOffset(), available);
        } else {
            byte[] temp = new byte[available];
            byteBuf.readBytes(temp);
            memory.put(SIZE_OF_HEADER, temp);
        }
        return available;
    }
}
