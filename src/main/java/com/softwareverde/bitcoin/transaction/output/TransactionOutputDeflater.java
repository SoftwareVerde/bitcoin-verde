package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.json.Json;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class TransactionOutputDeflater {
    protected ByteArray _toBytes(final TransactionOutput transactionOutput) {
        final byte[] valueBytes = new byte[8];
        ByteUtil.setBytes(valueBytes, ByteUtil.longToBytes(transactionOutput.getAmount()));

        final ByteArray lockingScriptBytes = transactionOutput.getLockingScript().getBytes();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(valueBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(CompactVariableLengthInteger.variableLengthIntegerToBytes(lockingScriptBytes.getByteCount()), Endian.BIG);
        byteArrayBuilder.appendBytes(lockingScriptBytes, Endian.BIG);

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }

    public Integer getByteCount(final TransactionOutput transactionOutput) {
        final int valueByteCount = 8;

        final Script lockingScript = transactionOutput.getLockingScript();
        final int scriptByteCount;
        {
            int byteCount = 0;
            byteCount += CompactVariableLengthInteger.variableLengthIntegerToBytes(lockingScript.getByteCount()).getByteCount();
            byteCount += lockingScript.getByteCount();
            scriptByteCount = byteCount;
        }

        return (valueByteCount + scriptByteCount);
    }

    public ByteArray toBytes(final TransactionOutput transactionOutput) {
        return _toBytes(transactionOutput);
    }

    public Json toJson(final TransactionOutput transactionOutput) {
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
        final LockingScript lockingScript = transactionOutput.getLockingScript();
        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
        final Address address = scriptPatternMatcher.extractAddress(scriptType, lockingScript);

        final Json json = new Json();
        json.put("amount", transactionOutput.getAmount());
        json.put("index", transactionOutput.getIndex());
        json.put("lockingScript", transactionOutput.getLockingScript());
        json.put("type", scriptType);
        json.put("address", (address == null ? null : address.toBase58CheckEncoded()));

        // json.put("bytes", HexUtil.toHexString(_toBytes(transactionOutput)));

        return json;
    }
}
