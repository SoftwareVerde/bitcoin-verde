package com.softwareverde.bitcoin.block.header;

import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

public interface BlockHeaderWithTransactionCount extends BlockHeader {
    Integer getTransactionCount();
}

class BlockHeaderWithTransactionCountCore {
    public static void toJson(final Json json, final Integer transactionCount) {
        json.put("transactionCount", transactionCount);
    }

    public static boolean equals(final BlockHeaderWithTransactionCount blockHeaderWithTransactionCount, final Object object) {
        if (! (object instanceof BlockHeaderWithTransactionCount)) { return false; }

        final BlockHeaderWithTransactionCount blockHeaderWithTransactionCountObject = ((BlockHeaderWithTransactionCount) object);
        return (Util.areEqual(blockHeaderWithTransactionCount.getTransactionCount(), blockHeaderWithTransactionCountObject.getTransactionCount()));
    }
}