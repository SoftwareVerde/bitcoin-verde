package com.softwareverde.bitcoin.block.header;

import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

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
        json.put("transactionCount", _transactionCount);
        return json;
    }

    @Override
    public boolean equals(final Object object) {
        if (object instanceof BlockHeaderWithTransactionCount) {
            if (! Util.areEqual(_transactionCount, ((BlockHeaderWithTransactionCount) object).getTransactionCount())) {
                return false;
            }
        }

        return super.equals(object);
    }
}
