package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.chain.time.MedianBlockTimeWithBlocks;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorFactory;
import com.softwareverde.network.time.NetworkTime;

public class BlockValidatorFactory extends BlockHeaderValidatorFactory {
    final TransactionValidatorFactory _transactionValidatorFactory;

    public BlockValidatorFactory(final TransactionValidatorFactory transactionValidatorFactory, final NetworkTime networkTime, final MedianBlockTimeWithBlocks medianBlockTimeWithBlocks) {
        super(networkTime, medianBlockTimeWithBlocks);
        _transactionValidatorFactory = transactionValidatorFactory;
    }

    public BlockValidator newBlockValidator(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        final BlockHeaderValidatorFactory blockHeaderValidatorFactory = this;
        return new BlockValidator(databaseManagerFactory, blockHeaderValidatorFactory, _transactionValidatorFactory, _networkTime, _medianBlockTime);
    }

    public TransactionValidatorFactory getTransactionValidatorFactory() {
        return _transactionValidatorFactory;
    }
}
