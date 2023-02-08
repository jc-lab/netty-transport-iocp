/*
 * Copyright 2014 The Netty Project
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

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.EventLoopTaskQueueFactory;
import io.netty.channel.SelectStrategy;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.util.IntSupplier;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Math.min;

/**
 * {@link EventLoop} which uses IOCP under the covers. Only works on Windows.
 *
 * WARNING
 * - Due to IOCP limitations, the minimum resolution of the timer is 1 ms.
 */
class IocpEventLoop extends SingleThreadEventLoop {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(IocpEventLoop.class);
    private static final int IOCP_WAIT_MILLIS_THRESHOLD =
            SystemPropertyUtil.getInt("kr.jclab.netty.channel.iocp.iocpWaitThreshold", 10);

    static {
        // Ensure JNI is initialized by the time this class is loaded by this time!
        // We use unix-common methods in this class which are backed by JNI methods.
        Iocp.ensureAvailability();
    }

    private final WinHandle iocpHandle;
//    private final IntObjectMap<AbstractEpollChannel> channels = new IntObjectHashMap<AbstractEpollChannel>(4096);
    private final boolean allowGrowing;
    private final OverlappedEntryArray events;

    // These are initialized on first use
//    private IovArray iovArray;
//    private NativeDatagramPacketArray datagramPacketArray;

    private final SelectStrategy selectStrategy;
    private final IntSupplier selectNowSupplier = new IntSupplier() {
        @Override
        public int get() throws Exception {
//            return epollWaitNow();
            return 0;
        }
    };

    private static final long AWAKE = -1L;
    private static final long NONE = Long.MAX_VALUE;

    // nextWakeupNanos is:
    //    AWAKE            when EL is awake
    //    NONE             when EL is waiting with no wakeup scheduled
    //    other value T    when EL is waiting with wakeup scheduled at time T
    private final AtomicLong nextWakeupNanos = new AtomicLong(AWAKE);
    private boolean pendingWakeup;

    private final ConcurrentHashMap<Long, AbstractIocpChannel> iocpChannels = new ConcurrentHashMap<>();

    IocpEventLoop(EventLoopGroup parent, Executor executor, int maxEvents,
                  SelectStrategy strategy, RejectedExecutionHandler rejectedExecutionHandler,
                  EventLoopTaskQueueFactory taskQueueFactory, EventLoopTaskQueueFactory tailTaskQueueFactory) {
        super(parent, executor, false, newTaskQueue(taskQueueFactory), newTaskQueue(tailTaskQueueFactory),
                rejectedExecutionHandler);
        selectStrategy = ObjectUtil.checkNotNull(strategy, "strategy");
        if (maxEvents == 0) {
            allowGrowing = true;
            events = new OverlappedEntryArray(4096);
        } else {
            allowGrowing = false;
            events = new OverlappedEntryArray(maxEvents);
        }
        boolean success = false;

        WinHandle iocpHandle = null;
        try {
            this.iocpHandle = iocpHandle = Native.newIoCompletionPort(0, 0);
            success = true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            if (!success) {
                if (iocpHandle != null) {
                    try {
                        iocpHandle.close();
                    } catch (IOException e) {
                    }
                }
            }
        }

//        FileDescriptor epollFd = null;
//        FileDescriptor eventFd = null;
//        FileDescriptor timerFd = null;
//        try {
//            this.epollFd = epollFd = Native.newEpollCreate();
//            this.eventFd = eventFd = Native.newEventFd();
//            try {
//                // It is important to use EPOLLET here as we only want to get the notification once per
//                // wakeup and don't call eventfd_read(...).
//                Native.epollCtlAdd(epollFd.intValue(), eventFd.intValue(), Native.EPOLLIN | Native.EPOLLET);
//            } catch (IOException e) {
//                throw new IllegalStateException("Unable to add eventFd filedescriptor to epoll", e);
//            }
//            this.timerFd = timerFd = Native.newTimerFd();
//            try {
//                // It is important to use EPOLLET here as we only want to get the notification once per
//                // wakeup and don't call read(...).
//                Native.epollCtlAdd(epollFd.intValue(), timerFd.intValue(), Native.EPOLLIN | Native.EPOLLET);
//            } catch (IOException e) {
//                throw new IllegalStateException("Unable to add timerFd filedescriptor to epoll", e);
//            }
//            success = true;
//        } finally {
//            if (!success) {
//                if (epollFd != null) {
//                    try {
//                        epollFd.close();
//                    } catch (Exception e) {
//                        // ignore
//                    }
//                }
//                if (eventFd != null) {
//                    try {
//                        eventFd.close();
//                    } catch (Exception e) {
//                        // ignore
//                    }
//                }
//                if (timerFd != null) {
//                    try {
//                        timerFd.close();
//                    } catch (Exception e) {
//                        // ignore
//                    }
//                }
//            }
//        }
    }

    private static Queue<Runnable> newTaskQueue(
            EventLoopTaskQueueFactory queueFactory) {
        if (queueFactory == null) {
            return newTaskQueue0(DEFAULT_MAX_PENDING_TASKS);
        }
        return queueFactory.newTaskQueue(DEFAULT_MAX_PENDING_TASKS);
    }

//    /**
//     * Return a cleared {@link IovArray} that can be used for writes in this {@link EventLoop}.
//     */
//    IovArray cleanIovArray() {
//        if (iovArray == null) {
//            iovArray = new IovArray();
//        } else {
//            iovArray.clear();
//        }
//        return iovArray;
//    }

    @Override
    protected void wakeup(boolean inEventLoop) {
        if (!inEventLoop && nextWakeupNanos.getAndSet(AWAKE) != AWAKE) {
            Native.postWakeup(iocpHandle, Native.IOCP_CONTEXT_WAKEUP);
        }
    }

    @Override
    protected boolean beforeScheduledTaskSubmitted(long deadlineNanos) {
        // Note this is also correct for the nextWakeupNanos == -1 (AWAKE) case
        return deadlineNanos < nextWakeupNanos.get();
    }

    @Override
    protected boolean afterScheduledTaskSubmitted(long deadlineNanos) {
        // Note this is also correct for the nextWakeupNanos == -1 (AWAKE) case
        return deadlineNanos < nextWakeupNanos.get();
    }

    void add(AbstractIocpChannel ch) throws IOException {
        assert inEventLoop();
    }

    void iocpRegister(WinHandle pipeHandle, AbstractIocpChannel ch) throws IOException {
        assert inEventLoop();
        iocpChannels.put(pipeHandle.longValue(), ch);
        Native.attachIoCompletionPort(pipeHandle, iocpHandle, Native.IOCP_CONTEXT_HANDLE);
    }

    // Only use for named pipe server
    void iocpChangeHandler(WinHandle pipeHandle, AbstractIocpChannel ch) {
        iocpChannels.put(pipeHandle.longValue(), ch);
    }

    void remove(AbstractIocpChannel ch) {
        AbstractWinHandle handle = ch.handle();
        if (handle != null) {
            AbstractIocpChannel removed = iocpChannels.remove(handle);
        }
        // nothing. just close handle
    }

//    /**
//     * Register the given epoll with this {@link EventLoop}.
//     */
//    void add(AbstractEpollChannel ch) throws IOException {
//        assert inEventLoop();
//        int fd = ch.socket.intValue();
//        Native.epollCtlAdd(epollFd.intValue(), fd, ch.flags);
//        AbstractEpollChannel old = channels.put(fd, ch);
//
//        // We either expect to have no Channel in the map with the same FD or that the FD of the old Channel is already
//        // closed.
//        assert old == null || !old.isOpen();
//    }
//
//    /**
//     * The flags of the given epoll was modified so update the registration
//     */
//    void modify(AbstractEpollChannel ch) throws IOException {
//        assert inEventLoop();
//        Native.epollCtlMod(epollFd.intValue(), ch.socket.intValue(), ch.flags);
//    }
//
//    /**
//     * Deregister the given epoll from this {@link EventLoop}.
//     */
//    void remove(AbstractEpollChannel ch) throws IOException {
//        assert inEventLoop();
//        int fd = ch.socket.intValue();
//
//        AbstractEpollChannel old = channels.remove(fd);
//        if (old != null && old != ch) {
//            // The Channel mapping was already replaced due FD reuse, put back the stored Channel.
//            channels.put(fd, old);
//
//            // If we found another Channel in the map that is mapped to the same FD the given Channel MUST be closed.
//            assert !ch.isOpen();
//        } else if (ch.isOpen()) {
//            // Remove the epoll. This is only needed if it's still open as otherwise it will be automatically
//            // removed once the file-descriptor is closed.
//            Native.epollCtlDel(epollFd.intValue(), fd);
//        }
//    }

    @Override
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return newTaskQueue0(maxPendingTasks);
    }

    private static Queue<Runnable> newTaskQueue0(int maxPendingTasks) {
        // This event loop never calls takeTask()
        return maxPendingTasks == Integer.MAX_VALUE ? PlatformDependent.<Runnable>newMpscQueue()
                : PlatformDependent.<Runnable>newMpscQueue(maxPendingTasks);
    }

    @Override
    public int registeredChannels() {
        return 0;
//        return channels.size();
    }

    @Override
    public Iterator<Channel> registeredChannelsIterator() {
//        assert inEventLoop();
//        IntObjectMap<AbstractEpollChannel> ch = channels;
//        if (ch.isEmpty()) {
//            return ChannelsReadOnlyIterator.empty();
//        }
//        return new ChannelsReadOnlyIterator<AbstractEpollChannel>(ch.values());
        return null;
    }

    // ... : count
    // LSB : used timer
    private long iocpWait(long deadlineNanos) throws IOException {
        if (deadlineNanos == NONE) {
            int value = Native.iocpWait(iocpHandle, events, IOCP_WAIT_MILLIS_THRESHOLD);
            if (value >= 0) {
                return ((long) value) << 1;
            }
            return value;
        }
        int armTimer = 0;
        long totalDelay = deadlineToDelayNanos(deadlineNanos);
        int delayMillis = (int) min(totalDelay / 1000000L, Integer.MAX_VALUE);
        if (delayMillis == 0) {
            delayMillis = 1;
            armTimer = 1;
        } else if (delayMillis >= IOCP_WAIT_MILLIS_THRESHOLD) {
            delayMillis = IOCP_WAIT_MILLIS_THRESHOLD;
        } else {
            armTimer = 1;
        }
        int value = Native.iocpWait(iocpHandle, events, delayMillis);
        if (value >= 0) {
            return ((long) value) << 1 | armTimer;
        }
        return value;
    }

    private int iocpWaitNoTimerChange() throws IOException {
        return Native.iocpWait(iocpHandle, events, false);
    }

    private int iocpWaitNow() throws IOException {
        return Native.iocpWait(iocpHandle, events, true);
    }

    private int iocpBusyWait() throws IOException {
        return Native.iocpBusyWait(iocpHandle, events);
    }

    private int iocpWaitTimeboxed() throws IOException {
        // Wait with 1 second "safeguard" timeout
        return Native.iocpWait(iocpHandle, events, 1000);
    }

    @Override
    protected void run() {
        long prevDeadlineNanos = NONE;
        for (;;) {
            try {
                int strategy = selectStrategy.calculateStrategy(selectNowSupplier, hasTasks());
                switch (strategy) {
                    case SelectStrategy.CONTINUE:
                        continue;

                    case SelectStrategy.BUSY_WAIT:
                        strategy = iocpBusyWait();
                        break;

                    case SelectStrategy.SELECT:
                        if (pendingWakeup) {
                            // We are going to be immediately woken so no need to reset wakenUp
                            // or check for timerfd adjustment.
                            strategy = iocpWaitTimeboxed();
                            if (strategy != 0) {
                                break;
                            }
                            // We timed out so assume that we missed the write event due to an
                            // abnormally failed syscall (the write itself or a prior epoll_wait)
                            logger.warn("Missed eventfd write (not seen after > 1 second)");
                            pendingWakeup = false;
                            if (hasTasks()) {
                                break;
                            }
                            // fall-through
                        }

                        long curDeadlineNanos = nextScheduledTaskDeadlineNanos();
                        if (curDeadlineNanos == -1L) {
                            curDeadlineNanos = NONE; // nothing on the calendar
                        }
                        nextWakeupNanos.set(curDeadlineNanos);
                        try {
                            if (!hasTasks()) {
                                if (curDeadlineNanos == prevDeadlineNanos) {
                                    // No timer activity needed
                                    strategy = iocpWaitNoTimerChange();
                                } else {
                                    // Timerfd needs to be re-armed or disarmed
                                    long result = iocpWait(curDeadlineNanos);
                                    // The result contains the actual return value and if a timer was used or not.
                                    // We need to "unpack" using the helper methods exposed in Native.
                                    strategy = ((int) (result >> 1));
                                    prevDeadlineNanos = ((result & 1) != 0) ? curDeadlineNanos : NONE;
                                }
                            }
                        } finally {
                            // Try get() first to avoid much more expensive CAS in the case we
                            // were woken via the wakeup() method (submitted task)
                            if (nextWakeupNanos.get() == AWAKE || nextWakeupNanos.getAndSet(AWAKE) == AWAKE) {
                                pendingWakeup = true;
                            }
                        }
                        // fallthrough
                    default:
                }

                try {
                    if (strategy > 0 && processReady(events, strategy)) {
                        prevDeadlineNanos = NONE;
                    }
                } finally {
                    // Ensure we always run tasks.
                    runAllTasks();
                }

                if (allowGrowing && strategy == events.length()) {
                    //increase the size of the array as we needed the whole space for the events
                    events.increase();
                }
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                handleLoopException(t);
            } finally {
                // Always handle shutdown even if the loop processing threw an exception.
                try {
                    if (isShuttingDown()) {
                        closeAll();
                        if (confirmShutdown()) {
                            break;
                        }
                    }
                } catch (Error e) {
                    throw e;
                } catch (Throwable t) {
                    handleLoopException(t);
                }
            }
        }
    }

    /**
     * Visible only for testing!
     */
    void handleLoopException(Throwable t) {
        logger.warn("Unexpected exception in the selector loop.", t);

        // Prevent possible consecutive immediate failures that lead to
        // excessive CPU consumption.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // Ignore.
        }
    }

    private void closeAll() {
//        // Using the intermediate collection to prevent ConcurrentModificationException.
//        // In the `close()` method, the channel is deleted from `channels` map.
//        AbstractEpollChannel[] localChannels = channels.values().toArray(new AbstractEpollChannel[0]);
//
//        for (AbstractEpollChannel ch: localChannels) {
//            ch.unsafe().close(ch.unsafe().voidPromise());
//        }
    }

    // Returns true if a timerFd event was encountered
    private boolean processReady(OverlappedEntryArray events, int ready) {
        boolean timerFired = false;
        for (int i = 0; i < ready; i ++) {
            OverlappedEntry entry = events.getEntry(i);
            if (entry.getCompletionKey() == Native.IOCP_CONTEXT_WAKEUP) {
                pendingWakeup = false;
            } else if (entry.getCompletionKey() == Native.IOCP_CONTEXT_HANDLE) {
                if (!entry.isOverlappedValid()) {
                    logger.warn("invalid overlapped object", entry);
                    continue;
                }

                AbstractIocpChannel channel = iocpChannels.get(entry.getFileHandle());
                if (channel == null) {
                    logger.warn("removed channel", entry);
                    continue;
                }

                try {
                    channel.handleEvent(entry);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
//            final int fd = events.fd(i);
//            if (fd == eventFd.intValue()) {
//                pendingWakeup = false;
//            } else if (fd == timerFd.intValue()) {
//                timerFired = true;
//            } else {
//                final long ev = events.events(i);
//
//                AbstractEpollChannel ch = channels.get(fd);
//                if (ch != null) {
//                    // Don't change the ordering of processing EPOLLOUT | EPOLLRDHUP / EPOLLIN if you're not 100%
//                    // sure about it!
//                    // Re-ordering can easily introduce bugs and bad side-effects, as we found out painfully in the
//                    // past.
//                    AbstractEpollChannel.AbstractEpollUnsafe unsafe = (AbstractEpollChannel.AbstractEpollUnsafe) ch.unsafe();
//
//                    // First check for EPOLLOUT as we may need to fail the connect ChannelPromise before try
//                    // to read from the file descriptor.
//                    // See https://github.com/netty/netty/issues/3785
//                    //
//                    // It is possible for an EPOLLOUT or EPOLLERR to be generated when a connection is refused.
//                    // In either case epollOutReady() will do the correct thing (finish connecting, or fail
//                    // the connection).
//                    // See https://github.com/netty/netty/issues/3848
//                    if ((ev & (Native.EPOLLERR | Native.EPOLLOUT)) != 0) {
//                        // Force flush of data as the epoll is writable again
//                        unsafe.epollOutReady();
//                    }
//
//                    // Check EPOLLIN before EPOLLRDHUP to ensure all data is read before shutting down the input.
//                    // See https://github.com/netty/netty/issues/4317.
//                    //
//                    // If EPOLLIN or EPOLLERR was received and the channel is still open call epollInReady(). This will
//                    // try to read from the underlying file descriptor and so notify the user about the error.
//                    if ((ev & (Native.EPOLLERR | Native.EPOLLIN)) != 0) {
//                        // The Channel is still open and there is something to read. Do it now.
//                        unsafe.epollInReady();
//                    }
//
//                    // Check if EPOLLRDHUP was set, this will notify us for connection-reset in which case
//                    // we may close the channel directly or try to read more data depending on the state of the
//                    // Channel and als depending on the AbstractEpollChannel subtype.
//                    if ((ev & Native.EPOLLRDHUP) != 0) {
//                        unsafe.epollRdHupReady();
//                    }
//                } else {
//                    // We received an event for an fd which we not use anymore. Remove it from the epoll_event set.
//                    try {
//                        Native.epollCtlDel(epollFd.intValue(), fd);
//                    } catch (IOException ignore) {
//                        // This can happen but is nothing we need to worry about as we only try to delete
//                        // the fd from the epoll set as we not found it in our mappings. So this call to
//                        // epollCtlDel(...) is just to ensure we cleanup stuff and so may fail if it was
//                        // deleted before or the file descriptor was closed before.
//                    }
//                }
//            }
//        }
        return timerFired;
    }

    @Override
    protected void cleanup() {
        try {
            // Ensure any in-flight wakeup writes have been performed prior to closing eventFd.
            while (pendingWakeup) {
                try {
                    int count = iocpWaitTimeboxed();
                    if (count == 0) {
                        // We timed-out so assume that the write we're expecting isn't coming
                        break;
                    }
                    for (int i = 0; i < count; i++) {
                        OverlappedEntry entry = events.getEntry(i);
                        if (entry.getCompletionKey() == Native.IOCP_CONTEXT_WAKEUP) {
                            pendingWakeup = false;
                            break;
                        }
                    }
                } catch (IOException ignore) {
                    // ignore
                }
            }
            try {
                iocpHandle.close();
            } catch (IOException e) {
                logger.warn("Failed to close the IOCP Handle.", e);
            }
        } finally {
            events.free();
        }
    }
}
