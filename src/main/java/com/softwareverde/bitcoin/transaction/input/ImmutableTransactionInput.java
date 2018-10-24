package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.constable.Const;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

public class ImmutableTransactionInput implements TransactionInput, Const {

    protected final ImmutableSha256Hash _previousOutputTransactionHash;
    protected final Integer _previousOutputIndex;
    protected final UnlockingScript _unlockingScript;
    protected final SequenceNumber _sequenceNumber;

    protected Integer _cachedHashCode;

    public ImmutableTransactionInput(final TransactionInput transactionInput) {
        _previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash().asConst();
        _previousOutputIndex = transactionInput.getPreviousOutputIndex();
        _unlockingScript = transactionInput.getUnlockingScript().asConst();
        _sequenceNumber = transactionInput.getSequenceNumber();
    }

    @Override
    public ImmutableSha256Hash getPreviousOutputTransactionHash() {
        return _previousOutputTransactionHash;
    }

    @Override
    public Integer getPreviousOutputIndex() {
        return _previousOutputIndex;
    }

    @Override
    public UnlockingScript getUnlockingScript() {
        return _unlockingScript;
    }

    @Override
    public SequenceNumber getSequenceNumber() {
        return _sequenceNumber;
    }

    @Override
    public ImmutableTransactionInput asConst() {
        return this;
    }

    @Override
    public Json toJson() {
        final TransactionInputDeflater transactionInputDeflater = new TransactionInputDeflater();
        return transactionInputDeflater.toJson(this);
    }

    @Override
    public int hashCode() {
        final Integer cachedHashCode = _cachedHashCode;
        if (cachedHashCode != null) { return cachedHashCode; }

        final TransactionInputDeflater transactionInputDeflater = new TransactionInputDeflater();
        _cachedHashCode = transactionInputDeflater.toBytes(this).hashCode();
        return _cachedHashCode;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof TransactionInput)) { return false; }

        final TransactionInput transactionInput = (TransactionInput) object;
        if (! Util.areEqual(_previousOutputTransactionHash, transactionInput.getPreviousOutputTransactionHash())) { return false; }
        if (! Util.areEqual(_previousOutputIndex, transactionInput.getPreviousOutputIndex())) { return false; }
        if (! Util.areEqual(_sequenceNumber, transactionInput.getSequenceNumber())) { return false; }
        if (! Util.areEqual(_unlockingScript, transactionInput.getUnlockingScript())) { return false; }
        return true;
    }
}
