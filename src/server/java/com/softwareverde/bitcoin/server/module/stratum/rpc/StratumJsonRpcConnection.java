package com.softwareverde.bitcoin.server.module.stratum.rpc;

import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;

public class StratumJsonRpcConnection implements AutoCloseable {
    public static final Long RPC_DURATION_TIMEOUT_MS = 30000L;

    protected final JsonSocket _jsonSocket;
    protected final Object _newMessageNotifier = new Object();

    protected final Runnable _onNewMessageCallback = new Runnable() {
        @Override
        public void run() {
            synchronized (_newMessageNotifier) {
                _newMessageNotifier.notifyAll();
            }
        }
    };

    protected Boolean _isUpgradedToHook = false;

    protected Json _executeJsonRequest(final Json rpcRequestJson) {
        if (_isUpgradedToHook) { throw new RuntimeException("Attempted to invoke Json request to hook-upgraded socket."); }

        _jsonSocket.write(new JsonProtocolMessage(rpcRequestJson));
        _jsonSocket.beginListening();

        JsonProtocolMessage jsonProtocolMessage;
        synchronized (_newMessageNotifier) {
            jsonProtocolMessage = _jsonSocket.popMessage();
            if (jsonProtocolMessage == null) {
                try {
                    _newMessageNotifier.wait(RPC_DURATION_TIMEOUT_MS);
                }
                catch (final InterruptedException exception) { }

                jsonProtocolMessage = _jsonSocket.popMessage();
            }
        }

        return (jsonProtocolMessage != null ? jsonProtocolMessage.getMessage() : null);
    }

    public StratumJsonRpcConnection(final String hostname, final Integer port, final ThreadPool threadPool) {
        java.net.Socket socket = null;
        try {
            socket = new java.net.Socket(hostname, port);
        }
        catch (final Exception exception) {
            Logger.warn(exception);
        }

        _jsonSocket = ((socket != null) ? new JsonSocket(socket, threadPool) : null);

        if (_jsonSocket != null) {
            _jsonSocket.setMessageReceivedCallback(_onNewMessageCallback);
        }
    }

    public StratumJsonRpcConnection(final java.net.Socket socket, final ThreadPool threadPool) {
        _jsonSocket = ((socket != null) ? new JsonSocket(socket, threadPool) : null);

        if (_jsonSocket != null) {
            _jsonSocket.setMessageReceivedCallback(_onNewMessageCallback);
        }
    }

    public Json getPrototypeBlock(final Boolean returnRawData) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("rawFormat", (returnRawData ? 1 : 0));

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "PROTOTYPE_BLOCK");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public JsonSocket getJsonSocket() {
        return _jsonSocket;
    }

    @Override
    public void close() {
        _jsonSocket.close();
    }
}
