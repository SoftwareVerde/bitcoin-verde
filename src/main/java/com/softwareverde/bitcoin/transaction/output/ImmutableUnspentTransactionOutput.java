package com.softwareverde.bitcoin.transaction.output;

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
}
