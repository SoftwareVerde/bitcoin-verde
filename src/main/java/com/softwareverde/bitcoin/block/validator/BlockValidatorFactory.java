package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.chain.time.MedianBlockTimeWithBlocks;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorFactory;
import com.softwareverde.network.time.NetworkTime;

public interface BlockValidatorFactory {
    BlockValidator newBlockValidator(FullNodeDatabaseManagerFactory databaseManagerFactory, TransactionValidatorFactory transactionValidatorFactory, NetworkTime networkTime, MedianBlockTimeWithBlocks medianBlockTime);
    BlockHeaderValidator newBlockHeaderValidator(DatabaseManager databaseManager, NetworkTime networkTime, MedianBlockTimeWithBlocks medianBlockTime);
}
