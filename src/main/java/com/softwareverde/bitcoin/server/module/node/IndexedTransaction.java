package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.util.Util;

import java.util.Objects;

public class IndexedTransaction {
    public final Long blockHeight;
    public final Long diskOffset;
    public final Integer byteCount;

    public IndexedTransaction(final Long blockHeight, final Long diskOffset, final Integer byteCount) {
        this.blockHeight = blockHeight;
        this.diskOffset = diskOffset;
        this.byteCount = byteCount;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) { return true; }
        if ( (object == null) || (this.getClass() != object.getClass()) ) { return false; }
        final IndexedTransaction indexedTransaction = (IndexedTransaction) object;
        return Util.areEqual(this.blockHeight, indexedTransaction.blockHeight) && Util.areEqual(this.diskOffset, indexedTransaction.diskOffset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.blockHeight, this.diskOffset);
    }
}
