package com.softwareverde.bitcoin.stratum.socket;

import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.socket.JsonSocket;

import java.io.IOException;
import java.util.Iterator;

public class StratumServerSocket {
    public interface SocketEventCallback {
        void onConnect(JsonSocket socketConnection);
        void onDisconnect(JsonSocket socketConnection);
    }

    protected final Integer _port;
    protected java.net.ServerSocket _socket;

    protected final MutableList<JsonSocket> _connections = new MutableArrayList<>();

    protected Long _nextConnectionId = 0L;
    protected volatile Boolean _shouldContinue = true;
    protected Thread _serverThread = null;

    protected SocketEventCallback _socketEventCallback = null;

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
            socketEventCallback.onConnect(socketConnection);
        }
    }

    protected void _onDisconnect(final JsonSocket socketConnection) {
        final SocketEventCallback socketEventCallback = _socketEventCallback;
        if (socketEventCallback != null) {
            socketEventCallback.onDisconnect(socketConnection);
        }
    }

    public StratumServerSocket(final Integer port) {
        _port = port;
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

                            final JsonSocket connection = new JsonSocket(_socket.accept());

                            final boolean shouldPurgeConnections = (_nextConnectionId % _purgeEveryCount == 0L);
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
        catch (final Exception exception) {
            Logger.warn(exception);
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
                _serverThread.join(30000L);
            }
        }
        catch (final Exception exception) { }
    }
}
