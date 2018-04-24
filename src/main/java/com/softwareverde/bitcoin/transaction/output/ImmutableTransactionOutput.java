package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.Const;
import com.softwareverde.json.Json;

public class ImmutableTransactionOutput implements TransactionOutput, Const {
    protected final Long _amount;
    protected final Integer _index;
    protected final LockingScript _lockingScript;

    public ImmutableTransactionOutput(final TransactionOutput transactionOutput) {
        _amount = transactionOutput.getAmount();
        _index = transactionOutput.getIndex();
        _lockingScript = transactionOutput.getLockingScript().asConst();
    }

    @Override
    public Long getAmount() {
        return _amount;
    }

    @Override
    public Integer getIndex() {
        return _index;
    }

    @Override
    public LockingScript getLockingScript() {
        return _lockingScript;
    }

    @Override
    public ImmutableTransactionOutput asConst() {
        return this;
    }

    @Override
    public Json toJson() {
        final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();
        return transactionOutputDeflater.toJson(this);
    }
}
