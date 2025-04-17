package kr.jclab.netty.channel.iocp;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.ObjectUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * typedef struct _netty_iocp_native_overlapped {
 *     c overlapped;
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
    private AtomicInteger refCount = new AtomicInteger(1);

    private long internal;
    private long internalHigh;

    public NativeOverlapped(int bufferSize) throws Errors.NativeIoException {
        this(null, bufferSize);
    }


    public NativeOverlapped(AbstractWinHandle handle, int bufferSize) throws Errors.NativeIoException {
        this.bufferSize = bufferSize;

        memory = Buffer.allocateDirectWithNativeOrder(SIZE_OF_HEADER + bufferSize);
        memoryAddress = Buffer.memoryAddress(memory);
        event = Native.createEvent(true, false);
        Native.overlappedInitialize0(memoryAddress, event.longValue(), (handle != null) ? handle.longValue() : 0, bufferSize);

        MemoryLeakDetector.put(memoryAddress, this);
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

    public void refInc() {
        refCount.incrementAndGet();
    }

    public void refDec() {
        int newRefCount = refCount.decrementAndGet();
        ObjectUtil.checkPositiveOrZero(newRefCount, "newRefCount");
        if (newRefCount == 0) {
            free();
        }
    }

    public int refCount() {
        return refCount.get();
    }

    private void free() {
        if (memoryAddress != 0) {
            // remove magic
            memory.position(0);
            memory.limit(memory.capacity());
            memory.putInt(0);

            MemoryLeakDetector.remove(memoryAddress);
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

    public void readData(ByteBuf dest, int length) {
        memory.limit(SIZE_OF_HEADER + length);
        memory.position(SIZE_OF_HEADER);
        dest.writeBytes(memory);
    }

    public int writeData(ByteBuf byteBuf) {
        int available = Math.min(byteBuf.readableBytes(), bufferSize);
        memory.limit(SIZE_OF_HEADER + available);
        memory.position(SIZE_OF_HEADER);
        byteBuf.readBytes(memory);
        return available;
    }
}
