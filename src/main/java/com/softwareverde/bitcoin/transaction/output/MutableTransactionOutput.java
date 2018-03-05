package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.ImmutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;

public class MutableTransactionOutput implements TransactionOutput {
    protected Long _amount = 0L;
    protected Integer _index = 0;
    protected Script _lockingScript = Script.EMPTY_SCRIPT;

    public MutableTransactionOutput() { }

    public MutableTransactionOutput(final TransactionOutput transactionOutput) {
        _amount = transactionOutput.getAmount();
        _index = transactionOutput.getIndex();

        _lockingScript = transactionOutput.getLockingScript();
    }

    @Override
    public Long getAmount() { return _amount; }
    public void setAmount(final Long amount) { _amount = amount; }

    @Override
    public Integer getIndex() { return _index; }
    public void setIndex(final Integer index) { _index = index; }

    @Override
    public Script getLockingScript() { return _lockingScript; }
    public void setLockingScript(final Script lockingScript) { _lockingScript = lockingScript; }
    public void setLockingScript(final byte[] bytes) {
        _lockingScript = new ImmutableScript(bytes);
    }

    @Override
    public ImmutableTransactionOutput asConst() {
        return new ImmutableTransactionOutput(this);
    }
}
