package com.softwareverde.bitcoin.server.module.stratum.rpc;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.module.stratum.api.endpoint.StratumDataHandler;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.json.Json;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.network.socket.JsonSocketServer;

public class StratumRpcServer {
    protected static final String ERROR_MESSAGE_KEY = "errorMessage";
    protected static final String WAS_SUCCESS_KEY = "wasSuccess";

    protected final JsonSocketServer _jsonRpcSocketServer;
    protected final ThreadPool _rpcThreadPool;
    protected final StratumDataHandler _stratumDataHandler;
    protected final BlockInflaters _blockInflaters;

    // Requires GET:    [rawFormat=0]
    // Requires POST:
    protected void _getPrototypeBlock(final Json parameters, final Json response) {
        final StratumDataHandler stratumDataHandler = _stratumDataHandler;
        if (stratumDataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Block prototypeBlock = stratumDataHandler.getPrototypeBlock();

        final Boolean shouldReturnRawBlockData = parameters.getBoolean("rawFormat");

        if (shouldReturnRawBlockData) {
            final BlockDeflater blockDeflater = _blockInflaters.getBlockDeflater();
            final ByteArray blockData = blockDeflater.toBytes(prototypeBlock);
            response.put("block", blockData);
        }
        else {
            final Json blockJson = prototypeBlock.toJson();
            response.put("block", blockJson);
        }

        response.put(WAS_SUCCESS_KEY, 1);
    }

    public StratumRpcServer(final StratumProperties stratumProperties, final StratumDataHandler stratumDataHandler, final ThreadPool rpcThreadPool) {
        this(
            stratumProperties,
            stratumDataHandler,
            rpcThreadPool,
            new CoreInflater()
        );
    }

    public StratumRpcServer(final StratumProperties stratumProperties, final StratumDataHandler stratumDataHandler, final ThreadPool rpcThreadPool, final BlockInflaters blockInflaters) {
        _rpcThreadPool = rpcThreadPool;
        _stratumDataHandler = stratumDataHandler;
        _blockInflaters = blockInflaters;

        final Integer rpcPort = stratumProperties.getRpcPort();
        if (rpcPort > 0) {
            final JsonSocketServer jsonRpcSocketServer = new JsonSocketServer(rpcPort, _rpcThreadPool);
            jsonRpcSocketServer.setSocketConnectedCallback(new JsonSocketServer.SocketConnectedCallback() {
                @Override
                public void run(final JsonSocket socketConnection) {
                    socketConnection.setMessageReceivedCallback(new Runnable() {
                        @Override
                        public void run() {
                            final JsonProtocolMessage protocolMessage = socketConnection.popMessage();
                            final Json message = protocolMessage.getMessage();

                            final String method = message.getString("method");
                            final String query = message.getString("query");

                            final Json response = new Json();
                            response.put(WAS_SUCCESS_KEY, 0);
                            response.put(ERROR_MESSAGE_KEY, null);

                            final Json parameters = message.get("parameters");
                            Boolean closeConnection = true;

                            switch (method.toUpperCase()) {
                                case "GET": {
                                    switch (query.toUpperCase()) {
                                        case "PROTOTYPE_BLOCK": {
                                            _getPrototypeBlock(parameters, response);
                                        } break;

                                        default: {
                                            response.put(ERROR_MESSAGE_KEY, "Invalid " + method + " query: " + query);
                                        } break;
                                    }
                                } break;

                                case "POST": {
                                    switch (query.toUpperCase()) {
                                        default: {
                                            response.put(ERROR_MESSAGE_KEY, "Invalid " + method + " query: " + query);
                                        } break;
                                    }
                                } break;

                                default: {
                                    response.put(ERROR_MESSAGE_KEY, "Invalid command: " + method.toUpperCase());
                                } break;
                            }

                            socketConnection.write(new JsonProtocolMessage(response));

                            if (closeConnection) {
                                socketConnection.close();
                            }
                        }
                    });
                    socketConnection.beginListening();
                }
            });
            _jsonRpcSocketServer = jsonRpcSocketServer;
        }
        else {
            _jsonRpcSocketServer = null;
        }
    }

    public void start() {
        if (_jsonRpcSocketServer != null) {
            _jsonRpcSocketServer.start();
        }
    }

    public void stop() {
        if (_jsonRpcSocketServer != null) {
            _jsonRpcSocketServer.stop();
        }
    }
}
