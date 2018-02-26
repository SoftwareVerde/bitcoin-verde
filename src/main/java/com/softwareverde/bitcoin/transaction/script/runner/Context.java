package com.softwareverde.bitcoin.transaction.script.runner;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;

public class Context {
    protected Transaction _transaction;
    protected Integer _transactionInputIndex;

    protected TransactionInput _transactionInput;
    protected TransactionOutput _transactionOutput;

    public void setTransaction(final Transaction transaction) {
        _transaction = transaction;
    }

    public void setTransactionInput(final TransactionInput transactionInput) {
        _transactionInput = transactionInput;
    }

    public void setTransactionOutput(final TransactionOutput transactionOutput) {
        _transactionOutput = transactionOutput;
    }

    public TransactionInput getTransactionInput() {
        return _transactionInput;
    }

    public TransactionOutput getTransactionOutput() {
        return _transactionOutput;
    }

    public void setTransactionInputIndex(final Integer transactionInputIndex) {
        _transactionInputIndex = transactionInputIndex;
    }

    public Transaction getTransaction() {
        return _transaction;
    }

    public Integer getTransactionInputIndex() {
        return _transactionInputIndex;
    }
}
