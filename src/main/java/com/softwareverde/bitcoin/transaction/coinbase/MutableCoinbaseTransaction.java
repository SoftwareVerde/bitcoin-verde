package com.softwareverde.bitcoin.transaction.coinbase;

import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
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

        final MutableTransactionInput transactionInput = new MutableTransactionInput(_transactionInputs.get(0));
        transactionInput.setUnlockingScript(unlockingScript.asConst());
        _transactionInputs.set(0, transactionInput);
    }

    @Override
    public UnlockingScript getCoinbaseScript() {
        if (_transactionInputs.getSize() < 1) { return null; }

        final TransactionInput transactionInput = _transactionInputs.get(0);
        return transactionInput.getUnlockingScript();
    }

    public void setBlockReward(final Long satoshis) {
        if (_transactionOutputs.getSize() < 1) {
            Logger.log("Attempted to set block reward on invalid coinbase transaction.");
            return;
        }

        final TransactionOutput transactionOutput = _transactionOutputs.get(0);
        final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput(transactionOutput);
        mutableTransactionOutput.setAmount(satoshis);
        _transactionOutputs.set(0, mutableTransactionOutput);
    }

    @Override
    public Long getBlockReward() {
        if (_transactionOutputs.getSize() < 1) { return null; }

        final TransactionOutput transactionOutput = _transactionOutputs.get(0);
        return transactionOutput.getAmount();
    }

    @Override
    public MutableCoinbaseTransaction asCoinbase() {
        return this;
    }
}
