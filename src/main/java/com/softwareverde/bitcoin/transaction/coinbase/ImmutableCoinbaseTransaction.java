package com.softwareverde.bitcoin.transaction.coinbase;

import com.softwareverde.bitcoin.transaction.ImmutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;

public class ImmutableCoinbaseTransaction extends ImmutableTransaction implements CoinbaseTransaction {

    public ImmutableCoinbaseTransaction(final Transaction transaction) {
        super(transaction);
    }

    @Override
    public UnlockingScript getCoinbaseScript() {
        if (_transactionInputs.getSize() < 1) { return null; }

        final TransactionInput transactionInput = _transactionInputs.get(0);
        return transactionInput.getUnlockingScript();
    }

    @Override
    public Long getBlockReward() {
        if (_transactionOutputs.getSize() < 1) { return null; }

        final TransactionOutput transactionOutput = _transactionOutputs.get(0);
        return transactionOutput.getAmount();
    }
}
