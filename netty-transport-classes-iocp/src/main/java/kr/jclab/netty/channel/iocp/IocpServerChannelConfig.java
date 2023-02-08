package kr.jclab.netty.channel.iocp;

import io.netty.channel.RecvByteBufAllocator;

public class IocpServerChannelConfig extends IocpChannelConfig {
    IocpServerChannelConfig(AbstractIocpChannel channel) {
        super(channel);
    }

    IocpServerChannelConfig(AbstractIocpChannel channel, RecvByteBufAllocator recvByteBufAllocator) {
        super(channel, recvByteBufAllocator);
    }
}
