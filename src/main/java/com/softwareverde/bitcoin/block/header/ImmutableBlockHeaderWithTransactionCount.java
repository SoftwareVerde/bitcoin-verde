package com.softwareverde.bitcoin.block.header;

import com.softwareverde.json.Json;

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

    @Override
    public Json toJson() {
        final Json json = super.toJson();
        BlockHeaderWithTransactionCountCore.toJson(json, _transactionCount);
        return json;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        final boolean transactionCountIsEqual = BlockHeaderWithTransactionCountCore.equals(this, object);
        if (! transactionCountIsEqual) { return false; }

        return super.equals(object);
    }
}
