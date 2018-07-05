package com.softwareverde.bitcoin.block.header;

public class ImmutableBlockHeaderWithTransactionCount extends ImmutableBlockHeader implements BlockHeaderWithTransactionCount {
    protected final Integer _transactionCount;

    public ImmutableBlockHeaderWithTransactionCount(final BlockHeader blockHeader, final Integer transactionCount) {
        super(blockHeader);
        _transactionCount = transactionCount;
    }

    @Override
    public Integer getTransactionCount() {
        return _transactionCount;
    }
}
