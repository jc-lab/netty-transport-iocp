package kr.jclab.netty.channel.iocp;

public interface RefCount {
    void refInc();
    void refDec();
    int getRefCount();
}
