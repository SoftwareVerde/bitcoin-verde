package com.softwareverde.bitcoin.transaction.output;

public class ImmutableUnspentTransactionOutput extends ImmutableTransactionOutput implements UnspentTransactionOutput {
    protected final Long _blockHeight;

    public ImmutableUnspentTransactionOutput(final TransactionOutput transactionOutput, final Long blockHeight) {
        super(transactionOutput);
        _blockHeight = blockHeight;
    }

    public ImmutableUnspentTransactionOutput(final UnspentTransactionOutput unspentTransactionOutput) {
        super(unspentTransactionOutput);
        _blockHeight = unspentTransactionOutput.getBlockHeight();
    }

    @Override
    public Long getBlockHeight() {
        return _blockHeight;
    }

    @Override
    public ImmutableUnspentTransactionOutput asConst() {
        return this;
    }
}
