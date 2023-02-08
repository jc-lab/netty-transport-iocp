package kr.jclab.netty.channel.iocp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class NamedPipeChannel extends AbstractIocpChannel implements Channel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NamedPipeChannelUnsafe.class);
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private final NamedPipeChannelConfig config = new NamedPipeChannelConfig(this);
    private WinHandle handle = null;

    private NativeOverlapped readOverlapped = null;
    private NativeOverlapped writeOverlapped = null;

    private final Runnable flushTask = new Runnable() {
        @Override
        public void run() {
            // Calling flush0 directly to ensure we not try to flush messages that were added via write(...) in the
            // meantime.
            ((AbstractIocpUnsafe) unsafe()).flush0();
        }
    };


    public NamedPipeChannel() {
        super(null);
    }

    public NamedPipeChannel(Channel parent, ChannelId id, WinHandle handle) throws Errors.NativeIoException {
        super(parent, id);
        this.handle = handle;
        this.active = true;
        prepareWrite();
    }

    @Override
    public WinHandle handle() {
        return handle;
    }

    @Override
    public NamedPipeChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return handle.isOpen();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    protected void handleEvent(OverlappedEntry entry) throws Exception {
        assert eventLoop().inEventLoop();
        if (readOverlapped != null && readOverlapped.memoryAddress() == entry.getOverlappedPointer()) {
            int size = entry.getNumberOfBytesTransferred();
            if (size > 0) {
                ByteBuffer sliced = readOverlapped.sliceData(size);
                pipeline().fireChannelRead(Unpooled.wrappedBuffer(sliced));
                startRead();
            } else {
                unsafe().close(voidPromise());
            }
        } else if (writeOverlapped != null && writeOverlapped.memoryAddress() == entry.getOverlappedPointer()) {
            eventLoop().execute(flushTask);
        }
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new NamedPipeChannelUnsafe();
    }

    @Override
    protected SocketAddress localAddress0() {
        return null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {

    }

    @Override
    protected void doBeginRead() throws Exception {
        if (readOverlapped == null) {
            readOverlapped = new NativeOverlapped(handle, config.getReceiveBufferSize());
        }
        startRead();
    }

    private void startRead() throws Errors.NativeIoException {
        Native.startOverlappedRead(readOverlapped);
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        final int msgCount = in.size();
        if (msgCount > 0) {
            ByteBuf buffer = (ByteBuf) in.current();
            int bytes = writeOverlapped.writeData(buffer);
            Native.startOverlappedWrite(writeOverlapped, bytes);
            in.removeBytes(bytes);
        } else {
            eventLoop().execute(flushTask);
        }
    }

    @Override
    protected void doCloseHandle() throws IOException {
        handle.close();
        if (readOverlapped != null) {
            readOverlapped.free();
            readOverlapped = null;
        }
    }

    private void prepareWrite() throws Errors.NativeIoException {
        if (writeOverlapped == null) {
            writeOverlapped = new NativeOverlapped(handle, config.getSendBufferSize());
        }
    }

    private class NamedPipeChannelUnsafe extends AbstractIocpUnsafe {
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {

        }
    }
}
