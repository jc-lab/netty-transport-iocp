/*
 * Copyright 2016 The Netty Project
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

import java.nio.ByteBuffer;

/**
 * This class is necessary to break the following cyclic dependency:
 * <ol>
 * <li>JNI_OnLoad</li>
 * <li>JNI Calls FindClass because RegisterNatives (used to register JNI methods) requires a class</li>
 * <li>FindClass loads the class, but static members variables of that class attempt to call a JNI method which has not
 * yet been registered.</li>
 * <li>java.lang.UnsatisfiedLinkError is thrown because native method has not yet been registered.</li>
 * </ol>
 * Static members which call JNI methods must not be declared in this class!
 */
final class NativeStaticallyReferencedJniMethods {

    private NativeStaticallyReferencedJniMethods() { }

    static native int nop();

    static native long bufferMemoryAddress(ByteBuffer buffer);
    static native int bufferAddressSize();

    static native int sizeOfUlongPtr();
    static native int sizeOfPtr();
    static native int sizeOfOverlappedEntry();
    static native int sizeOfNativeOverlappedStruct();

    static native int pipeAccessDuplex();
    static native int pipeAccessInbound();
    static native int pipeAccessOutbound();
    static native int fileFlagFirstPipeInstance();
    static native int fileFlagWriteThrough();
    static native int fileFlagOverlapped();
    static native int flagGenericRead();
    static native int flagGenericWrite();
    static native int flagOpenExisting();
    static native int flagPipeReadmodeMessage();
    static native int flagPipeReadmodeByte();
    static native int errorNotFound();

    /**
     * Close Handle
     *
     * @return if 0 success, otherwise windows error code
     */
    static native int winCloseHandle(long handle);

    /**
     * Close WSA Event Handle
     *
     * @return if 0 success, otherwise windows error code
     */
    static native int wsaCloseEvent(long handle);
}
