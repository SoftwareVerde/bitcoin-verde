package com.softwareverde.bitcoin.server.stratum.socket;

import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.io.Logger;
import com.softwareverde.socket.SocketConnection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StratumServerSocket {
    public interface SocketEventCallback {
        void onConnect(SocketConnection socketConnection);
        void onDisconnect(SocketConnection socketConnection);
    }

    protected final Integer _port;
    protected java.net.ServerSocket _socket;

    protected final List<SocketConnection> _connections = new ArrayList<SocketConnection>();

    protected Long _nextConnectionId = 0L;
    protected volatile Boolean _shouldContinue = true;
    protected Thread _serverThread = null;

    protected SocketEventCallback _socketEventCallback = null;

    protected final ThreadPool _threadPool;

    protected static final Long _purgeEveryCount = 20L;
    protected void _purgeDisconnectedConnections() {
        synchronized (_connections) {
            Integer i = 0;
            for (final SocketConnection connection : _connections) {
                if (!connection.isConnected()) {
                    _connections.remove(i.intValue());
                    System.out.println("Purging disconnected stratum socket: " + i);

                    _onDisconnect(connection);
                }
                i += 1;
            }
        }
    }

    protected void _onConnect(final SocketConnection socketConnection) {
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

    protected void _onDisconnect(final SocketConnection socketConnection) {
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

    public StratumServerSocket(final Integer port, final ThreadPool threadPool) {
        _port = port;
        _threadPool = threadPool;
    }

    public void setSocketEventCallback(final SocketEventCallback socketEventCallback) {
        _socketEventCallback = socketEventCallback;
    }

    public void start() {
        _shouldContinue = true;

        try {
            _socket = new java.net.ServerSocket(_port);

            _serverThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (_shouldContinue) {
                            if (_socket == null) { return; }

                            final SocketConnection connection = new SocketConnection(_socket.accept());

                            final Boolean shouldPurgeConnections = (_nextConnectionId % _purgeEveryCount == 0L);
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
            });
            _serverThread.start();

        }
        catch (final IOException exception) {
            Logger.log(exception);
        }
    }

    public void stop() {
        _shouldContinue = false;

        if (_socket != null) {
            try { _socket.close(); }
            catch (final IOException e) { }
        }

        _socket = null;

        try {
            if (_serverThread != null) {
                _serverThread.join();
            }
        }
        catch (final Exception exception) { }
    }
}
