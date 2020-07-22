package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.security.hash.sha256.Sha256Hash;

public class TransactionValidatorContext implements TransactionValidator.Context {
    protected final VolatileNetworkTime _networkTime;
    protected final MedianBlockTimeContext _medianBlockTimeContext;
    protected final UnspentTransactionOutputContext _unspentTransactionOutputContext;

    public TransactionValidatorContext(final VolatileNetworkTime networkTime, final MedianBlockTimeContext medianBlockTimeContext, final UnspentTransactionOutputContext unspentTransactionOutputContext) {
        _networkTime = networkTime;
        _medianBlockTimeContext = medianBlockTimeContext;
        _unspentTransactionOutputContext = unspentTransactionOutputContext;
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
        return _medianBlockTimeContext.getMedianBlockTime(blockHeight);
    }

    @Override
    public VolatileNetworkTime getNetworkTime() {
        return _networkTime;
    }

    @Override
    public TransactionOutput getTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _unspentTransactionOutputContext.getTransactionOutput(transactionOutputIdentifier);
    }

    @Override
    public Long getBlockHeight(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _unspentTransactionOutputContext.getBlockHeight(transactionOutputIdentifier);
    }

    @Override
    public Sha256Hash getBlockHash(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _unspentTransactionOutputContext.getBlockHash(transactionOutputIdentifier);
    }

    @Override
    public Boolean isCoinbaseTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _unspentTransactionOutputContext.isCoinbaseTransactionOutput(transactionOutputIdentifier);
    }
}
