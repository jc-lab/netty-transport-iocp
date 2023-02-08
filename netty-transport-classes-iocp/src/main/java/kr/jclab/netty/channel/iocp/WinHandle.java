package kr.jclab.netty.channel.iocp;

public class WinHandle extends AbstractWinHandle {
    public WinHandle(long handle) {
        super(handle);
    }

    @Override
    protected int closeImpl(long handle) {
        return NativeStaticallyReferencedJniMethods.winCloseHandle(handle);
    }
}
