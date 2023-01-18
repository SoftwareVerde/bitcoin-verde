package com.softwareverde.bitcoin.server.electrum.socket;

import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.http.tls.TlsCertificate;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.socket.JsonSocket;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.util.Iterator;

public class ElectrumServerSocket {
    public interface SocketEventCallback {
        void onConnect(JsonSocket socketConnection);
        void onDisconnect(JsonSocket socketConnection);
    }

    protected Runnable _createThreadRunnable(final java.net.ServerSocket serverSocket) {
        return new Runnable() {
            @Override
            public void run() {
                final Thread thread = Thread.currentThread();

                try {
                    int acceptCount = 0;
                    while (! thread.isInterrupted()) {
                        final JsonSocket connection = new JsonSocket(serverSocket.accept(), _threadPool);
                        acceptCount += 1;

                        final boolean shouldPurgeConnections = (acceptCount % _purgeEveryCount == 0L);
                        if (shouldPurgeConnections) {
                            _purgeDisconnectedConnections();
                        }

                        synchronized (_connections) {
                            _connections.add(connection);
                            _nextConnectionId += 1L;
                        }

                        _onConnect(connection);
                    }
                }
                catch (final IOException exception) { }
            }
        };
    }

    protected final Integer _port;
    protected final Integer _tlsPort;
    protected final TlsCertificate _tlsCertificate;
    protected java.net.ServerSocket _socket;
    protected java.net.ServerSocket _tlsSocket;

    protected final MutableList<JsonSocket> _connections = new MutableArrayList<>();

    protected Long _nextConnectionId = 0L;
    protected Thread _serverThread = null;
    protected Thread _tlsServerThread = null;

    protected SocketEventCallback _socketEventCallback = null;

    protected final ThreadPool _threadPool;

    protected static final Long _purgeEveryCount = 20L;
    protected void _purgeDisconnectedConnections() {
        synchronized (_connections) {
            final Iterator<JsonSocket> mutableIterator = _connections.mutableIterator();
            while (mutableIterator.hasNext()) {
                final JsonSocket connection = mutableIterator.next();
                if (connection == null) { continue; }

                if (! connection.isConnected()) {
                    mutableIterator.remove();
                    Logger.debug("Purging disconnected stratum socket: " + connection.getIp() + ":" + connection.getPort());

                    _onDisconnect(connection);
                }
            }
        }
    }

    protected void _onConnect(final JsonSocket socketConnection) {
        final SocketEventCallback socketEventCallback = _socketEventCallback;
        if (socketEventCallback != null) {
            _threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    socketEventCallback.onConnect(socketConnection);
                }
            });
        }
    }

    protected void _onDisconnect(final JsonSocket socketConnection) {
        final SocketEventCallback socketEventCallback = _socketEventCallback;
        if (socketEventCallback != null) {
            _threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    socketEventCallback.onDisconnect(socketConnection);
                }
            });
        }
    }

    public ElectrumServerSocket(final Integer port, final Integer tlsPort, final TlsCertificate tlsCertificate, final ThreadPool threadPool) {
        _port = port;
        _tlsPort = tlsPort;
        _tlsCertificate = tlsCertificate;
        _threadPool = threadPool;
    }

    public void setSocketEventCallback(final SocketEventCallback socketEventCallback) {
        _socketEventCallback = socketEventCallback;
    }

    public void start() {
        try {
            _socket = new java.net.ServerSocket(_port);
            if (_tlsCertificate != null && _tlsPort != null) {
                final SSLContext sslContext = _tlsCertificate.createContext();
                final SSLServerSocketFactory tlsSocketFactory = sslContext.getServerSocketFactory();
                _tlsSocket = tlsSocketFactory.createServerSocket(_tlsPort);
            }
            else {
                _tlsSocket = null;
            }

            _serverThread = new Thread(_createThreadRunnable(_socket));
            _tlsServerThread = (_tlsSocket != null ? new Thread(_createThreadRunnable(_tlsSocket)) : null);

            _serverThread.start();
            if (_tlsServerThread != null) {
                _tlsServerThread.start();
            }
        }
        catch (final Exception exception) {
            Logger.warn(exception);
        }
    }

    public void stop() {
        if (_socket != null) {
            try { _socket.close(); }
            catch (final IOException e) { }
        }

        _socket = null;
        _tlsSocket = null;

        final Thread serverThread = _serverThread;
        final Thread tlsServerThread = _tlsServerThread;
        _serverThread = null;
        _tlsServerThread = null;

        if (serverThread != null) {
            serverThread.interrupt();
        }

        if (tlsServerThread != null) {
            tlsServerThread.interrupt();
        }

        try {
            if (serverThread != null) {
                serverThread.join(30000L);
            }
        }
        catch (final Exception exception) { }

        try {
            if (tlsServerThread != null) {
                tlsServerThread.join(30000L);
            }
        }
        catch (final Exception exception) { }

        synchronized (_connections) {
            _connections.clear();
        }
    }
}
