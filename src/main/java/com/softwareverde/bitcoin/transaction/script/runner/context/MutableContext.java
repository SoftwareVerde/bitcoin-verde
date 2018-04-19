package com.softwareverde.bitcoin.transaction.script.runner.context;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.constable.Const;

public class MutableContext implements Context, Const {
    protected Long _blockHeight;
    protected Transaction _transaction;

    protected Integer _transactionInputIndex;
    protected TransactionInput _transactionInput;
    protected TransactionOutput _transactionOutput;

    protected Integer _currentLockingScriptIndex = 0;
    protected Integer _lockingScriptLastCodeSeparatorIndex = 0;

    public MutableContext() { }

    public MutableContext(final Context context) {
        _blockHeight = context.getBlockHeight();
        _transaction = context.getTransaction();
        _transactionInputIndex = context.getTransactionInputIndex();
        _transactionInput = context.getTransactionInput();
        _transactionOutput = context.getTransactionOutput();

        _currentLockingScriptIndex = context.getCurrentLockingScriptIndex();
        _lockingScriptLastCodeSeparatorIndex = context.getLockingScriptLastCodeSeparatorIndex();
    }

    public void setBlockHeight(final Long blockHeight) {
        _blockHeight = blockHeight;
    }

    public void setTransaction(final Transaction transaction) {
        _transaction = transaction;
    }

    public void setTransactionInputIndex(final Integer transactionInputIndex) {
        _transactionInputIndex = transactionInputIndex;
    }

    public void setTransactionInput(final TransactionInput transactionInput) {
        _transactionInput = transactionInput;
    }

    public void setTransactionOutput(final TransactionOutput transactionOutput) {
        _transactionOutput = transactionOutput;
    }

    public void incrementCurrentLockingScriptIndex() {
        _currentLockingScriptIndex += 1;
    }

    public void setLockingScriptLastCodeSeparatorIndex(final Integer codeSeparatorIndex) {
        _lockingScriptLastCodeSeparatorIndex = codeSeparatorIndex;
    }

    @Override
    public Long getBlockHeight() {
        return _blockHeight;
    }

    @Override
    public TransactionInput getTransactionInput() {
        return _transactionInput;
    }

    @Override
    public TransactionOutput getTransactionOutput() {
        return _transactionOutput;
    }

    @Override
    public Transaction getTransaction() {
        return _transaction;
    }

    @Override
    public Integer getTransactionInputIndex() {
        return _transactionInputIndex;
    }

    @Override
    public Integer getCurrentLockingScriptIndex() {
        return _currentLockingScriptIndex;
    }

    @Override
    public Integer getLockingScriptLastCodeSeparatorIndex() {
        return _lockingScriptLastCodeSeparatorIndex;
    }

    @Override
    public ImmutableContext asConst() {
        return new ImmutableContext(this);
    }
}
