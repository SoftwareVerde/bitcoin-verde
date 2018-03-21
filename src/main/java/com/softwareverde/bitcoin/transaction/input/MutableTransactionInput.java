package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.MutableHash;

public class MutableTransactionInput implements TransactionInput {

    protected Hash _previousTransactionOutputHash = new MutableHash();
    protected Integer _previousTransactionOutputIndex = 0;
    protected Script _unlockingScript = Script.EMPTY_SCRIPT;
    protected Long _sequenceNumber = MAX_SEQUENCE_NUMBER;

    public MutableTransactionInput() { }

    public MutableTransactionInput(final TransactionInput transactionInput) {
        _previousTransactionOutputHash = transactionInput.getPreviousOutputTransactionHash().asConst();
        _previousTransactionOutputIndex = transactionInput.getPreviousOutputIndex();
        _unlockingScript = transactionInput.getUnlockingScript().asConst();
        _sequenceNumber = transactionInput.getSequenceNumber();
    }

    @Override
    public Hash getPreviousOutputTransactionHash() { return _previousTransactionOutputHash; }
    public void setPreviousOutputTransactionHash(final Hash previousOutputTransactionHash) {
        _previousTransactionOutputHash = previousOutputTransactionHash;
    }

    @Override
    public Integer getPreviousOutputIndex() { return _previousTransactionOutputIndex; }
    public void setPreviousOutputIndex(final Integer index) {
        _previousTransactionOutputIndex = index;
    }

    @Override
    public Script getUnlockingScript() { return _unlockingScript; }
    public void setUnlockingScript(final UnlockingScript signatureScript) {
        _unlockingScript = signatureScript;
    }

    @Override
    public Long getSequenceNumber() { return _sequenceNumber; }

    public void setSequenceNumber(final Long sequenceNumber) {
        _sequenceNumber = sequenceNumber;
    }

    @Override
    public ImmutableTransactionInput asConst() {
        return new ImmutableTransactionInput(this);
    }
}
