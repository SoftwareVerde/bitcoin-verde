package com.softwareverde.bitcoin.server.module.node.database;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.properties.PropertiesStore;
import com.softwareverde.database.DatabaseException;

public interface DatabaseManager extends AutoCloseable {
    DatabaseConnection getDatabaseConnection();
    PropertiesStore getPropertiesStore();

    BitcoinNodeDatabaseManager getNodeDatabaseManager();
    BlockchainDatabaseManager getBlockchainDatabaseManager();
    BlockDatabaseManager getBlockDatabaseManager();
    BlockHeaderDatabaseManager getBlockHeaderDatabaseManager();
    TransactionDatabaseManager getTransactionDatabaseManager();

    Integer getMaxQueryBatchSize();

    void startTransaction() throws DatabaseException;
    void commitTransaction() throws DatabaseException;
    void rollbackTransaction() throws DatabaseException;

    @Override
    void close() throws DatabaseException;
}
