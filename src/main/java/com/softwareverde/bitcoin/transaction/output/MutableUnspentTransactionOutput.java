package com.softwareverde.bitcoin.transaction.output;

public class MutableUnspentTransactionOutput extends MutableTransactionOutput implements UnspentTransactionOutput {
    protected Long _blockHeight;

    public MutableUnspentTransactionOutput() { }

    public MutableUnspentTransactionOutput(final TransactionOutput transactionOutput, final Long blockHeight) {
        super(transactionOutput);
        _blockHeight = blockHeight;
    }

    public MutableUnspentTransactionOutput(final UnspentTransactionOutput unspentTransactionOutput) {
        super(unspentTransactionOutput);
        _blockHeight = unspentTransactionOutput.getBlockHeight();
    }

    public void setBlockHeight(final Long blockHeight) {
        _blockHeight = blockHeight;
    }

    @Override
    public Long getBlockHeight() {
        return _blockHeight;
    }

    @Override
    public ImmutableUnspentTransactionOutput asConst() {
        return new ImmutableUnspentTransactionOutput(this);
    }
}
