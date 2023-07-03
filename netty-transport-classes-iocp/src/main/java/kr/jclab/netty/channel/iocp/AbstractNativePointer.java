package kr.jclab.netty.channel.iocp;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractNativePointer implements NativePointer {
    private AtomicInteger refCount = new AtomicInteger(1);
    private long pointer = 0;

    public AbstractNativePointer(long pointer) {
        this.pointer = pointer;
    }

    @Override
    public long getPointer() {
        return pointer;
    }

    @Override
    public int getRefCount() {
        return refCount.get();
    }

    @Override
    public void refInc() {
        refCount.incrementAndGet();
    }

    @Override
    public void refDec() {
        if (refCount.decrementAndGet() <= 0) {
            free();
        }
    }

    public void free() {
        if (pointer == 0) {
            return ;
        }
        nativeFree(pointer);
        refCount.set(0);
    }

    protected abstract void nativeFree(long pointer);
}
