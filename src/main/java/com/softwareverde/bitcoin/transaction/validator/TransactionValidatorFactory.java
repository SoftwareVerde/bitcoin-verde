package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.network.time.NetworkTime;

public class TransactionValidatorFactory {
    final NetworkTime _networkTime;
    final MedianBlockTime _medianBlockTime;

    public TransactionValidatorFactory(final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
    }

    /**
     * Creates a new TransactionValidator with the provided DatabaseManager.
     *  UnspentTransactionOutputSet and BlockOutputs may be null.
     *  When UnspentTransactionOutputSet is null, the unspent TransactionOutputs are loaded on-demand via the DatabaseManager.
     *  If both UnspentTransactionOutputSet and BlockOutputs are null, then only outputs residing in the mempool are considered
     *  (which excludes previous blocks); this is usually undesired and either a UnspentTransactionOutputSet or BlockOutputs
     *  should be provided.
     */
    public TransactionValidator newTransactionValidator(final FullNodeDatabaseManager databaseManager, final UnspentTransactionOutputSet unspentTransactionOutputSet, final BlockOutputs blockOutputs) {
        return new TransactionValidatorCore(databaseManager, unspentTransactionOutputSet, blockOutputs, _networkTime, _medianBlockTime);
    }
}
