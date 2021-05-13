package com.softwareverde.network.socket;

import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.message.ProtocolMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Socket implements AutoCloseable {
    protected static final AtomicLong NEXT_SOCKET_ID = new AtomicLong(1L);

    protected interface ReadThread extends AutoCloseable {
        interface Callback {
            void onNewMessage(ProtocolMessage protocolMessage);
            void onExit();
        }

        default void setSocketName(final String socketName) { }

        void setInputStream(InputStream inputStream);
        void setCallback(Callback callback);
        void interrupt();
        void join() throws InterruptedException;
        void join(long timeout) throws InterruptedException;
        void start();

        Long getTotalBytesReceived();

        @Override
        void close();
    }

    protected interface WriteThread extends AutoCloseable {
        interface Callback {
            void onExit();
        }

        default void setSocketName(final String socketName) { }

        void setOutputStream(OutputStream outputStream);
        void setCallback(Callback callback);
        void interrupt();
        void join() throws InterruptedException;
        void join(long timeout) throws InterruptedException;
        void start();

        Boolean write(ByteArray bytes);
        void flush();

        Long getTotalBytesWritten();
        Long getTotalBytesDroppedCount();

        @Override
        void close();
    }

    protected final Long _id;
    protected final java.net.Socket _socket;
    protected final ConcurrentLinkedQueue<ProtocolMessage> _messages = new ConcurrentLinkedQueue<ProtocolMessage>();
    protected final AtomicBoolean _isClosed = new AtomicBoolean(false);

    protected Runnable _messageReceivedCallback;
    protected Runnable _socketClosedCallback;
    protected final AtomicBoolean _listenThreadWasStarted = new AtomicBoolean(false);
    protected final AtomicBoolean _writeThreadWasStarted = new AtomicBoolean(false);
    protected final ReadThread _readThread;
    protected final WriteThread _writeThread;
    protected String _cachedHost = null;

    protected final OutputStream _rawOutputStream;
    protected final InputStream _rawInputStream;

    protected final ThreadPool _threadPool;

    protected String _getHost() {
        if (_cachedHost == null) {
            Logger.debug("Performing ip lookup for: " + _socket.getRemoteSocketAddress());
            final InetAddress inetAddress = _socket.getInetAddress();
            _cachedHost = (inetAddress != null ? inetAddress.getHostName() : null);
        }

        return _cachedHost;
    }

    protected Integer _getPort() {
        return _socket.getPort();
    }

    /**
     * Internal callback that is executed when a message is received by the client.
     *  Is executed before any external callbacks are received.
     *  Intended for subclass extension.
     */
    protected void _onMessageReceived(final ProtocolMessage message) {
        final Runnable messageReceivedCallback = _messageReceivedCallback;
        if (messageReceivedCallback != null) {
            _threadPool.execute(messageReceivedCallback);
        }
    }

    /**
     * Internal callback that is executed when the connection is closed by either the client or server,
     *  or if the connection is terminated.
     *  Intended for subclass extension.
     */
    protected void _onSocketClosed() {
        // Nothing.
    }

    protected void _closeSocket() {
        final boolean wasOpen = _isClosed.compareAndSet(false, true);
        if (! wasOpen) { return; }

        Logger.debug("Closing socket. Thread Id: " + Thread.currentThread().getId() + " " + _socket.getRemoteSocketAddress());

        _readThread.close();
        _writeThread.close();

        try {
            _rawInputStream.close();
        }
        catch (final Exception exception) { }

        try {
            _rawOutputStream.close();
        }
        catch (final Exception exception) { }

        try {
            _readThread.join(5000L);
        }
        catch (final Exception exception) { }

        try {
            _writeThread.join(5000L);
        }
        catch (final Exception exception) { }

        try {
            _socket.close();
        }
        catch (final Exception exception) { }

        final Runnable onCloseCallback = _socketClosedCallback;
        _socketClosedCallback = null;

        if (onCloseCallback != null) {
            _threadPool.execute(onCloseCallback);
        }

        _onSocketClosed();
    }

    protected void _startWriteThreadIfNotStarted() {
        final boolean wasNotStarted = _writeThreadWasStarted.compareAndSet(false, true);
        if (! wasNotStarted) { return; }

        _writeThread.start();
    }

    protected Socket(final java.net.Socket socket, final ReadThread readThread, final WriteThread writeThread, final ThreadPool threadPool) {
        _id = NEXT_SOCKET_ID.getAndIncrement();
        _socket = socket;

        InputStream inputStream = null;
        OutputStream outputStream = null;
        { // Initialize the input and output streams...
            try {
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();
            }
            catch (final IOException exception) { }
        }
        _rawOutputStream = outputStream;
        _rawInputStream = inputStream;

        final String socketName = (Ip.fromSocket(socket) + ":" + _getPort());

        _readThread = readThread;
        _readThread.setInputStream(inputStream);
        _readThread.setCallback(new ReadThread.Callback() {
            @Override
            public void onNewMessage(final ProtocolMessage message) {
                _messages.offer(message);
                _onMessageReceived(message);
            }

            @Override
            public void onExit() {
                _closeSocket();
            }
        });
        _readThread.setSocketName(socketName);

        _writeThread = writeThread;
        _writeThread.setOutputStream(outputStream);
        _writeThread.setCallback(new WriteThread.Callback() {
            @Override
            public void onExit() {
                _closeSocket();
            }
        });
        _writeThread.setSocketName(socketName);

        _threadPool = threadPool;
    }

    public void enableTcpDelay(final Boolean enableTcpDelay) {
        try {
            _socket.setTcpNoDelay(! enableTcpDelay);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }
    }

    public void beginListening() {
        final boolean wasNotStarted = _listenThreadWasStarted.compareAndSet(false, true);
        if (! wasNotStarted) { return; }

        _readThread.start();
    }

    public void setMessageReceivedCallback(final Runnable callback) {
        _messageReceivedCallback = callback;
    }

    public void setOnClosedCallback(final Runnable callback) {
        _socketClosedCallback = callback;
    }

    public Boolean write(final ProtocolMessage outboundMessage) {
        if (_isClosed.get()) { return false; }

        _startWriteThreadIfNotStarted();

        final ByteArray bytes = outboundMessage.getBytes();
        return _writeThread.write(bytes);
    }

    public void flush() {
        if (_isClosed.get()) { return; }
        _writeThread.flush();
    }

    /**
     * Retrieves the oldest message from the inbound queue and returns it.
     *  Returns null if there are no pending messages.
     */
    public ProtocolMessage popMessage() {
        return _messages.poll();
    }

    /**
     * Attempts to return the DNS lookup of the connection or null if the lookup fails.
     */
    public String getHost() {
        return _getHost();
    }

    public Ip getIp() {
        return Ip.fromSocket(_socket);
    }

    public Integer getPort() {
        return _getPort();
    }

    /**
     * Ceases all reads, and closes the socket.
     *  Invoking any write functions after this call throws a runtime exception.
     */
    @Override
    public void close() {
        _closeSocket();
    }

    /**
     * Returns false if this instance has had its close() function invoked or the socket is no longer connected.
     */
    public Boolean isConnected() {
        if (_isClosed.get()) { return false; }
        return (! _socket.isClosed());
    }

    public Long getTotalBytesSentCount() {
        return _writeThread.getTotalBytesWritten();
    }

    public Long getTotalBytesReceivedCount() {
        return _readThread.getTotalBytesReceived();
    }

    public Long getTotalBytesDroppedCount() {
        return _writeThread.getTotalBytesDroppedCount();
    }

    @Override
    public int hashCode() {
        return (BinarySocket.class.getSimpleName().hashCode() + _id.hashCode());
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof BinarySocket)) { return false; }

        final BinarySocket socketConnectionObject = (BinarySocket) object;
        return _id.equals(socketConnectionObject._id);
    }

    @Override
    public String toString() {
        return (Ip.fromSocket(_socket) + ":" + _getPort());
    }
}
