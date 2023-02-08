package kr.jclab.netty.channel.iocp;

import io.netty.channel.ChannelConfig;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.ServerSocketChannelConfig;

public class NamedPipeServerChannelConfig extends NamedPipeChannelConfig<NamedPipeServerChannelConfig> implements ChannelConfig {
    private int maxInstances = 100;
    private boolean flagFirstPipeInstance = false;

    public NamedPipeServerChannelConfig(AbstractIocpChannel channel) {
        super(channel);
    }

    public NamedPipeServerChannelConfig(AbstractIocpChannel channel, RecvByteBufAllocator recvByteBufAllocator) {
        super(channel, recvByteBufAllocator);
    }

    public int getMaxInstances() {
        return maxInstances;
    }

    public NamedPipeServerChannelConfig setMaxInstances(int maxInstances) {
        this.maxInstances = maxInstances;
        return this;
    }

    public boolean isFlagFirstPipeInstance() {
        return flagFirstPipeInstance;
    }

    public NamedPipeServerChannelConfig setFlagFirstPipeInstance(boolean flagFirstPipeInstance) {
        this.flagFirstPipeInstance = flagFirstPipeInstance;
        return this;
    }
}
