package kr.jclab.netty.channel.iocp;

import java.net.SocketAddress;

public class NamedPipeSocketAddress extends SocketAddress {
    private final String name;

    public NamedPipeSocketAddress(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
