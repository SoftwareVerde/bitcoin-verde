package com.softwareverde.bitcoin.server.socket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BitcoinServerSocket {
    public interface SocketEventCallback {
        void onConnect(BitcoinSocket socketConnection);
        void onDisconnect(BitcoinSocket socketConnection);
    }

    protected final Integer _port;
    protected java.net.ServerSocket _socket;

    protected final List<BitcoinSocket> _connections = new ArrayList<BitcoinSocket>();

    protected Long _nextConnectionId = 0L;
    protected volatile Boolean _shouldContinue = true;
    protected Thread _serverThread = null;

    protected SocketEventCallback _socketEventCallback = null;

    protected static final Long _purgeEveryCount = 20L;
    protected void _purgeDisconnectedConnections() {
        synchronized (_connections) {
            Integer i = 0;
            for (final BitcoinSocket connection : _connections) {
                if (!connection.isConnected()) {
                    _connections.remove(i.intValue());
                    System.out.println("Purging disconnected socket: " + i);

                    _onDisconnect(connection);
                }
                i += 1;
            }
        }
    }

    protected void _onConnect(final BitcoinSocket socketConnection) {
        final SocketEventCallback socketEventCallback = _socketEventCallback;

        if (socketEventCallback != null) {
            (new Thread(new Runnable() {
                @Override
                public void run() {
                    socketEventCallback.onConnect(socketConnection);
                }
            })).start();
        }
    }

    protected void _onDisconnect(final BitcoinSocket socketConnection) {
        final SocketEventCallback socketEventCallback = _socketEventCallback;

        if (socketEventCallback != null) {
            (new Thread(new Runnable() {
                @Override
                public void run() {
                    socketEventCallback.onDisconnect(socketConnection);
                }
            })).start();
        }
    }

    public BitcoinServerSocket(final Integer port) {
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

                            final BitcoinSocket connection = new BitcoinSocket(_socket.accept());

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
        catch (final IOException exception) { }
    }

    public void stop() {
        _shouldContinue = false;

        if (_socket != null) {
            try {
                _socket.close();
            }
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
