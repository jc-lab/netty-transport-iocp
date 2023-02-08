package kr.jclab.netty.channel.iocp;

import io.netty.channel.Channel;

/**
 * {@link Channel} that expose operations that are only present on Windows systems.
 */
public interface OverlappedChannel extends Channel {
    /**
     * Returns the {@link AbstractWinHandle} that is used by this {@link Channel}.
     */
    AbstractWinHandle handle();
}