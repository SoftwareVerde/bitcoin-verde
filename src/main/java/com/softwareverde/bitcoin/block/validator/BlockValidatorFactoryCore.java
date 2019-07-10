package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.chain.time.MedianBlockTimeWithBlocks;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorFactory;
import com.softwareverde.network.time.NetworkTime;

public class BlockValidatorFactoryCore implements BlockValidatorFactory {
    @Override
    public BlockValidator newBlockValidator(final FullNodeDatabaseManagerFactory databaseManagerFactory, final TransactionValidatorFactory transactionValidatorFactory, final NetworkTime networkTime, final MedianBlockTimeWithBlocks medianBlockTime) {
        return new BlockValidator(databaseManagerFactory, this, transactionValidatorFactory, networkTime, medianBlockTime);
    }

    @Override
    public BlockHeaderValidator newBlockHeaderValidator(final FullNodeDatabaseManager databaseManager, final NetworkTime networkTime, final MedianBlockTimeWithBlocks medianBlockTime) {
        return new BlockHeaderValidator(databaseManager, networkTime, medianBlockTime);
    }
}
