package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.module.node.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.message.type.query.response.block.BlockMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.error.NotFoundResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.transaction.TransactionMessage;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.NodeConnection;
import com.softwareverde.util.timer.NanoTimer;

public class RequestDataHandler implements BitcoinNode.RequestDataCallback {
    public static final BitcoinNode.RequestDataCallback IGNORE_REQUESTS_HANDLER = new BitcoinNode.RequestDataCallback() {
        @Override
        public void run(final List<InventoryItem> dataHashes, final NodeConnection nodeConnection) { }
    };

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;

    public RequestDataHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
    }

    @Override
    public void run(final List<InventoryItem> dataHashes, final NodeConnection nodeConnection) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);

            final MutableList<InventoryItem> notFoundDataHashes = new MutableList<InventoryItem>();

            for (final InventoryItem inventoryItem : dataHashes) {
                switch (inventoryItem.getItemType()) {
                    case BLOCK: {
                        final NanoTimer getBlockDataTimer = new NanoTimer();
                        getBlockDataTimer.start();
                        final Sha256Hash blockHash = inventoryItem.getItemHash();
                        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);

                        if (blockId == null) {
                            notFoundDataHashes.add(inventoryItem);
                            continue;
                        }

                        final Block block = blockDatabaseManager.getBlock(blockId);
                        if (block == null) {
                            Logger.log("Error inflating Block: " + blockHash);
                            notFoundDataHashes.add(inventoryItem);
                            continue;
                        }

                        final BlockMessage blockMessage = new BlockMessage();
                        blockMessage.setBlock(block);
                        nodeConnection.queueMessage(blockMessage);
                        getBlockDataTimer.stop();
                        Logger.log("GetBlockData: " + blockHash + " "  + nodeConnection.toString() + " " + getBlockDataTimer.getMillisecondsElapsed() + "ms");
                    } break;

                    case TRANSACTION: {
                        final NanoTimer getTransactionTimer = new NanoTimer();
                        getTransactionTimer.start();

                        final Sha256Hash transactionHash = inventoryItem.getItemHash();
                        final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                        if (transactionId == null) {
                            notFoundDataHashes.add(inventoryItem);
                            continue;
                        }

                        final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                        if (transaction == null) {
                            Logger.log("Error inflating Transaction: " + transactionHash);
                            notFoundDataHashes.add(inventoryItem);
                            continue;
                        }

                        final TransactionMessage transactionMessage = new TransactionMessage();
                        transactionMessage.setTransaction(transaction);
                        nodeConnection.queueMessage(transactionMessage);

                        getTransactionTimer.stop();
                        Logger.log("GetTransactionData: " + transactionHash + " to " + nodeConnection.toString() + " " + getTransactionTimer.getMillisecondsElapsed() + "ms");
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
