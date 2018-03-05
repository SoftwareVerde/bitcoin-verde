package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.script.ImmutableScript;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.constable.Const;

public class ImmutableTransactionInput implements TransactionInput, Const {

    protected final ImmutableHash _previousTransactionOutputHash;
    protected final Integer _previousTransactionOutputIndex;
    protected final ImmutableScript _unlockingScript;
    protected final Long _sequenceNumber;

    public ImmutableTransactionInput(final TransactionInput transactionInput) {
        _previousTransactionOutputHash = transactionInput.getPreviousTransactionOutputHash().asConst();
        _previousTransactionOutputIndex = transactionInput.getPreviousTransactionOutputIndex();
        _unlockingScript = transactionInput.getUnlockingScript().asConst();
        _sequenceNumber = transactionInput.getSequenceNumber();
    }

    @Override
    public ImmutableHash getPreviousTransactionOutputHash() {
        return _previousTransactionOutputHash;
    }

    @Override
    public Integer getPreviousTransactionOutputIndex() {
        return _previousTransactionOutputIndex;
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
