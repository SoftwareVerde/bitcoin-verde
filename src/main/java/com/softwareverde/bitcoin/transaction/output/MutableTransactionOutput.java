package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class MutableTransactionOutput implements TransactionOutput {
    protected Long _amount = 0L;
    protected Integer _index = 0;
    protected byte[] _script = new byte[0];

    public MutableTransactionOutput() { }
    public MutableTransactionOutput(final TransactionOutput transactionOutput) {
        _amount = transactionOutput.getAmount();
        _index = transactionOutput.getIndex();

        if (transactionOutput instanceof ImmutableTransactionOutput) {
            _script = ByteUtil.copyBytes(transactionOutput.getScript());
        }
        else {
            _script = transactionOutput.getScript();
        }
    }

    @Override
    public Long getAmount() { return _amount; }
    public void setAmount(final Long amount) { _amount = amount; }

    @Override
    public Integer getIndex() { return _index; }
    public void setIndex(final Integer index) { _index = index; }

    @Override
    public byte[] getScript() { return ByteUtil.copyBytes(_script); }
    public void setScript(final byte[] bytes) { _script = bytes; }

    @Override
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

    @Override
    public byte[] getBytes() {
        final byte[] valueBytes = new byte[8];
        ByteUtil.setBytes(valueBytes, ByteUtil.longToBytes(_amount));

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(valueBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(_script.length), Endian.BIG);
        byteArrayBuilder.appendBytes(_script, Endian.LITTLE); // TODO: Unsure if Big or Little endian.

        return byteArrayBuilder.build();
    }
}
