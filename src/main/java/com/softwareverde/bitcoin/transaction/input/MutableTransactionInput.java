package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.json.Json;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

public class MutableTransactionInput implements TransactionInput {

    protected Sha256Hash _previousOutputTransactionHash = Sha256Hash.EMPTY_HASH;
    protected Integer _previousOutputIndex = 0;
    protected UnlockingScript _unlockingScript = UnlockingScript.EMPTY_SCRIPT;
    protected SequenceNumber _sequenceNumber = SequenceNumber.MAX_SEQUENCE_NUMBER;

    protected Integer _cachedHashCode = null;

    public MutableTransactionInput() { }

    public MutableTransactionInput(final TransactionInput transactionInput) {
        _previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash().asConst();
        _previousOutputIndex = transactionInput.getPreviousOutputIndex();
        _unlockingScript = transactionInput.getUnlockingScript().asConst();
        _sequenceNumber = transactionInput.getSequenceNumber();
    }

    @Override
    public Sha256Hash getPreviousOutputTransactionHash() { return _previousOutputTransactionHash; }

    public void setPreviousOutputTransactionHash(final Sha256Hash previousOutputTransactionHash) {
        _previousOutputTransactionHash = previousOutputTransactionHash.asConst();
        _cachedHashCode = null;
    }

    @Override
    public Integer getPreviousOutputIndex() { return _previousOutputIndex; }

    public void setPreviousOutputIndex(final Integer index) {
        _previousOutputIndex = index;
        _cachedHashCode = null;
    }

    @Override
    public UnlockingScript getUnlockingScript() { return _unlockingScript; }

    public void setUnlockingScript(final UnlockingScript signatureScript) {
        _unlockingScript = signatureScript.asConst();
        _cachedHashCode = null;
    }

    @Override
    public SequenceNumber getSequenceNumber() { return _sequenceNumber; }

    public void setSequenceNumber(final SequenceNumber sequenceNumber) {
        _sequenceNumber = sequenceNumber.asConst();
        _cachedHashCode = null;
    }

    @Override
    public ImmutableTransactionInput asConst() {
        return new ImmutableTransactionInput(this);
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
        final Integer hashCode = transactionInputDeflater.toBytes(this).hashCode();
        _cachedHashCode = hashCode;
        return hashCode;
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
