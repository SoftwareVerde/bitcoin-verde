package com.softwareverde.bitcoin.server.module.node.database;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.database.DatabaseException;

public interface DatabaseManager extends AutoCloseable {
    DatabaseConnection getDatabaseConnection();

    BitcoinNodeDatabaseManager getNodeDatabaseManager();
    BlockchainDatabaseManager getBlockchainDatabaseManager();
    BlockDatabaseManager getBlockDatabaseManager();
    BlockHeaderDatabaseManager getBlockHeaderDatabaseManager();
    PendingBlockDatabaseManager getPendingBlockDatabaseManager();
    TransactionDatabaseManager getTransactionDatabaseManager();

    @Override
    void close() throws DatabaseException;
}
