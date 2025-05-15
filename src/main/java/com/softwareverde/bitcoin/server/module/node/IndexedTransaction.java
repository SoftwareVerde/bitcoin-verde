package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

import java.util.Objects;

public class IndexedTransaction {
    public final Sha256Hash hash;
    public final Long blockHeight;
    public final Long diskOffset;
    public final Integer byteCount;

    public IndexedTransaction(final Sha256Hash hash, final Long blockHeight, final Long diskOffset, final Integer byteCount) {
        this.hash = hash;
        this.blockHeight = blockHeight;
        this.diskOffset = diskOffset;
        this.byteCount = byteCount;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) { return true; }
        if ( (object == null) || (this.getClass() != object.getClass()) ) { return false; }
        final IndexedTransaction indexedTransaction = (IndexedTransaction) object;
        if (! Util.areEqual(this.hash, indexedTransaction.hash)) { return false; }
        if (! Util.areEqual(this.blockHeight, indexedTransaction.blockHeight)) { return false; }
        if (! Util.areEqual(this.diskOffset, indexedTransaction.diskOffset)) { return false; }
        if (! Util.areEqual(this.byteCount, indexedTransaction.byteCount)) { return false; }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.hash, this.blockHeight, this.diskOffset, this.byteCount);
    }
}
