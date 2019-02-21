package com.softwareverde.bitcoin.server.module.stratum.rpc;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.json.Json;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.network.socket.JsonSocketServer;

public class StratumRpcHandler implements JsonSocketServer.SocketConnectedCallback {
    protected static final String ERROR_MESSAGE_KEY = "errorMessage";
    protected static final String WAS_SUCCESS_KEY = "wasSuccess";

    public interface StatusHandler {
        Block getPrototypeBlock();
    }

    protected final StatusHandler _statusHandler;

    // Requires GET:    [rawFormat=0]
    // Requires POST:
    protected void _getPrototypeBlock(final Json parameters, final Json response) {
        final StatusHandler statusHandler = _statusHandler;
        if (statusHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Block prototypeBlock = statusHandler.getPrototypeBlock();

        final Boolean shouldReturnRawBlockData = parameters.getBoolean("rawFormat");

        if (shouldReturnRawBlockData) {
            final BlockDeflater blockDeflater = new BlockDeflater();
            final ByteArray blockData = blockDeflater.toBytes(prototypeBlock);
            response.put("block", blockData);
        }
        else {
            final Json blockJson = prototypeBlock.toJson();
            response.put("block", blockJson);
        }

        response.put(WAS_SUCCESS_KEY, 1);
    }

    public StratumRpcHandler(final StatusHandler statusHandler) {
        _statusHandler = statusHandler;
    }

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
}
