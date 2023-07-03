package kr.jclab.netty.channel.iocp;

import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOption;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.ServerSocketChannelConfig;

import java.util.Map;

import static kr.jclab.netty.channel.iocp.IocpChannelOption.SECURITY_ATTRIBUTES;

public class NamedPipeServerChannelConfig extends NamedPipeChannelConfig<NamedPipeServerChannelConfig> implements ChannelConfig {
    private int maxInstances = 100;
    private boolean flagFirstPipeInstance = false;
    private NativePointer securityAttributes = null;

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

    public NativePointer getSecurityAttributes() {
        return securityAttributes;
    }

    public void setSecurityAttributes(NativePointer securityAttributes) {
        this.securityAttributes = securityAttributes;
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(
                super.getOptions(),
                SECURITY_ATTRIBUTES
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == SECURITY_ATTRIBUTES) {
            return (T) this.securityAttributes;
        } else {
            return super.getOption(option);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (option == SECURITY_ATTRIBUTES) {
            this.securityAttributes = (NativePointer) value;
            return true;
        } else {
            return super.setOption(option, value);
        }
    }
}
