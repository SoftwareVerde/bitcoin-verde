package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.network.socket.JsonSocketServer;
import com.softwareverde.util.Container;
import com.softwareverde.util.DateUtil;
import com.softwareverde.util.type.time.SystemTime;

public class JsonRpcSocketServerHandler implements JsonSocketServer.SocketConnectedCallback {
    public interface ShutdownHandler {
        void shutdown();
    }

    public static class StatisticsContainer {
        public Container<Float> averageBlocksPerSecond;
        public Container<Float> averageTransactionsPerSecond;
    }

    protected final Environment _environment;
    protected final ShutdownHandler _shutdownHandler;
    protected final Container<Float> _averageBlocksPerSecond;
    protected final Container<Float> _averageTransactionsPerSecond;

    public JsonRpcSocketServerHandler(final Environment environment, final ShutdownHandler shutdownHandler, final StatisticsContainer statisticsContainer) {
        _environment = environment;
        _shutdownHandler = shutdownHandler;

        _averageBlocksPerSecond = statisticsContainer.averageBlocksPerSecond;
        _averageTransactionsPerSecond = statisticsContainer.averageTransactionsPerSecond;
    }

    protected Long _calculateBlockHeight(final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final Sha256Hash lastKnownHash = blockDatabaseManager.getHeadBlockHash();
        final BlockId blockId = blockDatabaseManager.getBlockIdFromHash(lastKnownHash);
        return blockDatabaseManager.getBlockHeightForBlockId(blockId);
    }

    protected void _queryBlockHeight(final Json response) {
        final EmbeddedMysqlDatabase database = _environment.getDatabase();
        try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
            final Long blockHeight = _calculateBlockHeight(databaseConnection);

            response.put("was_success", 1);
            response.put("block_height", blockHeight);
        }
        catch (final Exception exception) {
            response.put("was_success", 0);
            response.put("error_message", exception.getMessage());
        }
    }

    protected void _queryStatus(final Json response) {
        final SystemTime systemTime = new SystemTime();

        final EmbeddedMysqlDatabase database = _environment.getDatabase();
        try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {

            final Long blockTimestampInSeconds;
            final Long secondsBehind;
            {
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
                final Sha256Hash lastKnownHash = blockDatabaseManager.getHeadBlockHash();
                final BlockId blockId = blockDatabaseManager.getBlockIdFromHash(lastKnownHash);
                final BlockHeader blockHeader = blockDatabaseManager.getBlockHeader(blockId);
                blockTimestampInSeconds = blockHeader.getTimestamp();

                secondsBehind = (systemTime.getCurrentTimeInSeconds() - blockTimestampInSeconds);
            }

            final Integer secondsInAnHour = (60 * 60);
            final Boolean isSyncing = (secondsBehind > secondsInAnHour);

            response.put("was_success", 1);
            response.put("status", (isSyncing ? "SYNCHRONIZING" : "ONLINE"));

            final Long blockHeight = _calculateBlockHeight(databaseConnection);

            final Json statisticsJson = new Json();
            statisticsJson.put("blocksPerSecond", _averageBlocksPerSecond.value);
            statisticsJson.put("transactionsPerSecond", _averageTransactionsPerSecond.value);
            statisticsJson.put("blockHeight", blockHeight);
            statisticsJson.put("blockDate", DateUtil.timestampToDatetimeString(blockTimestampInSeconds * 1000));

            response.put("statistics", statisticsJson);
        }
        catch (final Exception exception) {
            response.put("was_success", 0);
            response.put("error_message", exception.getMessage());
        }
    }

    protected void _shutdown(final Json response) {
        _shutdownHandler.shutdown();
        response.put("was_success", 1);
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

                            case "STATUS": {
                                _queryStatus(response);
                            } break;

                            default: {
                                response.put("error_message", "Invalid command: " + method + "/" + query);
                            } break;
                        }
                    } break;

                    case "POST": {
                        switch (query.toUpperCase()) {
                            case "SHUTDOWN": {
                                _shutdown(response);
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
