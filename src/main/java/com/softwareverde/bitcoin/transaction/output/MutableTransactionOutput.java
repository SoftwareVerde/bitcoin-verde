package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;

public class MutableTransactionOutput implements TransactionOutput {
    protected Long _amount = 0L;
    protected Integer _index = 0;
    protected LockingScript _lockingScript = LockingScript.EMPTY_SCRIPT;

    public MutableTransactionOutput() { }

    public MutableTransactionOutput(final TransactionOutput transactionOutput) {
        _amount = transactionOutput.getAmount();
        _index = transactionOutput.getIndex();

        _lockingScript = transactionOutput.getLockingScript().asConst();
    }

    @Override
    public Long getAmount() { return _amount; }
    public void setAmount(final Long amount) { _amount = amount; }

    @Override
    public Integer getIndex() { return _index; }
    public void setIndex(final Integer index) { _index = index; }

    @Override
    public LockingScript getLockingScript() { return _lockingScript; }
    public void setLockingScript(final LockingScript lockingScript) { _lockingScript = lockingScript.asConst(); }
    public void setLockingScript(final byte[] bytes) {
        _lockingScript = new ImmutableLockingScript(bytes);
    }

    @Override
    public ImmutableTransactionOutput asConst() {
        return new ImmutableTransactionOutput(this);
    }
}
