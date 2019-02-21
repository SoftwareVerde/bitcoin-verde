package com.softwareverde.bitcoin.transaction;

public class TransactionWithFee {
    public final Transaction transaction;
    public final Long transactionFee;

    public TransactionWithFee(final Transaction transaction, final Long transactionFee) {
        this.transaction = transaction.asConst();
        this.transactionFee = transactionFee;
    }
}
