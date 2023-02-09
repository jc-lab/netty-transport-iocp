package kr.jclab.netty.channel.iocp;

import io.netty.channel.*;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;

public class NamedPipeServerChannel extends AbstractNamedPipeChannel implements ServerChannel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NamedPipeServerChannel.class);
    private static final int FILE_FLAG_FIRST_PIPE_INSTANCE = NativeStaticallyReferencedJniMethods.fileFlagFirstPipeInstance();
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private final NamedPipeServerChannelConfig config;

    private NamedPipeSocketAddress localAddress = null;
    private WinHandle pendingConnectHandle = null;
    private boolean handleRegistered = false;
    private NativeOverlapped connectOverlapped = null;

    public NamedPipeServerChannel() {
        super(null);
        this.config = new NamedPipeServerChannelConfig(this);
    }

    @Override
    public AbstractWinHandle handle() {
        return pendingConnectHandle;
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new IocpPipeServerUnsafe();
    }

    @Override
    protected SocketAddress localAddress0() {
        return localAddress;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doBind(SocketAddress local) throws Exception {
        if (!(local instanceof NamedPipeSocketAddress)) {
            throw new UnsupportedOperationException("address is not NamedPipeSocketAddress");
        }

        NamedPipeSocketAddress addressImpl = (NamedPipeSocketAddress) local;
        this.localAddress = addressImpl;
        this.connectOverlapped = new NativeOverlapped(0);

        active = true;
        createListenPipe();
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public NamedPipeServerChannelConfig config() {
        return config;
    }

    @Override
    public boolean isActive() {
        return active && (pendingConnectHandle != null);
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    protected void doBeginRead() throws Exception {
        if (pendingConnectHandle != null && !handleRegistered) {
            ((IocpEventLoop) eventLoop()).iocpRegister(pendingConnectHandle, this);
            handleRegistered = true;
            startConnect();
        }
    }

    @Override
    protected void doCloseHandle() throws IOException {
        if (pendingConnectHandle != null) {
            try {
                if (connectOverlapped != null) {
                    int rc = Native.cancelIoEx0(pendingConnectHandle.longValue(), connectOverlapped.memoryAddress());
                    if (rc == 0) {
                        connectOverlapped.refDec();
                    }
                }
                pendingConnectHandle.close();
            } catch (Exception e) {
            }
        }
        if (connectOverlapped != null) {
            connectOverlapped.refDec();
            connectOverlapped = null;
        }
    }

    @Override
    protected void handleEvent(OverlappedEntry entry) throws IOException {
        if (entry.getOverlappedPointer() != connectOverlapped.memoryAddress()) {
            logger.warn("invalid pointer: ", entry.getOverlappedPointer() + " != " + connectOverlapped.memoryAddress());
            return ;
        }
        connectOverlapped.refDec();

        NamedPipeChannel namedPipeChannel = new NamedPipeChannel(this, DefaultChannelId.newInstance(), pendingConnectHandle);
        ((IocpEventLoop) eventLoop()).iocpChangeHandler(pendingConnectHandle, namedPipeChannel);
        this.pipeline().fireChannelRead(namedPipeChannel);

        if (config.isFlagFirstPipeInstance()) {
            pendingConnectHandle = null;
        } else {
            createListenPipe();
            startConnect();
        }
    }

    private void createListenPipe() throws IOException {
        long handleValue = Native.createNamedPipe0(
                localAddress.getName(),
                this.config.isFlagFirstPipeInstance() ? FILE_FLAG_FIRST_PIPE_INSTANCE : 0,
                this.config.getMaxInstances(),
                this.config.getSendBufferSize(),
                this.config.getReceiveBufferSize(),
                this.config.getDefaultTimeout(),
                0
        );
        if (handleValue < 0) {
            throw Errors.newIOException("createNamedPipe0", (int) handleValue);
        }

        WinHandle pipeHandle = new WinHandle(handleValue);
        this.pendingConnectHandle = pipeHandle;

        if (handleRegistered) {
            ((IocpEventLoop) eventLoop()).iocpRegister(pipeHandle, this);
        }
    }

    private void startConnect() throws IOException {
        connectOverlapped.initialize(pendingConnectHandle);
        connectOverlapped.refInc();
        int result = Native.connectNamedPipe0(pendingConnectHandle.longValue(), connectOverlapped.memoryAddress());
        if (result < 0) {
            connectOverlapped.refDec();
            throw Errors.newIOException("connectNamedPipe", result);
        }
    }

    private final class IocpPipeServerUnsafe extends AbstractNamedPipeChannelUnsafe {
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            throw new UnsupportedOperationException();
        }
    }
}
