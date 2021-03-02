package com.softwareverde.network.socket;

import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.list.mutable.MutableList;

import java.io.IOException;

public class SocketServer<T extends Socket> {
    public interface SocketFactory<T> {
        T newSocket(java.net.Socket socket);
    }

    public interface SocketConnectedCallback<T> {
        void run(T socketConnection);
    }
    public interface SocketDisconnectedCallback<T> {
        void run(T socketConnection);
    }

    protected class ServerThread extends Thread {
        public ServerThread() {
            this.setName("Socket Server - Server Thread - " + this.getId());
        }

        @Override
        public void run() {
            try {
                while (_shouldContinue) {
                    if (_socket == null) { return; }

                    final T connection = _socketFactory.newSocket(_socket.accept());

                    final boolean shouldPurgeConnections = ((_nextConnectionId % PURGE_EVERY_COUNT) == 0L);
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
            catch (final Exception exception) { }
        }
    }

    protected static final Long PURGE_EVERY_COUNT = 20L;

    protected final Integer _port;
    protected final SocketFactory<T> _socketFactory;
    protected java.net.ServerSocket _socket;

    protected final MutableList<T> _connections = new MutableList<T>();

    protected Long _nextConnectionId = 0L;
    protected volatile Boolean _shouldContinue = true;
    protected Thread _serverThread = null;

    protected final ThreadPool _threadPool;

    protected SocketConnectedCallback<T> _socketConnectedCallback = null;
    protected SocketDisconnectedCallback<T> _socketDisconnectedCallback = null;

    protected void _purgeDisconnectedConnections() {
        final int socketCount = _connections.getCount();
        final MutableList<T> disconnectedSockets = new MutableList<T>(socketCount);

        synchronized (_connections) {
            int socketIndex = 0;
            while (socketIndex < _connections.getCount()) {
                final T connection = _connections.get(socketIndex);

                if (! connection.isConnected()) {
                    _connections.remove(socketIndex);
                    disconnectedSockets.add(connection);
                }
                else {
                    socketIndex += 1;
                }
            }
        }

        for (final T disconnectedSocket : disconnectedSockets) {
            _onDisconnect(disconnectedSocket);
        }
    }

    protected void _onConnect(final T socketConnection) {
        final SocketConnectedCallback<T> socketConnectedCallback = _socketConnectedCallback;
        if (socketConnectedCallback != null) {
            _threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    socketConnectedCallback.run(socketConnection);
                }
            });
        }
    }

    protected void _onDisconnect(final T socketConnection) {
        final SocketDisconnectedCallback<T> socketDisconnectedCallback = _socketDisconnectedCallback;
        if (socketDisconnectedCallback != null) {
            _threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    socketDisconnectedCallback.run(socketConnection);
                }
            });
        }
    }

    public SocketServer(final Integer port, final SocketFactory<T> socketFactory, final ThreadPool threadPool) {
        _port = port;
        _socketFactory = socketFactory;
        _threadPool = threadPool;
    }

    public void setSocketConnectedCallback(final SocketConnectedCallback<T> socketConnectedCallback) {
        _socketConnectedCallback = socketConnectedCallback;
    }

    public void setSocketDisconnectedCallback(final SocketDisconnectedCallback<T> socketDisconnectedCallback) {
        _socketDisconnectedCallback = socketDisconnectedCallback;
    }

    public void start() {
        _shouldContinue = true;

        try {
            _socket = new java.net.ServerSocket(_port);

            _serverThread = new ServerThread();
            _serverThread.start();

        }
        catch (final IOException exception) { }
    }

    public void stop() {
        _shouldContinue = false;

        if (_socket != null) {
            try {
                _socket.close();
            }
            catch (final IOException exception) { }
        }

        _socket = null;

        try {
            _serverThread.interrupt();
            if (_serverThread != null) {
                _serverThread.join(30000L);
            }
        }
        catch (final Exception exception) { }
    }
}
