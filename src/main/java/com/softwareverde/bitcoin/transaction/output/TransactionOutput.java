package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class TransactionOutput {
    protected Long _value = 0L;
    protected Integer _index = 0;
    protected byte[] _script = new byte[0];

    public Long getValue() { return _value; }
    public void setValue(final Long value) { _value = value; }

    public Integer getIndex() { return _index; }
    public void setIndex(final Integer index) { _index = index; }

    public byte[] getScript() { return ByteUtil.copyBytes(_script); }
    public void setScript(final byte[] bytes) { _script = bytes; }

    public Integer getByteCount() {
        final Integer valueByteCount = 8;

        final Integer scriptByteCount;
        {
            Integer byteCount = 0;
            byteCount += ByteUtil.variableLengthIntegerToBytes(_script.length).length;
            byteCount += _script.length;
            scriptByteCount = byteCount;
        }

        return (valueByteCount + scriptByteCount);
    }

    public TransactionOutput copy() {
        final TransactionOutput transactionOutput = new TransactionOutput();
        transactionOutput._value = _value;
        transactionOutput._index = _index;
        transactionOutput._script = ByteUtil.copyBytes(_script);
        return transactionOutput;
    }

    public byte[] getBytes() {
        final byte[] valueBytes = new byte[8];
        ByteUtil.setBytes(valueBytes, ByteUtil.longToBytes(_value));

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(valueBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_script.length), Endian.BIG);
        byteArrayBuilder.appendBytes(_script, Endian.LITTLE); // TODO: Unsure if Big or Little endian.

        return byteArrayBuilder.build();
    }
}
