package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.network.time.NetworkTime;

public class TransactionValidatorFactory {
    final UnspentTransactionOutputSet _unspentTransactionOutputSet;
    final NetworkTime _networkTime;
    final MedianBlockTime _medianBlockTime;

    public TransactionValidatorFactory(final UnspentTransactionOutputSet unspentTransactionOutputSet, final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        _unspentTransactionOutputSet = unspentTransactionOutputSet;
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
    }

    public TransactionValidator newTransactionValidator(final FullNodeDatabaseManager databaseManager, final BlockOutputs blockOutputs) {
        return new TransactionValidatorCore(databaseManager, _unspentTransactionOutputSet, blockOutputs, _networkTime, _medianBlockTime);
    }
}
