package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.security.hash.sha256.Sha256Hash;

public class TransactionValidatorContext implements MedianBlockTimeContext, NetworkTimeContext, UnspentTransactionOutputContext {
    protected final NetworkTime _networkTime;
    protected final MedianBlockTime _medianBlockTime;
    protected final UnspentTransactionOutputContext _unspentTransactionOutputContext;

    public <Context extends NetworkTimeContext & MedianBlockTimeContext> TransactionValidatorContext(final NetworkTime networkTime, final MedianBlockTime medianBlockTime, final UnspentTransactionOutputContext unspentTransactionOutputContext) {
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
        _unspentTransactionOutputContext = unspentTransactionOutputContext;
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
        return _medianBlockTime;
    }

    @Override
    public NetworkTime getNetworkTime() {
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
