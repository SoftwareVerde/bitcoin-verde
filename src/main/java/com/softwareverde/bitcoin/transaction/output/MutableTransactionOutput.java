package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class MutableTransactionOutput implements TransactionOutput {
    protected Long _amount = 0L;
    protected Integer _index = 0;
    protected byte[] _lockingScript = new byte[0];

    public MutableTransactionOutput() { }
    public MutableTransactionOutput(final TransactionOutput transactionOutput) {
        _amount = transactionOutput.getAmount();
        _index = transactionOutput.getIndex();

        if (transactionOutput instanceof ImmutableTransactionOutput) {
            _lockingScript = ByteUtil.copyBytes(transactionOutput.getLockingScript());
        }
        else {
            _lockingScript = transactionOutput.getLockingScript();
        }
    }

    @Override
    public Long getAmount() { return _amount; }
    public void setAmount(final Long amount) { _amount = amount; }

    @Override
    public Integer getIndex() { return _index; }
    public void setIndex(final Integer index) { _index = index; }

    @Override
    public byte[] getLockingScript() { return ByteUtil.copyBytes(_lockingScript); }
    public void setLockingScript(final byte[] bytes) { _lockingScript = bytes; }

    @Override
    public Integer getByteCount() {
        final Integer valueByteCount = 8;

        final Integer scriptByteCount;
        {
            Integer byteCount = 0;
            byteCount += ByteUtil.variableLengthIntegerToBytes(_lockingScript.length).length;
            byteCount += _lockingScript.length;
            scriptByteCount = byteCount;
        }

        return (valueByteCount + scriptByteCount);
    }

    @Override
    public byte[] getBytes() {
        final byte[] valueBytes = new byte[8];
        ByteUtil.setBytes(valueBytes, ByteUtil.longToBytes(_amount));

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(valueBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_lockingScript.length), Endian.BIG);
        byteArrayBuilder.appendBytes(_lockingScript, Endian.LITTLE); // TODO: Unsure if Big or Little endian.

        return byteArrayBuilder.build();
    }
}
