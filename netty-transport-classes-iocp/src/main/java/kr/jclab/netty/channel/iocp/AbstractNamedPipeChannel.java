package kr.jclab.netty.channel.iocp;

import io.netty.channel.*;

public abstract class AbstractNamedPipeChannel extends AbstractIocpChannel {
    protected AbstractNamedPipeChannel(Channel parent) {
        super(parent);
    }

    protected AbstractNamedPipeChannel(Channel parent, ChannelId id) {
        super(parent, id);
    }

    protected abstract class AbstractNamedPipeChannelUnsafe extends AbstractIocpUnsafe {

    }
}
