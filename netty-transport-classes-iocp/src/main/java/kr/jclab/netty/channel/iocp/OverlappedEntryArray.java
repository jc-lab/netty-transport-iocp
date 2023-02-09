/*
 * Copyright 2015 The Netty Project
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
 * This is an internal datastructure which can be directly passed to OVERLAPPED_ENTRY to reduce the overhead.
 */
public final class OverlappedEntryArray {
    private static final int OVERLAPPED_ENTRY_SIZE = NativeStaticallyReferencedJniMethods.sizeOfOverlappedEntry();

    private ByteBuffer memory;
    private long memoryAddress;
    private int length;

    OverlappedEntryArray(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("length must be >= 1 but was " + length);
        }
        this.length = length;
        memory = Buffer.allocateDirectWithNativeOrder(calculateBufferCapacity(length));
        memoryAddress = Buffer.memoryAddress(memory);
        MemoryLeakDetector.put(memoryAddress, this);
    }

    /**
     * Return the {@code memoryAddress} which points to the start of this {@link OverlappedEntryArray}.
     */
    long memoryAddress() {
        return memoryAddress;
    }

    /**
     * Return the length of the {@link OverlappedEntryArray} which represent the maximum number of {@code epoll_events}
     * that can be stored in it.
     */
    int length() {
        return length;
    }

    /**
     * Increase the storage of this {@link OverlappedEntryArray}.
     */
    void increase() {
        // double the size
        length <<= 1;
        // There is no need to preserve what was in the memory before.
        ByteBuffer buffer = Buffer.allocateDirectWithNativeOrder(calculateBufferCapacity(length));
        Buffer.free(memory);
        MemoryLeakDetector.remove(memoryAddress);
        memory = buffer;
        memoryAddress = Buffer.memoryAddress(buffer);
        MemoryLeakDetector.put(memoryAddress, this);
    }

    /**
     * Free this {@link OverlappedEntryArray}. Any usage after calling this method may segfault the JVM!
     */
    void free() {
        if (memoryAddress != 0) {
            MemoryLeakDetector.remove(memoryAddress);
            Buffer.free(memory);
            memoryAddress = 0;
        }
    }

    public OverlappedEntry getEntry(int index) {
        long n = (long) index * (long) OVERLAPPED_ENTRY_SIZE;
        OverlappedEntry entry = new OverlappedEntry();
        Native.readOverlappedEntry0(memoryAddress + n, entry);
        return entry;
    }

    private static int calculateBufferCapacity(int capacity) {
        return capacity * OVERLAPPED_ENTRY_SIZE;
    }
}
