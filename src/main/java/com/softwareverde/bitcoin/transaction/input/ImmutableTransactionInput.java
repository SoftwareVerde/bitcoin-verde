package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.constable.Const;
import com.softwareverde.json.Json;

public class ImmutableTransactionInput implements TransactionInput, Const {

    protected final ImmutableSha256Hash _previousOutputTransactionHash;
    protected final Integer _previousOutputIndex;
    protected final UnlockingScript _unlockingScript;
    protected final Long _sequenceNumber;

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
    public Long getSequenceNumber() {
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
}
