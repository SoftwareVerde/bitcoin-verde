package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class TransactionOutputDeflater {
    public Integer getByteCount(final TransactionOutput transactionOutput) {
        final Integer valueByteCount = 8;

        final Script lockingScript = transactionOutput.getLockingScript();
        final Integer scriptByteCount;
        {
            Integer byteCount = 0;
            byteCount += ByteUtil.variableLengthIntegerToBytes(lockingScript.getByteCount()).length;
            byteCount += lockingScript.getByteCount();
            scriptByteCount = byteCount;
        }

        return (valueByteCount + scriptByteCount);
    }

    public byte[] toBytes(final TransactionOutput transactionOutput) {
        final byte[] valueBytes = new byte[8];
        ByteUtil.setBytes(valueBytes, ByteUtil.longToBytes(transactionOutput.getAmount()));

        final byte[] lockingScriptBytes = transactionOutput.getLockingScript().getBytes();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(valueBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(lockingScriptBytes.length), Endian.BIG);
        byteArrayBuilder.appendBytes(lockingScriptBytes, Endian.BIG);

        return byteArrayBuilder.build();
    }
}
