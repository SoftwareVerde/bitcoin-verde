package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.token.CashToken;
import com.softwareverde.constable.Const;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

public class ImmutableTransactionOutput implements TransactionOutput, Const {
    protected final Long _amount;
    protected final Integer _index;
    protected final LockingScript _lockingScript;
    protected final CashToken _cashToken;

    protected Integer _cachedHashCode = null;

    public ImmutableTransactionOutput(final TransactionOutput transactionOutput) {
        _amount = transactionOutput.getAmount();
        _index = transactionOutput.getIndex();
        _lockingScript = transactionOutput.getLockingScript().asConst();
        _cashToken = transactionOutput.getCashToken();
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
    public CashToken getCashToken() {
        return _cashToken;
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

    @Override
    public int hashCode() {
        final Integer cachedHashCode = _cachedHashCode;
        if (cachedHashCode != null) { return cachedHashCode; }

        final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();
        _cachedHashCode = transactionOutputDeflater.toBytes(this).hashCode();
        return _cachedHashCode;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof TransactionOutput)) { return false; }

        final TransactionOutput transactionOutput = (TransactionOutput) object;
        if (! Util.areEqual(_amount, transactionOutput.getAmount())) { return false; }
        if (! Util.areEqual(_index, transactionOutput.getIndex())) { return false; }
        if (! Util.areEqual(_lockingScript, transactionOutput.getLockingScript())) { return false; }
        if (! Util.areEqual(_cashToken, transactionOutput.getCashToken())) { return false; }
        return true;
    }
}
