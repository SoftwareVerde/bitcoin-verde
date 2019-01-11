package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.servlet.WebSocketServlet;
import com.softwareverde.servlet.request.WebSocketRequest;
import com.softwareverde.servlet.response.WebSocketResponse;
import com.softwareverde.servlet.socket.WebSocket;
import com.softwareverde.util.RotatingQueue;

import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

public class AnnouncementsApi implements WebSocketServlet {
    protected static final Object MUTEX = new Object();
    protected static final HashMap<Long, WebSocket> WEB_SOCKETS = new HashMap<Long, WebSocket>();

    protected static final RotatingQueue<Json> BLOCK_HEADERS = new RotatingQueue<Json>(10);
    protected static final RotatingQueue<Json> TRANSACTIONS = new RotatingQueue<Json>(32);

    protected final AtomicLong _nextSocketId = new AtomicLong(1L);
    protected final Configuration.ExplorerProperties _explorerProperties;
    protected final Object _socketConnectionMutex = new Object();
    protected Boolean _isShuttingDown = false;
    protected JsonSocket _socketConnection = null;

    protected Json _wrapObject(final String objectType, final Json object) {
        final Json json = new Json();
        json.put("objectType", objectType);
        json.put("object", object);
        return json;
    }

    protected void _checkRpcConnection() {
        { // Lock-less check...
            final JsonSocket jsonSocket = _socketConnection;
            if ((jsonSocket != null) && (jsonSocket.isConnected())) {
                return;
            }
        }

        synchronized (_socketConnectionMutex) {
            { // Locked, 2nd check...
                final JsonSocket jsonSocket = _socketConnection;
                if ((jsonSocket != null) && (jsonSocket.isConnected())) {
                    return;
                }
            }

            if (_isShuttingDown) { return; }

            final String bitcoinRpcUrl = _explorerProperties.getBitcoinRpcUrl();
            final Integer bitcoinRpcPort = _explorerProperties.getBitcoinRpcPort();
            _socketConnection = null;

            try {
                final Socket socket = new Socket(bitcoinRpcUrl, bitcoinRpcPort);
                if (socket.isConnected()) {
                    final JsonSocket jsonSocket = new JsonSocket(socket, _threadPool);

                    jsonSocket.setMessageReceivedCallback(new Runnable() {
                        @Override
                        public void run() {
                            final JsonProtocolMessage message = jsonSocket.popMessage();
                            final Json json = message.getMessage();

                            final String objectType = json.getString("objectType");
                            final Json object = json.get("object");
                            switch (objectType) {
                                case "BLOCK": {
                                    _onNewBlock(object);
                                } break;

                                case "TRANSACTION": {
                                    _onNewTransaction(object);
                                } break;

                                default: { } break;
                            }
                        }
                    });

                    final Json registerHookRpcJson = new Json();
                    {
                        registerHookRpcJson.put("method", "POST");
                        registerHookRpcJson.put("query", "ADD_HOOK");

                        final Json eventTypesJson = new Json(true);
                        eventTypesJson.add("NEW_BLOCK");
                        eventTypesJson.add("NEW_TRANSACTION");

                        final Json parametersJson = new Json();
                        parametersJson.put("events", eventTypesJson);

                        registerHookRpcJson.put("parameters", parametersJson);
                    }

                    final JsonProtocolMessage jsonProtocolMessage = new JsonProtocolMessage(registerHookRpcJson);
                    jsonSocket.write(jsonProtocolMessage);
                    _socketConnection = jsonSocket;

                    jsonSocket.beginListening();
                }
            }
            catch (final Exception exception) {
                Logger.log(exception);
            }
        }
    }

    public AnnouncementsApi(final Configuration.ExplorerProperties explorerProperties) {
        _explorerProperties = explorerProperties;
    }

    protected final MainThreadPool _threadPool = new MainThreadPool(256, 1000L);

    protected void _broadcastNewBlockHeader(final Json blockHeaderJson) {
        final String message;
        {
            final Json messageJson = _wrapObject("BLOCK", blockHeaderJson);
            message = messageJson.toString();
        }

        synchronized (MUTEX) {
            for (final WebSocket webSocket : WEB_SOCKETS.values()) {
                webSocket.sendMessage(message);
            }
        }
    }

    protected void _broadcastNewTransaction(final Json transactionJson) {
        final String message;
        {
            final Json messageJson = _wrapObject("TRANSACTION", transactionJson);
            message = messageJson.toString();
        }

        synchronized (MUTEX) {
            for (final WebSocket webSocket : WEB_SOCKETS.values()) {
                webSocket.sendMessage(message);
            }
        }
    }

    protected void _onNewBlock(final Json blockJson) {
        synchronized (MUTEX) {
            BLOCK_HEADERS.add(blockJson);
        }

        _broadcastNewBlockHeader(blockJson);
    }

    protected void _onNewTransaction(final Json transactionJson) {
        synchronized (MUTEX) {
            TRANSACTIONS.add(transactionJson);
        }

        _broadcastNewTransaction(transactionJson);
    }

    @Override
    public WebSocketResponse onRequest(final WebSocketRequest webSocketRequest) {
        final WebSocketResponse webSocketResponse = new WebSocketResponse();
        if (! _isShuttingDown) {
            final Long webSocketId = _nextSocketId.getAndIncrement();
            webSocketResponse.setWebSocketId(webSocketId);
            webSocketResponse.upgradeToWebSocket();
        }
        return webSocketResponse;
    }

    @Override
    public void onNewWebSocket(final WebSocket webSocket) {
        if (_isShuttingDown) {
            webSocket.close();
            return;
        }

        _checkRpcConnection();

        final Long webSocketId = webSocket.getId();
        synchronized (MUTEX) {
            WEB_SOCKETS.put(webSocketId, webSocket);
        }

        // webSocket.setMessageReceivedCallback(new WebSocket.MessageReceivedCallback() {
        //     @Override
        //     public void onMessage(final String message) {
        //         // Nothing.
        //     }
        // });

        webSocket.setConnectionClosedCallback(new WebSocket.ConnectionClosedCallback() {
            @Override
            public void onClose(final int code, final String message) {
                Logger.log("WebSocket Closed: " + code + " " + message + " " + webSocket.toString());
                synchronized (MUTEX) {
                    WEB_SOCKETS.remove(webSocketId);
                }
            }
        });

        // webSocket.startListening();

        synchronized (MUTEX) {
            for (final Json transactionJson : BLOCK_HEADERS) {
                final Json messageJson = _wrapObject("BLOCK", transactionJson);
                final String message = messageJson.toString();
                webSocket.sendMessage(message);
            }

            for (final Json transactionJson : TRANSACTIONS) {
                final Json messageJson = _wrapObject("TRANSACTION", transactionJson);
                final String message = messageJson.toString();
                webSocket.sendMessage(message);
            }
        }
    }

    public void shutdown() {
        _isShuttingDown = true;

        _threadPool.stop();

        synchronized (_socketConnectionMutex) {
            if (_socketConnection != null) {
                _socketConnection.close();
            }
        }

        synchronized (MUTEX) {
            for (final WebSocket webSocket : WEB_SOCKETS.values()) {
                webSocket.close();
            }
            WEB_SOCKETS.clear();
        }
    }
}
