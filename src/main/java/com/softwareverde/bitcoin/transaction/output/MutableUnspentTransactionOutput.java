package com.softwareverde.bitcoin.transaction.output;

public class MutableUnspentTransactionOutput extends MutableTransactionOutput implements UnspentTransactionOutput {
    protected Long _blockHeight;

    public MutableUnspentTransactionOutput() { }

    public MutableUnspentTransactionOutput(final TransactionOutput transactionOutput) {
        super(transactionOutput);

        if (transactionOutput instanceof UnspentTransactionOutput) {
            _blockHeight = ((UnspentTransactionOutput) transactionOutput).getBlockHeight();
        }
    }

    public void setBlockHeight(final Long blockHeight) {
        _blockHeight = blockHeight;
    }

    @Override
    public Long getBlockHeight() {
        return _blockHeight;
    }
}
