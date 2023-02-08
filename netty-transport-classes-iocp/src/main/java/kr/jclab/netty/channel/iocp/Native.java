/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package kr.jclab.netty.channel.iocp;

import io.netty.channel.ChannelException;
import io.netty.util.internal.ClassInitializerUtil;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.channels.Selector;

import static kr.jclab.netty.channel.iocp.Errors.newIOException;

/**
 * Native helper methods
 * <p><strong>Internal usage only!</strong>
 * <p>Static members which call JNI methods must be defined in {@link NativeStaticallyReferencedJniMethods}.
 */
public final class Native {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Native.class);

    public static final int INVALID_HANDLE_VALUE = -1;
    public static final int IOCP_CONTEXT_WAKEUP = 0x00000000;
    public static final int IOCP_CONTEXT_TIMER  = 0x00000001;
    public static final int IOCP_CONTEXT_HANDLE = 0x10000000;


    static {
        Selector selector = null;
        try {
            // We call Selector.open() as this will under the hood cause IOUtil to be loaded.
            // This is a workaround for a possible classloader deadlock that could happen otherwise:
            //
            // See https://github.com/netty/netty/issues/10187
            selector = Selector.open();
        } catch (IOException ignore) {
            // Just ignore
        }

        // Preload all classes that will be used in the OnLoad(...) function of JNI to eliminate the possiblity of a
        // class-loader deadlock. This is a workaround for https://github.com/netty/netty/issues/11209.

        // This needs to match all the classes that are loaded via NETTY_JNI_UTIL_LOAD_CLASS or looked up via
        // NETTY_JNI_UTIL_FIND_CLASS.
        ClassInitializerUtil.tryLoadClasses(
                Native.class,
                WinHandle.class,
                WsaEventHandle.class,
                ChannelException.class
        );

        try {
            // First, try calling a side-effect free JNI method to see if the library was already
            // loaded by the application.
            NativeStaticallyReferencedJniMethods.nop();
        } catch (UnsatisfiedLinkError ignore) {
            // The library was not previously loaded, load it now.
            loadNativeLibrary();
        } finally {
            try {
                if (selector != null) {
                    selector.close();
                }
            } catch (IOException ignore) {
                // Just ignore
            }
        }
    }

    private static void loadNativeLibrary() {
        String name = PlatformDependent.normalizedOs();
        if (!"windows".equals(name)) {
            throw new IllegalStateException("Only supported on Windows");
        }
        String staticLibName = "netty_transport_native_iocp";
        String sharedLibName = staticLibName + '_' + PlatformDependent.normalizedArch();
        ClassLoader cl = PlatformDependent.getClassLoader(Native.class);
        try {
            NativeLibraryLoader.load(sharedLibName, cl);
        } catch (UnsatisfiedLinkError e1) {
            try {
                NativeLibraryLoader.load(staticLibName, cl);
                logger.debug("Failed to load {}", sharedLibName, e1);
            } catch (UnsatisfiedLinkError e2) {
                ThrowableUtil.addSuppressed(e1, e2);
                throw e1;
            }
        }
    }

    private Native() {
        // utility
    }

    public static WinHandle createEvent(boolean manualReset, boolean initialState) throws Errors.NativeIoException {
        long handle = createEvent0(0, manualReset, initialState, null);
        if (handle <= 0) {
            throw Errors.newIOException("createEvent", (int) handle);
        }
        return new WinHandle(handle);
    }

    public static WinHandle newIoCompletionPort(long context, int numberOfConcurrentThreads) throws ChannelException {
        return new WinHandle(createIoCompletionPort0(INVALID_HANDLE_VALUE, 0, context, numberOfConcurrentThreads));
    }

    public static void attachIoCompletionPort(AbstractWinHandle handle, AbstractWinHandle existingCompletionPort, long context) throws Errors.NativeIoException {
        long newHandle = createIoCompletionPort0(handle.longValue(), existingCompletionPort.longValue(), context, 0);
        if (newHandle <= 0) {
            throw Errors.newIOException("attachIoCompletionPort", (int) newHandle);
        }
    }

    static int iocpWait(WinHandle iocpHandle, OverlappedEntryArray events, boolean immediatePoll) throws IOException {
        return iocpWait(iocpHandle, events, immediatePoll ? 0 : -1);
    }

    static int iocpWait(WinHandle iocpHandle, OverlappedEntryArray events, int timeoutMillis) throws IOException {
        int ready = getQueuedCompletionStatusExWait(iocpHandle.longValue(), events.memoryAddress(), events.length(), timeoutMillis);
        if (ready < 0) {
            throw newIOException("epoll_wait", ready);
        }
        return ready;
    }

    static int iocpBusyWait(WinHandle iocpHandle, OverlappedEntryArray events) throws IOException {
        int ready = getQueuedCompletionStatusExBusyWait(iocpHandle.longValue(), events.memoryAddress(), events.length());
        if (ready < 0) {
            throw newIOException("epoll_wait", ready);
        }
        return ready;
    }

    static int postWakeup(WinHandle iocpHandle, int completionKey) {
        return postQueuedCompletionStatus0(iocpHandle.longValue(), 0, completionKey, 0);
    }

    private static native long createIoCompletionPort0(long handle, long existingCompletionPort, long context, int numberOfConcurrentThreads) throws ChannelException;

    static native int readOverlappedEntry0(long pointer, OverlappedEntry entry);
    static native int readNativeOverlapped(NativeOverlapped entry);

    private static native int getQueuedCompletionStatusExWait(long handle, long entries, int count, int timeout);
    private static native int getQueuedCompletionStatusExBusyWait(long handle, long entries, int count);

    private static native int postQueuedCompletionStatus0(long handle, int numberOfBytesTransferred, long completionKey, long overlappedPointer);

    static native long createNamedPipe0(String name, int openMode, int maxInstances, int outBufferSize, int inBufferSize, int defaultTimeout, long securityAttributesPointer);
    static native int connectNamedPipe0(long handle, long overlappedPointer);
    static native long createEvent0(long securityAttributePointer, boolean manualReset, boolean initialState, String name);
    static native int overlappedInitialize0(long memory, long eventHandle, long fileHandle, int bufferSize);
    static native int startOverlappedRead0(long overlappedPointer);
    static native int startOverlappedWrite0(long overlappedPointer, int dataSize);
    static native long getNamedPipeClientProcessId0(long handle);

    static int startOverlappedRead(NativeOverlapped overlapped) throws Errors.NativeIoException {
        int rc = startOverlappedRead0(overlapped.memoryAddress());
        if (rc < 0) {
            throw Errors.newIOException("startOverlappedRead", rc);
        }
        return rc;
    }

    static int startOverlappedWrite(NativeOverlapped overlapped, int dataSize) throws Errors.NativeIoException {
        int rc = startOverlappedWrite0(overlapped.memoryAddress(), dataSize);
        if (rc < 0) {
            throw Errors.newIOException("startOverlappedWrite", rc);
        }
        return rc;
    }

    static long getNamedPipeClientProcessId(WinHandle handle) throws Errors.NativeIoException {
        long rc = getNamedPipeClientProcessId0(handle.longValue());
        if (rc < 0) {
            throw Errors.newIOException("startOverlappedWrite", (int) rc);
        }
        return rc;
    }
}
