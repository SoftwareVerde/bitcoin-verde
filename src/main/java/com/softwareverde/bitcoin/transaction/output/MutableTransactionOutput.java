package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.MutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class MutableTransactionOutput implements TransactionOutput {
    protected Long _amount = 0L;
    protected Integer _index = 0;
    protected Script _lockingScript = null;

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
    public void setLockingScript(final byte[] bytes) { _lockingScript = new MutableScript(bytes); }

    @Override
    public Integer getByteCount() {
        final Integer valueByteCount = 8;

        final Integer scriptByteCount;
        {
            Integer byteCount = 0;
            byteCount += ByteUtil.variableLengthIntegerToBytes(_lockingScript.getByteCount()).length;
            byteCount += _lockingScript.getByteCount();
            scriptByteCount = byteCount;
        }

        return (valueByteCount + scriptByteCount);
    }

    @Override
    public byte[] getBytes() {
        final byte[] valueBytes = new byte[8];
        ByteUtil.setBytes(valueBytes, ByteUtil.longToBytes(_amount));

        final byte[] lockingScriptBytes = _lockingScript.getBytes();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(valueBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(lockingScriptBytes.length), Endian.BIG);
        byteArrayBuilder.appendBytes(lockingScriptBytes, Endian.LITTLE); // TODO: Unsure if Big or Little endian.

        return byteArrayBuilder.build();
    }
}
