package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.transaction.input.ImmutableTransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.output.ImmutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;

public class ImmutableTransaction implements Transaction, Const {
    protected final ImmutableHash _hash;
    protected final Integer _version;
    protected final Boolean _hasWitnessData;
    protected final ImmutableList<ImmutableTransactionInput> _transactionInputs;
    protected final ImmutableList<ImmutableTransactionOutput> _transactionOutputs;
    protected final ImmutableLockTime _lockTime;

    public ImmutableTransaction(final Transaction transaction) {
        _hash = transaction.getHash().asConst();
        _version = transaction.getVersion();
        _hasWitnessData = transaction.hasWitnessData();
        _lockTime = transaction.getLockTime().asConst();

        _transactionInputs = ImmutableListBuilder.newConstListOfConstItems(transaction.getTransactionInputs());
        _transactionOutputs = ImmutableListBuilder.newConstListOfConstItems(transaction.getTransactionOutputs());
    }

    @Override
    public Hash getHash() {
        return _hash;
    }

    @Override
    public Integer getVersion() { return _version; }

    @Override
    public Boolean hasWitnessData() { return _hasWitnessData; }

    @Override
    public final List<ImmutableTransactionInput> getTransactionInputs() {
        return _transactionInputs;
    }

    @Override
    public final List<ImmutableTransactionOutput> getTransactionOutputs() {
        return _transactionOutputs;
    }

    @Override
    public ImmutableLockTime getLockTime() { return _lockTime; }

    @Override
    public Long getTotalOutputValue() {
        long totalValue = 0L;

        for (final TransactionOutput transactionOutput : _transactionOutputs) {
            totalValue += transactionOutput.getAmount();
        }

        return totalValue;
    }

    @Override
    public ImmutableTransaction asConst() {
        return this;
    }
}
