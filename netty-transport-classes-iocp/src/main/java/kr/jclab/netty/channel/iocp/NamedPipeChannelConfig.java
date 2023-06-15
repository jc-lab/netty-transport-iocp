package kr.jclab.netty.channel.iocp;

import io.netty.channel.RecvByteBufAllocator;

public class NamedPipeChannelConfig<T extends NamedPipeChannelConfig<T>> extends IocpChannelConfig {
    private int receiveBufferSize = 1024;
    private int sendBufferSize = 1024;
    private int defaultTimeout = 5000;
    private boolean messageMode = false; // use PIPE_READMODE_MESSAGE

    NamedPipeChannelConfig(AbstractIocpChannel channel) {
        super(channel);
    }

    NamedPipeChannelConfig(AbstractIocpChannel channel, RecvByteBufAllocator recvByteBufAllocator) {
        super(channel, recvByteBufAllocator);
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    @SuppressWarnings("unchecked")
    public T setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
        return (T) this;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    @SuppressWarnings("unchecked")
    public T setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
        return (T) this;
    }

    public int getDefaultTimeout() {
        return defaultTimeout;
    }

    @SuppressWarnings("unchecked")
    public T setDefaultTimeout(int defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
        return (T) this;
    }

    public boolean isMessageMode() {
        return messageMode;
    }

    @SuppressWarnings("unchecked")
    public T setMessageMode(boolean messageMode) {
        this.messageMode = messageMode;
        return (T) this;
    }
}
