package kr.jclab.netty.channel.iocp;

public class WsaEventHandle extends AbstractWinHandle {
    public WsaEventHandle(long handle) {
        super(handle);
    }

    @Override
    protected int closeImpl(long handle) {
        return NativeStaticallyReferencedJniMethods.wsaCloseEvent(handle);
    }
}
