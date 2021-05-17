package com.softwareverde.bitcoin.transaction.output;

public class MutableUnspentTransactionOutput extends MutableTransactionOutput implements UnspentTransactionOutput {
    protected Long _blockHeight;
    protected Boolean _isCoinbase;

    public MutableUnspentTransactionOutput() { }

    public MutableUnspentTransactionOutput(final TransactionOutput transactionOutput, final Long blockHeight, final Boolean isCoinbase) {
        super(transactionOutput);
        _blockHeight = blockHeight;
        _isCoinbase = isCoinbase;
    }

    public MutableUnspentTransactionOutput(final UnspentTransactionOutput unspentTransactionOutput) {
        super(unspentTransactionOutput);
        _blockHeight = unspentTransactionOutput.getBlockHeight();
        _isCoinbase = unspentTransactionOutput.isCoinbase();
    }

    public void setBlockHeight(final Long blockHeight) {
        _blockHeight = blockHeight;
    }

    @Override
    public Long getBlockHeight() {
        return _blockHeight;
    }

    public void setIsCoinbase(final Boolean isCoinbase) {
        _isCoinbase = isCoinbase;
    }

    @Override
    public Boolean isCoinbase() {
        return _isCoinbase;
    }

    @Override
    public ImmutableUnspentTransactionOutput asConst() {
        return new ImmutableUnspentTransactionOutput(this);
    }
}
