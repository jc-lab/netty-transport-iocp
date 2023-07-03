package kr.jclab.netty.channel.iocp;

public interface NativePointer extends RefCount {
    long getPointer();
}
