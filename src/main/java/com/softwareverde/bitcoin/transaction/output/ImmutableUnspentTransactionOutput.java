package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.util.Util;

public class ImmutableUnspentTransactionOutput extends ImmutableTransactionOutput implements UnspentTransactionOutput {
    protected final Long _blockHeight;
    protected final Boolean _isCoinbase;

    public ImmutableUnspentTransactionOutput(final TransactionOutput transactionOutput, final Long blockHeight, final Boolean isCoinbase) {
        super(transactionOutput);
        _blockHeight = blockHeight;
        _isCoinbase = isCoinbase;
    }

    public ImmutableUnspentTransactionOutput(final UnspentTransactionOutput unspentTransactionOutput) {
        super(unspentTransactionOutput);
        _blockHeight = unspentTransactionOutput.getBlockHeight();
        _isCoinbase = unspentTransactionOutput.isCoinbase();
    }

    @Override
    public Long getBlockHeight() {
        return _blockHeight;
    }

    @Override
    public Boolean isCoinbase() {
        return _isCoinbase;
    }

    @Override
    public ImmutableUnspentTransactionOutput asConst() {
        return this;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof UnspentTransactionOutput)) { return false; }

        final boolean superIsEqual = super.equals(object);
        if (! superIsEqual) { return false; }

        final UnspentTransactionOutput unspentTransactionOutput = (UnspentTransactionOutput) object;
        if (! Util.areEqual(_blockHeight, unspentTransactionOutput.getBlockHeight())) { return false; }
        if (! Util.areEqual(_isCoinbase, unspentTransactionOutput.isCoinbase())) { return false; }

        return true;
    }
}
