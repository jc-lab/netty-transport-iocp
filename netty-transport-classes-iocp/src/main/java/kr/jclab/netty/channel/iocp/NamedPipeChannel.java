package kr.jclab.netty.channel.iocp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.TimeUnit;

public class NamedPipeChannel extends AbstractIocpChannel implements Channel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NamedPipeChannelUnsafe.class);
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private final NamedPipeChannelConfig config = new NamedPipeChannelConfig(this);
    private WinHandle handle = null;

    private NativeOverlapped readOverlapped = null;
    private NativeOverlapped writeOverlapped = null;

    private PeerCredentials peerCredentials = null;

    private final Runnable flushTask = new Runnable() {
        @Override
        public void run() {
            // Calling flush0 directly to ensure we not try to flush messages that were added via write(...) in the
            // meantime.
            ((AbstractIocpUnsafe) unsafe()).flush0();
        }
    };

    private NamedPipeSocketAddress requestedRemoteAddress = null;
    private NamedPipeSocketAddress remoteAddress = null;


    public NamedPipeChannel() {
        super(null);
    }

    public NamedPipeChannel(Channel parent, ChannelId id, WinHandle handle) throws Errors.NativeIoException {
        super(parent, id);
        this.handle = handle;
        this.active = true;
        this.peerCredentials = new PeerCredentials((int) Native.getNamedPipeClientProcessId(handle));
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
    public ChannelMetadata metadata() {
        return METADATA;
    }

    public PeerCredentials peerCredentials() {
        return peerCredentials;
    }

    @Override
    protected void handleEvent(OverlappedEntry entry) throws Exception {
        assert eventLoop().inEventLoop();
        if (readOverlapped != null && readOverlapped.memoryAddress() == entry.getOverlappedPointer()) {
            readOverlapped.refDec();
            int size = entry.getNumberOfBytesTransferred();
            if (size > 0) {
                ByteBuffer sliced = readOverlapped.sliceData(size);
                pipeline().fireChannelRead(Unpooled.wrappedBuffer(sliced));
                startRead();
            } else {
                unsafe().close(voidPromise());
            }
        } else if (writeOverlapped != null && writeOverlapped.memoryAddress() == entry.getOverlappedPointer()) {
            writeOverlapped.refDec();
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
        return remoteAddress;
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
        try {
            readOverlapped.refInc();
            Native.startOverlappedRead(readOverlapped);
        } catch (Exception e) {
            readOverlapped.refDec();
            throw e;
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        final int msgCount = in.size();
        if (msgCount > 0) {
            ByteBuf buffer = (ByteBuf) in.current();
            int bytes = writeOverlapped.writeData(buffer);
            try {
                writeOverlapped.refInc();
                Native.startOverlappedWrite(writeOverlapped, bytes);
            } catch (Exception e) {
                writeOverlapped.refDec();
                throw e;
            }
            in.removeBytes(bytes);
        } else {
            eventLoop().execute(flushTask);
        }
    }

    @Override
    protected void doCloseHandle() throws IOException {
        int rc;
        if (readOverlapped != null) {
            if (handle != null) {
                rc = Native.cancelIoEx0(handle.longValue(), readOverlapped.memoryAddress());
                if (rc == 0) {
                    readOverlapped.refDec();
                }
            }

            readOverlapped.refDec();
            readOverlapped = null;
        }
        if (writeOverlapped != null) {
            if (handle != null) {
                rc = Native.cancelIoEx0(handle.longValue(), writeOverlapped.memoryAddress());
                if (rc == 0) {
                    writeOverlapped.refDec();
                }
            }

            writeOverlapped.refDec();
            writeOverlapped = null;
        }
        if (handle != null) {
            handle.close();
        }
    }

    private void prepareWrite() throws Errors.NativeIoException {
        if (writeOverlapped == null) {
            writeOverlapped = new NativeOverlapped(handle, config.getSendBufferSize());
        }
    }

    private boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws IOException {
        if (!(remoteAddress instanceof NamedPipeSocketAddress)) {
            throw new UnresolvedAddressException();
        }

        if (handle != null) {
            // Check if already connected before trying to connect.
            throw new AlreadyConnectedException();
        }

        NamedPipeSocketAddress remoteNamedPipeAddress = (NamedPipeSocketAddress) remoteAddress;
        requestedRemoteAddress = remoteNamedPipeAddress;

        WinHandle connectedHandle = Native.createFile(
                remoteNamedPipeAddress.getName(),
                Native.GENERIC_READ | Native.GENERIC_WRITE,
                0,
                0,
                Native.OPEN_EXISTING,
                Native.FILE_FLAG_OVERLAPPED,
                0
        );
        Native.setPipeMessageReadMode(connectedHandle, Native.PIPE_READMODE_MESSAGE);
        this.handle = connectedHandle;

        prepareWrite();
        ((IocpEventLoop) eventLoop()).iocpRegister(handle, this);

        return true;
    }

    private void finishConnect() {
        remoteAddress = requestedRemoteAddress;
        requestedRemoteAddress = null;
    }

    private void fulfillConnectPromise(ChannelPromise promise, boolean wasActive) {
        if (promise == null) {
            // Closed via cancellation and the promise has been notified already.
            return;
        }
        active = true;

        // Get the state as trySuccess() may trigger an ChannelFutureListener that will close the Channel.
        // We still need to ensure we call fireChannelActive() in this case.
        boolean active = isActive();

        // trySuccess() will return false if a user cancelled the connection attempt.
        boolean promiseSet = promise.trySuccess();

        // Regardless if the connection attempt was cancelled, channelActive() event should be triggered,
        // because what happened is what happened.
        if (!wasActive && active) {
            pipeline().fireChannelActive();
        }

        // If a user cancelled the connection attempt, close the channel, which is followed by channelInactive().
        if (!promiseSet) {
            close(voidPromise());
        }
    }

    private void fulfillConnectPromise(ChannelPromise promise, Throwable cause) {
        if (promise == null) {
            // Closed via cancellation and the promise has been notified already.
            return;
        }

        // Use tryFailure() instead of setFailure() to avoid the race against cancel().
        promise.tryFailure(cause);
        closeIfClosed();
    }

    private class NamedPipeChannelUnsafe extends AbstractIocpUnsafe {
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }

            try {
                if (connectPromise != null) {
                    throw new ConnectionPendingException();
                }

                boolean wasActive = isActive();
                if (doConnect(remoteAddress, localAddress)) {
                    finishConnect();
                    fulfillConnectPromise(promise, wasActive);
                } else {
                    connectPromise = promise;

                    // Schedule connect timeout.
                    int connectTimeoutMillis = config().getConnectTimeoutMillis();
                    if (connectTimeoutMillis > 0) {
                        connectTimeoutFuture = eventLoop().schedule(new Runnable() {
                            @Override
                            public void run() {
                                ChannelPromise connectPromise = NamedPipeChannel.this.connectPromise;
                                if (connectPromise != null && !connectPromise.isDone()
                                        && connectPromise.tryFailure(new ConnectTimeoutException(
                                        "connection timed out: " + remoteAddress))) {
                                    close(voidPromise());
                                }
                            }
                        }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
                    }

                    promise.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isCancelled()) {
                                if (connectTimeoutFuture != null) {
                                    connectTimeoutFuture.cancel(false);
                                }
                                connectPromise = null;
                                close(voidPromise());
                            }
                        }
                    });
                }
            } catch (Throwable t) {
                closeIfClosed();
                promise.tryFailure(annotateConnectException(t, remoteAddress));
            }
        }
    }
}
