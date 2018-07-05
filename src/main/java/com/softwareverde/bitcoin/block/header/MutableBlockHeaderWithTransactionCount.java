package com.softwareverde.bitcoin.block.header;

public class MutableBlockHeaderWithTransactionCount extends MutableBlockHeader implements BlockHeaderWithTransactionCount {
    protected final Integer _transactionCount;

    public MutableBlockHeaderWithTransactionCount(final BlockHeader blockHeader, final Integer transactionCount) {
        super(blockHeader);
        _transactionCount = transactionCount;
    }

    @Override
    public Integer getTransactionCount() {
        return _transactionCount;
    }
}
