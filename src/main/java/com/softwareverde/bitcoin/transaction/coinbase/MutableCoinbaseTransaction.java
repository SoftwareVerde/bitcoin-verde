package com.softwareverde.bitcoin.transaction.coinbase;

import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.logging.Logger;

public class MutableCoinbaseTransaction extends MutableTransaction implements CoinbaseTransaction {

    public MutableCoinbaseTransaction() { }

    public MutableCoinbaseTransaction(final Transaction transaction) {
        super(transaction);
    }

    public void setCoinbaseScript(final UnlockingScript unlockingScript) {
        if (_transactionInputs.isEmpty()) {
            Logger.warn("Attempted to set unlocking script on invalid coinbase transaction.");
            return;
        }

        final MutableTransactionInput transactionInput = new MutableTransactionInput(_transactionInputs.get(0));
        transactionInput.setUnlockingScript(unlockingScript.asConst());
        _transactionInputs.set(0, transactionInput);
    }

    @Override
    public UnlockingScript getCoinbaseScript() {
        if (_transactionInputs.isEmpty()) { return null; }

        final TransactionInput transactionInput = _transactionInputs.get(0);
        return transactionInput.getUnlockingScript();
    }

    public void setBlockReward(final Long satoshis) {
        if (_transactionOutputs.isEmpty()) {
            Logger.warn("Attempted to set block reward on invalid coinbase transaction.");
            return;
        }

        for (int i = 0; i < _transactionOutputs.getCount(); ++i) {
            final TransactionOutput transactionOutput = _transactionOutputs.get(i);
            final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput(transactionOutput);
            mutableTransactionOutput.setAmount((i == 0) ? satoshis : 0L);
            _transactionOutputs.set(i, mutableTransactionOutput);
        }
    }

    @Override
    public Long getBlockReward() {
        if (_transactionOutputs.isEmpty()) { return null; }

        return this.getTotalOutputValue();
    }

    @Override
    public MutableCoinbaseTransaction asCoinbase() {
        return this;
    }
}
