package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.network.socket.JsonSocketServer;

public class JsonRpcSocketServerHandler implements JsonSocketServer.SocketConnectedCallback {
    protected final Environment _environment;

    public JsonRpcSocketServerHandler(final Environment environment) {
        _environment = environment;
    }

    protected void _queryBlockHeight(final Json response) {
        final EmbeddedMysqlDatabase database = _environment.getDatabase();
        try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
            final Long blockHeight;
            {
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
                final Sha256Hash lastKnownHash = blockDatabaseManager.getMostRecentBlockHash();
                final BlockId blockId = blockDatabaseManager.getBlockIdFromHash(lastKnownHash);
                blockHeight = blockDatabaseManager.getBlockHeightForBlockId(blockId);
            }

            response.put("was_success", 1);
            response.put("block_height", blockHeight);
        }
        catch (final Exception exception) {
            response.put("was_success", 0);
            response.put("error_message", exception.getMessage());
        }
    }

    @Override
    public void run(final JsonSocket socketConnection) {
        Logger.log("New Connection: " + socketConnection);
        socketConnection.setMessageReceivedCallback(new Runnable() {
            @Override
            public void run() {
                final Json message = socketConnection.popMessage().getMessage();
                Logger.log("Message received: "+ message);

                final String method = message.getString("method");
                final String query = message.getString("query");

                final Json response = new Json();
                response.put("method", "RESPONSE");
                response.put("was_success", 0);
                response.put("error_message", null);

                switch (method.toUpperCase()) {
                    case "GET": {

                        switch (query.toUpperCase()) {

                            case "BLOCK-HEIGHT": {
                                _queryBlockHeight(response);
                            } break;

                            default: {
                                response.put("error_message", "Invalid command: " + method + "/" + query);
                            } break;
                        }

                    } break;

                    default: {
                        response.put("error_message", "Invalid method: " + method);
                    } break;
                }

                Logger.log("Writing: " + response.toString());
                socketConnection.write(new JsonProtocolMessage(response));
            }
        });
        socketConnection.beginListening();
    }
}
