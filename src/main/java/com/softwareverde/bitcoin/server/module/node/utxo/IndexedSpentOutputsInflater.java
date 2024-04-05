package com.softwareverde.bitcoin.server.module.node.utxo;

import com.google.leveldb.LevelDb;
import com.softwareverde.bitcoin.transaction.output.identifier.ShortTransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class IndexedSpentOutputsInflater implements LevelDb.EntryInflater<ShortTransactionOutputIdentifier, Long> {
    @Override
    public ShortTransactionOutputIdentifier keyFromBytes(final ByteArray byteArray) {
        final Long transactionId = ByteUtil.bytesToLong(byteArray.getBytes(0, 4)); // NOTE: Only stored via 4 bytes.
        final Integer outputIndex = ByteUtil.bytesToInteger(byteArray.getBytes(4, 4));
        return new ShortTransactionOutputIdentifier(transactionId, outputIndex);
    }

    @Override
    public ByteArray keyToBytes(final ShortTransactionOutputIdentifier transactionOutputIdentifier) {
        final Long transactionId = transactionOutputIdentifier.getTransactionId();
        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

        final MutableByteArray byteArray = new MutableByteArray(4 + 4);
        byteArray.setBytes(0, ByteUtil.integerToBytes(transactionId)); // NOTE: Only stored via 4 bytes.
        byteArray.setBytes(4, ByteUtil.integerToBytes(outputIndex));
        return byteArray;
    }

    @Override
    public Long valueFromBytes(final ByteArray byteArray) {
        return ByteUtil.bytesToLong(byteArray.getBytes());
    }

    @Override
    public ByteArray valueToBytes(final Long value) {
        return MutableByteArray.wrap(ByteUtil.integerToBytes(value)); // NOTE: Only stored via 4 bytes.
    }

}
