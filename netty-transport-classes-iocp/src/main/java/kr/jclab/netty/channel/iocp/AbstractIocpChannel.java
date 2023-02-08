package kr.jclab.netty.channel.iocp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.util.concurrent.Future;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

public abstract class AbstractIocpChannel extends AbstractChannel {
    /**
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    protected ChannelPromise connectPromise;
    protected Future<?> connectTimeoutFuture;

    boolean inputClosedSeenErrorOnRead;

    protected volatile boolean active;

    protected AbstractIocpChannel(Channel parent) {
        super(parent);
    }

    protected AbstractIocpChannel(Channel parent, ChannelId id) {
        super(parent, id);
    }

    @Override
    public abstract IocpChannelConfig config();

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof IocpEventLoop;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    protected void doRegister() throws Exception {
        ((IocpEventLoop) eventLoop()).add(this);
    }

    @Override
    protected void doDeregister() throws Exception {
        ((IocpEventLoop) eventLoop()).remove(this);
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        active = false;
        // Even if we allow half closed sockets we should give up on reading. Otherwise we may allow a read attempt on a
        // socket which has not even been connected yet. This has been observed to block during unit tests.
        inputClosedSeenErrorOnRead = true;
        try {
            ChannelPromise promise = connectPromise;
            if (promise != null) {
                // Use tryFailure() instead of setFailure() to avoid the race against cancel().
                promise.tryFailure(new ClosedChannelException());
                connectPromise = null;
            }

            Future<?> future = connectTimeoutFuture;
            if (future != null) {
                future.cancel(false);
                connectTimeoutFuture = null;
            }

            if (isRegistered()) {
                // Need to check if we are on the EventLoop as doClose() may be triggered by the GlobalEventExecutor
                // if SO_LINGER is used.
                //
                // See https://github.com/netty/netty/issues/7159
                EventLoop loop = eventLoop();
                if (loop.inEventLoop()) {
                    doDeregister();
                } else {
                    loop.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                doDeregister();
                            } catch (Throwable cause) {
                                pipeline().fireExceptionCaught(cause);
                            }
                        }
                    });
                }
            }
        } finally {
            doCloseHandle();
        }
    }

    public abstract AbstractWinHandle handle();

    protected abstract void doCloseHandle() throws IOException;

    protected abstract void handleEvent(OverlappedEntry entry) throws Exception;

    protected abstract class AbstractIocpUnsafe extends AbstractUnsafe {
        @Override
        public void flush0() {
            super.flush0();
        }
    }
}
