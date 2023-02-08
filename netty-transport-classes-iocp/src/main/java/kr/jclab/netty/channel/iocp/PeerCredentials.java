package kr.jclab.netty.channel.iocp;

public class PeerCredentials {
    private final int pid;

    PeerCredentials(int p) {
        pid = p;
    }

    /**
     * Get the PID of the peer process.
     */
    public int pid() {
        return pid;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("PeerCredentials[pid=").append(pid).append(']');
        return sb.toString();
    }
}
