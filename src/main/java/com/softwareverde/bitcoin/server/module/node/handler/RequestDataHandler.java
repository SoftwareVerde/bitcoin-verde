package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.message.type.query.response.block.BlockMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.error.NotFoundResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.NodeConnection;

public class RequestDataHandler implements BitcoinNode.RequestDataCallback {
    public static final BitcoinNode.RequestDataCallback IGNORE_REQUESTS_HANDLER = new BitcoinNode.RequestDataCallback() {
        @Override
        public void run(final List<InventoryItem> dataHashes, final NodeConnection nodeConnection) { }
    };

    protected final MysqlDatabaseConnectionFactory _connectionFactory;

    public RequestDataHandler(final MysqlDatabaseConnectionFactory connectionFactory) {
        _connectionFactory = connectionFactory;
    }

    @Override
    public void run(final List<InventoryItem> dataHashes, final NodeConnection nodeConnection) {
        try (final MysqlDatabaseConnection databaseConnection = _connectionFactory.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

            final MutableList<InventoryItem> notFoundDataHashes = new MutableList<InventoryItem>();

            for (final InventoryItem inventoryItem : dataHashes) {
                switch (inventoryItem.getItemType()) {
                    case BLOCK: {
                        final Sha256Hash blockHash = inventoryItem.getItemHash();
                        final BlockId blockId = blockDatabaseManager.getBlockIdFromHash(blockHash);

                        if (blockId == null) {
                            notFoundDataHashes.add(inventoryItem);
                        }
                        else {
                            final Block block = blockDatabaseManager.getBlock(blockId);
                            final BlockMessage blockMessage = new BlockMessage();
                            blockMessage.setBlock(block);
                            nodeConnection.queueMessage(blockMessage);
                        }
                    } break;

                    case TRANSACTION: {
                        final Sha256Hash transactionHash = inventoryItem.getItemHash();
                        Logger.log("Unsupported RequestDataMessage Type: " + inventoryItem.getItemType() + " : " + transactionHash);
                    } break;

                    default: {
                        Logger.log("Unsupported RequestDataMessage Type: " + inventoryItem.getItemType());
                    } break;
                }
            }

            if (! notFoundDataHashes.isEmpty()) {
                final NotFoundResponseMessage notFoundResponseMessage = new NotFoundResponseMessage();
                for (final InventoryItem inventoryItem : notFoundDataHashes) {
                    notFoundResponseMessage.addItem(inventoryItem);
                }
                nodeConnection.queueMessage(notFoundResponseMessage);
            }
        }
        catch (final DatabaseException exception) { Logger.log(exception); }
    }
}
