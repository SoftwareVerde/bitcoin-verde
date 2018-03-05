package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;

public class SignatureContext {
    private final Transaction _transaction;
    private final Integer _inputIndexToBeSigned;
    private final TransactionOutput _transactionOutputBeingSpent;

    public SignatureContext(final Transaction transaction, final Integer inputIndexToBeSigned, final TransactionOutput transactionOutputBeingSpent) {
        _transaction = transaction;
        _inputIndexToBeSigned = inputIndexToBeSigned;
        _transactionOutputBeingSpent = transactionOutputBeingSpent;
    }

    public Transaction getTransaction() {
        return _transaction;
    }

    public Integer getInputIndexToBeSigned() {
        return _inputIndexToBeSigned;
    }

    public TransactionOutput getTransactionOutputBeingSpent() {
        return _transactionOutputBeingSpent;
    }
}
