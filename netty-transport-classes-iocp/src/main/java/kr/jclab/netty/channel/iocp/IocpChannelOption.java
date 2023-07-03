package kr.jclab.netty.channel.iocp;

import io.netty.channel.ChannelOption;

public class IocpChannelOption {
    public static final ChannelOption<NativePointer> SECURITY_ATTRIBUTES = ChannelOption.valueOf("SECURITY_ATTRIBUTES");
}
