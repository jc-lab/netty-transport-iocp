package kr.jclab.netty.channel.iocp;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;
import static kr.jclab.netty.channel.iocp.Errors.newIOException;

/**
 * Native {@link AbstractWinHandle} implementation which allows to wrap an Handle and provide a
 * {@link AbstractWinHandle} for it.
 */
public abstract class AbstractWinHandle {
    private static final AtomicIntegerFieldUpdater<AbstractWinHandle> stateUpdater =
            AtomicIntegerFieldUpdater.newUpdater(AbstractWinHandle.class, "state");

    private static final int STATE_CLOSED_MASK = 1;
    private static final int STATE_INPUT_SHUTDOWN_MASK = 1 << 1;
    private static final int STATE_OUTPUT_SHUTDOWN_MASK = 1 << 2;
    private static final int STATE_ALL_MASK = STATE_CLOSED_MASK |
            STATE_INPUT_SHUTDOWN_MASK |
            STATE_OUTPUT_SHUTDOWN_MASK;

    /**
     * Bit map = [Output Shutdown | Input Shutdown | Closed]
     */
    volatile int state;
    final long handle;

    public AbstractWinHandle(long handle) {
        checkPositiveOrZero(handle, "handle");
        this.handle = handle;
    }

    /**
     * Return the long value of the WinHandle.
     */
    public final long longValue() {
        return handle;
    }

    protected boolean markClosed() {
        for (;;) {
            int state = this.state;
            if (isClosed(state)) {
                return false;
            }
            // Once a close operation happens, the channel is considered shutdown.
            if (casState(state, state | STATE_ALL_MASK)) {
                return true;
            }
        }
    }

    /**
     * Close the file descriptor.
     */
    public void close() throws IOException {
        if (markClosed()) {
            int res = closeImpl(handle);
            if (res < 0) {
                throw newIOException("close", res);
            }
        }
    }

    /**
     * Returns {@code true} if the file descriptor is open.
     */
    public boolean isOpen() {
        return !isClosed(state);
    }
//
//    public final int write(ByteBuffer buf, int pos, int limit) throws IOException {
//        int res = write(fd, buf, pos, limit);
//        if (res >= 0) {
//            return res;
//        }
//        return ioResult("write", res);
//    }
//
//    public final int writeAddress(long address, int pos, int limit) throws IOException {
//        int res = writeAddress(fd, address, pos, limit);
//        if (res >= 0) {
//            return res;
//        }
//        return ioResult("writeAddress", res);
//    }
//
//    public final long writev(ByteBuffer[] buffers, int offset, int length, long maxBytesToWrite) throws IOException {
//        long res = writev(fd, buffers, offset, min(IOV_MAX, length), maxBytesToWrite);
//        if (res >= 0) {
//            return res;
//        }
//        return ioResult("writev", (int) res);
//    }
//
//    public final long writevAddresses(long memoryAddress, int length) throws IOException {
//        long res = writevAddresses(fd, memoryAddress, length);
//        if (res >= 0) {
//            return res;
//        }
//        return ioResult("writevAddresses", (int) res);
//    }
//
//    public final int read(ByteBuffer buf, int pos, int limit) throws IOException {
//        int res = read(fd, buf, pos, limit);
//        if (res > 0) {
//            return res;
//        }
//        if (res == 0) {
//            return -1;
//        }
//        return ioResult("read", res);
//    }
//
//    public final int readAddress(long address, int pos, int limit) throws IOException {
//        int res = readAddress(fd, address, pos, limit);
//        if (res > 0) {
//            return res;
//        }
//        if (res == 0) {
//            return -1;
//        }
//        return ioResult("readAddress", res);
//    }

    @Override
    public String toString() {
        return "WinHandle{" +
                "handle=" + handle +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractWinHandle)) {
            return false;
        }

        return handle == ((AbstractWinHandle) o).handle;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(handle);
    }

    final boolean casState(int expected, int update) {
        return stateUpdater.compareAndSet(this, expected, update);
    }

    static boolean isClosed(int state) {
        return (state & STATE_CLOSED_MASK) != 0;
    }

    static boolean isInputShutdown(int state) {
        return (state & STATE_INPUT_SHUTDOWN_MASK) != 0;
    }

    static boolean isOutputShutdown(int state) {
        return (state & STATE_OUTPUT_SHUTDOWN_MASK) != 0;
    }

    static int inputShutdown(int state) {
        return state | STATE_INPUT_SHUTDOWN_MASK;
    }

    static int outputShutdown(int state) {
        return state | STATE_OUTPUT_SHUTDOWN_MASK;
    }

    protected abstract int closeImpl(long handle);
}
