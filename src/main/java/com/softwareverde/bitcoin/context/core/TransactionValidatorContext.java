package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.network.time.VolatileNetworkTime;

public class TransactionValidatorContext implements TransactionValidator.Context {
    protected final UpgradeSchedule _upgradeSchedule;
    protected final TransactionInflaters _transactionInflaters;
    protected final VolatileNetworkTime _networkTime;
    protected final MedianBlockTimeContext _medianBlockTimeContext;
    protected final UnspentTransactionOutputContext _unspentTransactionOutputContext;

    public TransactionValidatorContext(final TransactionInflaters transactionInflaters, final VolatileNetworkTime networkTime, final MedianBlockTimeContext medianBlockTimeContext, final UnspentTransactionOutputContext unspentTransactionOutputContext, final UpgradeSchedule upgradeSchedule) {
        _upgradeSchedule = upgradeSchedule;
        _transactionInflaters = transactionInflaters;
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

    @Override
    public TransactionInflater getTransactionInflater() {
        return _transactionInflaters.getTransactionInflater();
    }

    @Override
    public TransactionDeflater getTransactionDeflater() {
        return _transactionInflaters.getTransactionDeflater();
    }

    @Override
    public UpgradeSchedule getUpgradeSchedule() {
        return _upgradeSchedule;
    }
}
