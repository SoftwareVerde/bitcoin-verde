package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.Script;

public class ImmutableTransactionOutput implements TransactionOutput {
    protected final TransactionOutput _transactionOutput;

    public ImmutableTransactionOutput() {
        _transactionOutput = new MutableTransactionOutput();
    }

    public ImmutableTransactionOutput(final TransactionOutput transactionOutput) {
        if (transactionOutput instanceof ImmutableTransactionOutput) {
            _transactionOutput = transactionOutput;
            return;
        }

        final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
        mutableTransactionOutput.setIndex(transactionOutput.getIndex());
        mutableTransactionOutput.setAmount(transactionOutput.getAmount());
        mutableTransactionOutput.setLockingScript(transactionOutput.getLockingScript());
        _transactionOutput = mutableTransactionOutput;
    }

    @Override
    public Long getAmount() {
        return _transactionOutput.getAmount();
    }

    @Override
    public Integer getIndex() {
        return _transactionOutput.getIndex();
    }

    @Override
    public Script getLockingScript() {
        return _transactionOutput.getLockingScript();
    }

    @Override
    public Integer getByteCount() {
        return _transactionOutput.getByteCount();
    }

    @Override
    public byte[] getBytes() {
        // return ByteUtil.copyBytes(_transactionOutput.getBytes());
        return _transactionOutput.getBytes(); // NOTE: This is already a copied byte[]...
    }
}
