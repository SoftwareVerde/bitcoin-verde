package com.softwareverde.bitcoin.transaction.output.identifier;

import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.Const;
import com.softwareverde.util.Util;

public class StoredTransactionOutputIdentifier implements Const {
    protected final TransactionId _transactionId;
    protected final Integer _outputIndex;

    public StoredTransactionOutputIdentifier(final TransactionId transactionId, final Integer outputIndex) {
        _transactionId = transactionId;
        _outputIndex = outputIndex;
    }

    public TransactionId getTransactionId() {
        return _transactionId;
    }

    public Integer getOutputIndex() {
        return _outputIndex;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof StoredTransactionOutputIdentifier)) { return false; }
        final StoredTransactionOutputIdentifier transactionOutputIdentifier = (StoredTransactionOutputIdentifier) object;

        if (! Util.areEqual(_transactionId, transactionOutputIdentifier._transactionId)) { return false; }
        if (! Util.areEqual(_outputIndex, transactionOutputIdentifier._outputIndex)) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        return (_transactionId.hashCode() + _outputIndex.hashCode());
    }
}
