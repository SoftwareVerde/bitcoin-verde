package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.json.Json;
import com.softwareverde.util.HexUtil;

public class TransactionOutputDeflater {
    protected byte[] _toBytes(final TransactionOutput transactionOutput) {
        final byte[] valueBytes = new byte[8];
        ByteUtil.setBytes(valueBytes, ByteUtil.longToBytes(transactionOutput.getAmount()));

        final ByteArray lockingScriptBytes = transactionOutput.getLockingScript().getBytes();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(valueBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(lockingScriptBytes.getByteCount()), Endian.BIG);
        byteArrayBuilder.appendBytes(lockingScriptBytes, Endian.BIG);

        return byteArrayBuilder.build();
    }

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
        return _toBytes(transactionOutput);
    }

    public Json toJson(final TransactionOutput transactionOutput) {
        final Json json = new Json();
        json.put("amount", transactionOutput.getAmount());
        json.put("index", transactionOutput.getIndex());
        json.put("lockingScript", transactionOutput.getLockingScript());
        json.put("bytes", HexUtil.toHexString(_toBytes(transactionOutput)));
        return json;
    }
}
