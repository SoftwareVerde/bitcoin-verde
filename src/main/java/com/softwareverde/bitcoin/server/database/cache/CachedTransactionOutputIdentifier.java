package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.Const;
import com.softwareverde.util.Util;

class CachedTransactionOutputIdentifier implements Const {
    final TransactionId transactionId;
    final Integer transactionOutputIndex;

    public CachedTransactionOutputIdentifier(final TransactionId transactionId, final Integer transactionOutputIndex) {
        this.transactionId = transactionId;
        this.transactionOutputIndex = transactionOutputIndex;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof CachedTransactionOutputIdentifier)) { return false; }

        final CachedTransactionOutputIdentifier cachedTransactionOutputIdentifier = (CachedTransactionOutputIdentifier) object;
        if (! Util.areEqual(this.transactionId, cachedTransactionOutputIdentifier.transactionId)) { return false; }
        if (! Util.areEqual(this.transactionOutputIndex, cachedTransactionOutputIdentifier.transactionOutputIndex)) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        return ( (this.transactionId == null ? 0 : this.transactionId.hashCode()) + (this.transactionOutputIndex == null ? 0 : transactionOutputIndex.hashCode()) );
    }
}
