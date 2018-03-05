package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class TransactionInputDeflater {

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
        final byte[] sequenceBytes = new byte[4];
        ByteUtil.setBytes(sequenceBytes, ByteUtil.integerToBytes(transactionInput.getSequenceNumber()));

        final byte[] indexBytes = new byte[4];
        ByteUtil.setBytes(indexBytes, ByteUtil.integerToBytes(transactionInput.getPreviousTransactionOutputIndex()));

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        final byte[] unlockingScriptBytes = transactionInput.getUnlockingScript().getBytes();

        byteArrayBuilder.appendBytes(transactionInput.getPreviousTransactionOutputHash().getBytes(), Endian.LITTLE);
        byteArrayBuilder.appendBytes(indexBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(unlockingScriptBytes.length), Endian.BIG);
        byteArrayBuilder.appendBytes(unlockingScriptBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(sequenceBytes, Endian.LITTLE);

        return byteArrayBuilder.build();
    }
}
