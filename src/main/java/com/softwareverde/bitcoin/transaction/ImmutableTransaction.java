package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.transaction.input.ImmutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.MutableLockTime;
import com.softwareverde.bitcoin.transaction.output.ImmutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.stack.ScriptSignature;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.util.ByteUtil;

import java.util.ArrayList;
import java.util.List;

public class ImmutableTransaction implements Transaction {
    protected final Transaction _transaction;

    public ImmutableTransaction() {
        _transaction = new MutableTransaction();
    }

    public ImmutableTransaction(final Transaction transaction) {
        if (transaction instanceof ImmutableTransaction) {
            _transaction = transaction;
            return;
        }

        final MutableTransaction mutableTransaction = new MutableTransaction();
        mutableTransaction.setVersion(transaction.getVersion());
        mutableTransaction.setHasWitnessData(transaction.hasWitnessData());
        mutableTransaction.setLockTime(new MutableLockTime(transaction.getLockTime()));
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            mutableTransaction.addTransactionInput(new ImmutableTransactionInput(transactionInput));
        }
        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            mutableTransaction.addTransactionOutput(new ImmutableTransactionOutput(transactionOutput));
        }
        _transaction = mutableTransaction;
    }

    @Override
    public byte[] getBytesForSigning(final Integer inputIndexToBeSigned, final TransactionOutput transactionOutputBeingSpent, final ScriptSignature.HashType hashType) {
        return _transaction.getBytesForSigning(inputIndexToBeSigned, transactionOutputBeingSpent, hashType);
    }

    @Override
    public Hash calculateSha256HashForSigning(final Integer inputIndexToBeSigned, final TransactionOutput transactionOutputBeingSpent, final ScriptSignature.HashType hashType) {
        return new ImmutableHash(_transaction.calculateSha256HashForSigning(inputIndexToBeSigned, transactionOutputBeingSpent, hashType));
    }

    @Override
    public Hash calculateSha256Hash() {
        return new ImmutableHash(_transaction.calculateSha256Hash());
    }

    @Override
    public Integer getVersion() { return _transaction.getVersion(); }

    @Override
    public Boolean hasWitnessData() { return _transaction.hasWitnessData(); }

    @Override
    public final List<TransactionInput> getTransactionInputs() {
        final List<TransactionInput> protectedTransactionInputs = _transaction.getTransactionInputs();

        final List<TransactionInput> transactionInputs = new ArrayList<TransactionInput>(protectedTransactionInputs.size());
        for (final TransactionInput transactionInput : protectedTransactionInputs) {
            transactionInputs.add(new ImmutableTransactionInput(transactionInput));
        }
        return transactionInputs;
    }

    @Override
    public final List<TransactionOutput> getTransactionOutputs() {
        final List<TransactionOutput> protectedTransactionOutputs = _transaction.getTransactionOutputs();

        final List<TransactionOutput> transactionOutputs = new ArrayList<TransactionOutput>(protectedTransactionOutputs.size());
        for (final TransactionOutput transactionOutput : protectedTransactionOutputs) {
            transactionOutputs.add(new ImmutableTransactionOutput(transactionOutput));
        }
        return transactionOutputs;
    }

    @Override
    public LockTime getLockTime() { return new ImmutableLockTime(_transaction.getLockTime()); }

    @Override
    public Long getTotalOutputValue() {
        return _transaction.getTotalOutputValue();
    }

    @Override
    public Integer getByteCount() {
        return _transaction.getByteCount();
    }

    @Override
    public byte[] getBytes() {
        return ByteUtil.copyBytes(_transaction.getBytes());
    }
}
