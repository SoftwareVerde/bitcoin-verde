package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.script.ImmutableScript;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.constable.Const;

public class ImmutableTransactionInput implements TransactionInput, Const {

    protected final ImmutableHash _previousOutputTransactionHash;
    protected final Integer _previousOutputIndex;
    protected final ImmutableScript _unlockingScript;
    protected final Long _sequenceNumber;

    public ImmutableTransactionInput(final TransactionInput transactionInput) {
        _previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash().asConst();
        _previousOutputIndex = transactionInput.getPreviousOutputIndex();
        _unlockingScript = transactionInput.getUnlockingScript().asConst();
        _sequenceNumber = transactionInput.getSequenceNumber();
    }

    @Override
    public ImmutableHash getPreviousOutputTransactionHash() {
        return _previousOutputTransactionHash;
    }

    @Override
    public Integer getPreviousOutputIndex() {
        return _previousOutputIndex;
    }

    @Override
    public ImmutableScript getUnlockingScript() {
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
}
