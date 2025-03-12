package kr.jclab.netty.channel.iocp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.ScheduledFuture;
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

    private int connectRetryCount = 0;
    private ScheduledFuture<?> deferredConnectFuture = null;


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
        ChannelPipeline pipeline = pipeline();
        if (readOverlapped != null && readOverlapped.memoryAddress() == entry.getOverlappedPointer()) {
            readOverlapped.refDec();
            int size = entry.getNumberOfBytesTransferred();
            if (size > 0) {
                ByteBuf buffer = this.config.getAllocator().buffer(size);
                readOverlapped.readData(buffer, size);

                try {
                    pipeline.fireChannelRead(buffer);
                    pipeline.fireChannelReadComplete();
                } catch (Throwable e) {
                    pipeline.fireChannelReadComplete();
                    pipeline.fireExceptionCaught(e);
                }
            } else {
                unsafe().close(voidPromise());
            }
        } else if (writeOverlapped != null && writeOverlapped.memoryAddress() == entry.getOverlappedPointer()) {
            writeOverlapped.refDec();
            eventLoop().execute(flushTask);
        }
    }

    public static ByteBuffer clone(ByteBuffer original) {
        ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        original.rewind();//copy from the beginning
        clone.put(original);
        original.rewind();
        clone.flip();
        return clone;
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
         if (readOverlapped.refCount() > 1) {
            return ;
        }
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
        boolean written = false;
        while (!written && in.size() > 0) {
            if (writeOverlapped.refCount() > 1) {
                return ;
            }

            ByteBuf current = (ByteBuf) in.current();
            written = doWriteInternal(in, current);
        }
    }

    private boolean doWriteInternal(ChannelOutboundBuffer in, ByteBuf current) throws Exception {
        if (!current.isReadable()) {
            in.remove();
            return false;
        }

        int bytes = writeOverlapped.writeData(current);
        try {
            writeOverlapped.refInc();
            Native.startOverlappedWrite(writeOverlapped, bytes);
        } catch (Exception e) {
            writeOverlapped.refDec();
            throw e;
        }

        in.progress(bytes);
        if (!current.isReadable()) {
            in.remove();
        }

        return true;
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

    private boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise, boolean wasActive) throws IOException {
        if (!(remoteAddress instanceof NamedPipeSocketAddress)) {
            throw new UnresolvedAddressException();
        }

        if (handle != null) {
            // Check if already connected before trying to connect.
            throw new AlreadyConnectedException();
        }

        requestedRemoteAddress = (NamedPipeSocketAddress) remoteAddress;

        promise.addListener((f) -> {
           if (!f.isSuccess() && f.isCancelled()) {
               ScheduledFuture<?> future = deferredConnectFuture;
               deferredConnectFuture = null;
               if (future != null) {
                   future.cancel(false);
               }
           }
        });
        return connectRetryable(promise, wasActive);
    }

    private boolean connectRetryable(ChannelPromise promise, boolean wasActive) {
        try {
            WinHandle connectedHandle = Native.createFile(
                    requestedRemoteAddress.getName(),
                    Native.GENERIC_READ | Native.GENERIC_WRITE,
                    0,
                    0,
                    Native.OPEN_EXISTING,
                    Native.FILE_FLAG_OVERLAPPED,
                    0
            );
            if (config.isMessageMode()) {
                Native.setPipeMessageReadMode(connectedHandle, Native.PIPE_READMODE_MESSAGE);
            }
            this.handle = connectedHandle;

            prepareWrite();
            ((IocpEventLoop) eventLoop()).iocpRegister(handle, this);
        } catch (Errors.NativeIoException e) {
            if (e.getCode() == -231) {
                // pipe is busy

                if (connectRetryCount < config.getMaxBusyRetries()) {
                    // RETRY
                    connectRetryCount++;
                    connectRetryAfter(promise, wasActive, 100);
                    return false;
                } else {
                    fulfillConnectPromise(promise, new Errors.PipeBusyException(e));
                    return true;
                }
            }

            fulfillConnectPromise(promise, e);
            return true;
        } catch (Exception e) {
            fulfillConnectPromise(promise, e);
            return true;
        }

        finishConnect();
        fulfillConnectPromise(promise, wasActive);

        return true;
    }

    private void connectRetryAfter(ChannelPromise promise, boolean wasActive, int delayMs) {
        deferredConnectFuture = eventLoop().schedule(() -> {
            try {
                connectRetryable(promise, wasActive);
            } catch (Exception e2) {
                fulfillConnectPromise(promise, e2);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void finishConnect() {
        remoteAddress = requestedRemoteAddress;
        requestedRemoteAddress = null;
        deferredConnectFuture = null;
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
                connectPromise = promise;
                if (!doConnect(remoteAddress, localAddress, promise, wasActive)) {
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
