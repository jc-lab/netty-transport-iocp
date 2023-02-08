package kr.jclab.netty.channel.iocp;

import io.netty.util.internal.EmptyArrays;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NotYetConnectedException;

/**
 * <strong>Internal usage only!</strong>
 */
public final class Errors {
//    // As all our JNI methods return -errno on error we need to compare with the negative errno codes.
//    public static final int ERRNO_ENOENT_NEGATIVE = -errnoENOENT();
//    public static final int ERRNO_ENOTCONN_NEGATIVE = -errnoENOTCONN();
//    public static final int ERRNO_EBADF_NEGATIVE = -errnoEBADF();
//    public static final int ERRNO_EPIPE_NEGATIVE = -errnoEPIPE();
//    public static final int ERRNO_ECONNRESET_NEGATIVE = -errnoECONNRESET();
//    public static final int ERRNO_EAGAIN_NEGATIVE = -errnoEAGAIN();
//    public static final int ERRNO_EWOULDBLOCK_NEGATIVE = -errnoEWOULDBLOCK();
//    public static final int ERRNO_EINPROGRESS_NEGATIVE = -errnoEINPROGRESS();
//    public static final int ERROR_ECONNREFUSED_NEGATIVE = -errorECONNREFUSED();
//    public static final int ERROR_EISCONN_NEGATIVE = -errorEISCONN();
//    public static final int ERROR_EALREADY_NEGATIVE = -errorEALREADY();
//    public static final int ERROR_ENETUNREACH_NEGATIVE = -errorENETUNREACH();

    /**
     * Holds the mappings for errno codes to String messages.
     * This eliminates the need to call back into JNI to get the right String message on an exception
     * and thus is faster.
     *
     * The array length of 512 should be more then enough because errno.h only holds < 200 codes.
     */
    private static final String[] ERRORS = new String[512];

    /**
     * <strong>Internal usage only!</strong>
     */
    public static final class NativeIoException extends IOException {
        private static final long serialVersionUID = -835244428854019171L;

        private final int expectedErr;
        private final boolean fillInStackTrace;

        public NativeIoException(String method, int expectedErr) {
            this(method, expectedErr, true);
        }

        public NativeIoException(String method, int expectedErr, boolean fillInStackTrace) {
            super(method + "(..) failed: " + expectedErr);
            this.expectedErr = expectedErr;
            this.fillInStackTrace = fillInStackTrace;
        }

        public int expectedErr() {
            return expectedErr;
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            if (fillInStackTrace) {
                return super.fillInStackTrace();
            }
            return this;
        }
    }

//    static final class NativeConnectException extends ConnectException {
//        private static final long serialVersionUID = -5532328671712318161L;
//        private final int expectedErr;
//        NativeConnectException(String method, int expectedErr) {
//            super(method + "(..) failed: " + ERRORS[-expectedErr]);
//            this.expectedErr = expectedErr;
//        }
//
//        int expectedErr() {
//            return expectedErr;
//        }
//    }
//
//    static {
//        for (int i = 0; i < ERRORS.length; i++) {
//            // This is ok as strerror returns 'Unknown error i' when the message is not known.
//            ERRORS[i] = strError(i);
//        }
//    }
//
//    static boolean handleConnectErrno(String method, int err) throws IOException {
//        if (err == ERRNO_EINPROGRESS_NEGATIVE || err == ERROR_EALREADY_NEGATIVE) {
//            // connect not complete yet need to wait for EPOLLOUT event.
//            // EALREADY has been observed when using tcp fast open on centos8.
//            return false;
//        }
//        throw newConnectException0(method, err);
//    }
//
//    /**
//     * @deprecated Use {@link #handleConnectErrno(String, int)}.
//     * @param method The native method name which caused the errno.
//     * @param err the negative value of the errno.
//     * @throws IOException The errno translated into an exception.
//     */
//    @Deprecated
//    public static void throwConnectException(String method, int err) throws IOException {
//        if (err == ERROR_EALREADY_NEGATIVE) {
//            throw new ConnectionPendingException();
//        }
//        throw newConnectException0(method, err);
//    }
//
//    private static IOException newConnectException0(String method, int err) {
//        if (err == ERROR_ENETUNREACH_NEGATIVE) {
//            return new NoRouteToHostException();
//        }
//        if (err == ERROR_EISCONN_NEGATIVE) {
//            throw new AlreadyConnectedException();
//        }
//        if (err == ERRNO_ENOENT_NEGATIVE) {
//            return new FileNotFoundException();
//        }
//        return new ConnectException(method + "(..) failed: " + ERRORS[-err]);
//    }
//
//    public static NativeIoException newConnectionResetException(String method, int errnoNegative) {
//        NativeIoException exception = new NativeIoException(method, errnoNegative, false);
//        exception.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
//        return exception;
//    }
//
    public static NativeIoException newIOException(String method, int err) {
        return new NativeIoException(method, err);
    }

//    @Deprecated
//    public static int ioResult(String method, int err, NativeIoException resetCause,
//                               ClosedChannelException closedCause) throws IOException {
//        // network stack saturated... try again later
//        if (err == ERRNO_EAGAIN_NEGATIVE || err == ERRNO_EWOULDBLOCK_NEGATIVE) {
//            return 0;
//        }
//        if (err == resetCause.expectedErr()) {
//            throw resetCause;
//        }
//        if (err == ERRNO_EBADF_NEGATIVE) {
//            throw closedCause;
//        }
//        if (err == ERRNO_ENOTCONN_NEGATIVE) {
//            throw new NotYetConnectedException();
//        }
//        if (err == ERRNO_ENOENT_NEGATIVE) {
//            throw new FileNotFoundException();
//        }
//
//        // TODO: We could even go further and use a pre-instantiated IOException for the other error codes, but for
//        //       all other errors it may be better to just include a stack trace.
//        throw newIOException(method, err);
//    }
//
//    public static int ioResult(String method, int err) throws IOException {
//        // network stack saturated... try again later
//        if (err == ERRNO_EAGAIN_NEGATIVE || err == ERRNO_EWOULDBLOCK_NEGATIVE) {
//            return 0;
//        }
//        if (err == ERRNO_EBADF_NEGATIVE) {
//            throw new ClosedChannelException();
//        }
//        if (err == ERRNO_ENOTCONN_NEGATIVE) {
//            throw new NotYetConnectedException();
//        }
//        if (err == ERRNO_ENOENT_NEGATIVE) {
//            throw new FileNotFoundException();
//        }
//
//        throw new NativeIoException(method, err, false);
//    }

    private Errors() { }
}