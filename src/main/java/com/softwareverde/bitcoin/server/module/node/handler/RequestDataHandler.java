package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.message.type.block.BlockMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.error.NotFoundResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHash;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.NodeConnection;

public class RequestDataHandler implements BitcoinNode.RequestDataCallback {
    protected final MysqlDatabaseConnectionFactory _connectionFactory;

    public RequestDataHandler(final MysqlDatabaseConnectionFactory connectionFactory) {
        _connectionFactory = connectionFactory;
    }

    @Override
    public void run(final List<DataHash> dataHashes, final NodeConnection nodeConnection) {
        try (final MysqlDatabaseConnection databaseConnection = _connectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

            final MutableList<DataHash> notFoundDataHashes = new MutableList<DataHash>();

            for (final DataHash dataHash : dataHashes) {
                switch (dataHash.getDataHashType()) {
                    case BLOCK: {
                        final Sha256Hash blockHash = dataHash.getObjectHash();
                        final BlockId blockId = blockDatabaseManager.getBlockIdFromHash(blockHash);

                        if (blockId == null) {
                            notFoundDataHashes.add(dataHash);
                        }
                        else {
                            final Block block = blockDatabaseManager.getBlock(blockId);
                            final BlockMessage blockMessage = new BlockMessage();
                            blockMessage.setBlock(block);
                            nodeConnection.queueMessage(blockMessage);
                        }
                    } break;

                    case TRANSACTION: {
                        final Sha256Hash transactionHash = dataHash.getObjectHash();
                        Logger.log("Unsupported RequestDataMessage Type: " + dataHash.getDataHashType() + " : " + transactionHash);
                    } break;

                    default: {
                        Logger.log("Unsupported RequestDataMessage Type: " + dataHash.getDataHashType());
                    } break;
                }
            }

            if (! notFoundDataHashes.isEmpty()) {
                final NotFoundResponseMessage notFoundResponseMessage = new NotFoundResponseMessage();
                for (final DataHash dataHash : notFoundDataHashes) {
                    notFoundResponseMessage.addItem(dataHash);
                }
                nodeConnection.queueMessage(notFoundResponseMessage);
            }
        }
        catch (final DatabaseException exception) { Logger.log(exception); }
    }
}
