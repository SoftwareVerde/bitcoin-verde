package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.network.time.NetworkTime;

public class TransactionValidatorFactory {
    protected final NetworkTime _networkTime;
    protected final MedianBlockTime _medianBlockTime;
    protected final MedianBlockTimeSet _medianBlockTimeSet;

    public TransactionValidatorFactory(final NetworkTime networkTime, final MedianBlockTime medianBlockTime, final MedianBlockTimeSet medianBlockTimeSet) {
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
        _medianBlockTimeSet = medianBlockTimeSet;
    }

    /**
     * Creates a new TransactionValidator with the provided DatabaseManager.
     *  UnspentTransactionOutputSet and BlockOutputs may be null.
     *  When UnspentTransactionOutputSet is null, the unspent TransactionOutputs are loaded on-demand via the DatabaseManager.
     *  If both UnspentTransactionOutputSet and BlockOutputs are null, then only outputs residing in the mempool are considered
     *  (which excludes previous blocks); this is usually undesired and either a UnspentTransactionOutputSet or BlockOutputs
     *  should be provided.
     */
    public TransactionValidator newTransactionValidator(final UnspentTransactionOutputSet unspentTransactionOutputSet, final BlockOutputs blockOutputs) {
        return new TransactionValidatorCore(unspentTransactionOutputSet, _medianBlockTimeSet, blockOutputs, _networkTime, _medianBlockTime);
    }
    public TransactionValidator newTransactionValidator(final UnspentTransactionOutputSet unspentTransactionOutputSet) {
        return new TransactionValidatorCore(unspentTransactionOutputSet, _medianBlockTimeSet, null, _networkTime, _medianBlockTime);
    }
}
