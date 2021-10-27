package com.softwareverde.bitcoin.block.header;

import com.softwareverde.json.Json;

public class MutableBlockHeaderWithTransactionCount extends MutableBlockHeader implements BlockHeaderWithTransactionCount {
    protected Integer _transactionCount;

    public MutableBlockHeaderWithTransactionCount(final BlockHeader blockHeader, final Integer transactionCount) {
        super(blockHeader);
        _transactionCount = transactionCount;
    }

    public void setTransactionCount(final Integer transactionCount) {
        _transactionCount = transactionCount;
        _cachedHashCode = null;
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
    public boolean equals(final Object object) {
        final boolean transactionCountIsEqual = BlockHeaderWithTransactionCountCore.equals(this, object);
        if (! transactionCountIsEqual) { return false; }

        return super.equals(object);
    }
}
