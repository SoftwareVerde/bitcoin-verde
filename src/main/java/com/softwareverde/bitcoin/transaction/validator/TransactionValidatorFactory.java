package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.network.time.NetworkTime;

public class TransactionValidatorFactory {
    public TransactionValidator newTransactionValidator(final FullNodeDatabaseManager databaseManager, final UnspentTransactionOutputSet unspentTransactionOutputSet, final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        return new TransactionValidatorCore(databaseManager, unspentTransactionOutputSet, networkTime, medianBlockTime);
    }
}
