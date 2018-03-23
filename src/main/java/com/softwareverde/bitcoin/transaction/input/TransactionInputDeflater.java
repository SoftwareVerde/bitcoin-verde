package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.type.bytearray.FragmentedBytes;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class TransactionInputDeflater {
    private void _toFragmentedBytes(final TransactionInput transactionInput, final ByteArrayBuilder headBytes, final ByteArrayBuilder tailBytes) {
        final byte[] sequenceBytes = new byte[4];
        ByteUtil.setBytes(sequenceBytes, ByteUtil.integerToBytes(transactionInput.getSequenceNumber()));

        final byte[] indexBytes = new byte[4];
        ByteUtil.setBytes(indexBytes, ByteUtil.integerToBytes(transactionInput.getPreviousOutputIndex()));

        final byte[] unlockingScriptBytes = transactionInput.getUnlockingScript().getBytes();

        headBytes.appendBytes(transactionInput.getPreviousOutputTransactionHash().getBytes(), Endian.LITTLE);
        headBytes.appendBytes(indexBytes, Endian.LITTLE);
        headBytes.appendBytes(ByteUtil.variableLengthIntegerToBytes(unlockingScriptBytes.length), Endian.BIG);
        headBytes.appendBytes(unlockingScriptBytes, Endian.BIG);

        tailBytes.appendBytes(sequenceBytes, Endian.LITTLE);
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
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        _toFragmentedBytes(transactionInput, byteArrayBuilder, byteArrayBuilder);
        return byteArrayBuilder.build();
    }

    public FragmentedBytes fragmentTransactionInput(final TransactionInput transactionInput) {
        final ByteArrayBuilder headBytesBuilder = new ByteArrayBuilder();
        final ByteArrayBuilder tailBytesBuilder = new ByteArrayBuilder();

        _toFragmentedBytes(transactionInput, headBytesBuilder, tailBytesBuilder);

        return new FragmentedBytes(headBytesBuilder.build(), tailBytesBuilder.build());
    }
}
