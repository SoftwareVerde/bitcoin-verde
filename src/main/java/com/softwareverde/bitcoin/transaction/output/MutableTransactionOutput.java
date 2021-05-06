package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

public class MutableTransactionOutput implements TransactionOutput {
    protected Long _amount = 0L;
    protected Integer _index = 0;
    protected LockingScript _lockingScript = LockingScript.EMPTY_SCRIPT;

    protected Integer _cachedHashCode = null;

    public MutableTransactionOutput() { }

    public MutableTransactionOutput(final TransactionOutput transactionOutput) {
        _amount = transactionOutput.getAmount();
        _index = transactionOutput.getIndex();

        _lockingScript = transactionOutput.getLockingScript().asConst();
    }

    @Override
    public Long getAmount() { return _amount; }

    public void setAmount(final Long amount) {
        _amount = amount;
        _cachedHashCode = null;
    }

    @Override
    public Integer getIndex() { return _index; }

    public void setIndex(final Integer index) {
        _index = index;
        _cachedHashCode = null;
    }

    @Override
    public LockingScript getLockingScript() { return _lockingScript; }

    public void setLockingScript(final LockingScript lockingScript) {
        _lockingScript = lockingScript.asConst();
        _cachedHashCode = null;
    }

    public void setLockingScript(final ByteArray bytes) {
        _lockingScript = new ImmutableLockingScript(bytes);
    }

    @Override
    public ImmutableTransactionOutput asConst() {
        return new ImmutableTransactionOutput(this);
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
        final ByteArray bytes = transactionOutputDeflater.toBytes(this);
        final int hashCode = bytes.hashCode();
        _cachedHashCode = hashCode;
        return hashCode;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof TransactionOutput)) { return false; }

        final TransactionOutput transactionOutput = (TransactionOutput) object;
        if (! Util.areEqual(_amount, transactionOutput.getAmount())) { return false; }
        if (! Util.areEqual(_index, transactionOutput.getIndex())) { return false; }
        if (! Util.areEqual(_lockingScript, transactionOutput.getLockingScript())) { return false; }
        return true;
    }
}
