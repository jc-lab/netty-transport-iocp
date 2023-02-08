package kr.jclab.netty.channel.iocp;

import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.RecvByteBufAllocator;

public class IocpChannelConfig extends DefaultChannelConfig {
    IocpChannelConfig(AbstractIocpChannel channel) {
        super(channel);
    }

    IocpChannelConfig(AbstractIocpChannel channel, RecvByteBufAllocator recvByteBufAllocator) {
        super(channel, recvByteBufAllocator);
    }
}
