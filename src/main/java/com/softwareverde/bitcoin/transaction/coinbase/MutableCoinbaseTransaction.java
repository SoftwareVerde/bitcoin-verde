package com.softwareverde.bitcoin.transaction.coinbase;

import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;

public class MutableCoinbaseTransaction extends MutableTransaction implements CoinbaseTransaction {

    public MutableCoinbaseTransaction() { }

    public MutableCoinbaseTransaction(final Transaction transaction) {
        super(transaction);
    }

    @Override
    public UnlockingScript getCoinbaseScript() {
        if (_transactionInputs.getSize() < 1) { return null; }

        final TransactionInput transactionInput = _transactionInputs.get(0);
        return transactionInput.getUnlockingScript();
    }
}
