package com.softwareverde.bitcoin.transaction.coinbase;

import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.io.Logger;

public class MutableCoinbaseTransaction extends MutableTransaction implements CoinbaseTransaction {

    public MutableCoinbaseTransaction() { }

    public MutableCoinbaseTransaction(final Transaction transaction) {
        super(transaction);
    }

    public void setCoinbaseScript(final UnlockingScript unlockingScript) {
        if (_transactionInputs.getSize() < 1) {
            Logger.log("Attempted to set unlocking script on invalid coinbase transaction.");
            return;
        }

        final MutableTransactionInput transactionInput = _transactionInputs.get(0);
        transactionInput.setUnlockingScript(unlockingScript.asConst());
    }

    @Override
    public UnlockingScript getCoinbaseScript() {
        if (_transactionInputs.getSize() < 1) { return null; }

        final TransactionInput transactionInput = _transactionInputs.get(0);
        return transactionInput.getUnlockingScript();
    }
}
