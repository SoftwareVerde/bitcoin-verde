package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.type.bytearray.FragmentedBytes;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.json.Json;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class TransactionInputDeflater {
    protected void _toFragmentedBytes(final TransactionInput transactionInput, final ByteArrayBuilder headBytes, final ByteArrayBuilder tailBytes) {
        final byte[] sequenceBytes = new byte[4];
        final SequenceNumber sequenceNumber = transactionInput.getSequenceNumber();
        ByteUtil.setBytes(sequenceBytes, ByteUtil.integerToBytes(sequenceNumber.getValue()));

        final byte[] indexBytes = new byte[4];
        ByteUtil.setBytes(indexBytes, ByteUtil.integerToBytes(transactionInput.getPreviousOutputIndex()));

        final ByteArray unlockingScriptBytes = transactionInput.getUnlockingScript().getBytes();

        headBytes.appendBytes(transactionInput.getPreviousOutputTransactionHash().getBytes(), Endian.LITTLE);
        headBytes.appendBytes(indexBytes, Endian.LITTLE);
        headBytes.appendBytes(ByteUtil.variableLengthIntegerToBytes(unlockingScriptBytes.getByteCount()), Endian.BIG);
        headBytes.appendBytes(unlockingScriptBytes, Endian.BIG);

        tailBytes.appendBytes(sequenceBytes, Endian.LITTLE);
    }

    protected byte[] _toBytes(final TransactionInput transactionInput) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        _toFragmentedBytes(transactionInput, byteArrayBuilder, byteArrayBuilder);
        return byteArrayBuilder.build();
    }

    public Integer getByteCount(final TransactionInput transactionInput) {
        final Integer previousTransactionOutputHashByteCount = 32;

        final Integer scriptByteCount;
        {
            final Script unlockingScript = transactionInput.getUnlockingScript();

            Integer byteCount = 0;
            byteCount += ByteUtil.variableLengthIntegerToBytes(unlockingScript.getByteCount()).length;
            byteCount += unlockingScript.getByteCount();
            scriptByteCount = byteCount;
        }

        final Integer sequenceByteCount = 4;

        return (previousTransactionOutputHashByteCount + scriptByteCount + sequenceByteCount);
    }

    public byte[] toBytes(final TransactionInput transactionInput) {
        return _toBytes(transactionInput);
    }

    public FragmentedBytes fragmentTransactionInput(final TransactionInput transactionInput) {
        final ByteArrayBuilder headBytesBuilder = new ByteArrayBuilder();
        final ByteArrayBuilder tailBytesBuilder = new ByteArrayBuilder();

        _toFragmentedBytes(transactionInput, headBytesBuilder, tailBytesBuilder);

        return new FragmentedBytes(headBytesBuilder.build(), tailBytesBuilder.build());
    }

    public Json toJson(final TransactionInput transactionInput) {
        final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();

        final Json json = new Json();
        json.put("previousOutputTransactionHash", transactionInput.getPreviousOutputTransactionHash());
        json.put("previousOutputIndex", transactionInput.getPreviousOutputIndex());
        json.put("unlockingScript", unlockingScript);
        json.put("sequenceNumber", transactionInput.getSequenceNumber());

        // json.put("bytes", HexUtil.toHexString(_toBytes(transactionInput)));

        return json;
    }
}
