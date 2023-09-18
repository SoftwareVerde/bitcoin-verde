package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.transaction.output.identifier.ShortTransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.btreedb.BucketDb;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.util.HashUtil;

public class IndexedSpentOutputsInflater implements BucketDb.BucketEntryInflater<ShortTransactionOutputIdentifier, Long> {
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
    public int getKeyByteCount() {
        return (4 + 4);
    }

    @Override
    public Long valueFromBytes(final ByteArray byteArray) {
        return ByteUtil.bytesToLong(byteArray.getBytes());
    }

    @Override
    public ByteArray valueToBytes(final Long value) {
        return MutableByteArray.wrap(ByteUtil.integerToBytes(value)); // NOTE: Only stored via 4 bytes.
    }

    @Override
    public int getValueByteCount(final Long bytes) {
        return 4; // NOTE: Only stored via 4 bytes.
    }

    @Override
    public Sha256Hash getHash(final ShortTransactionOutputIdentifier transactionOutputIdentifier) {
        final Long transactionId = transactionOutputIdentifier.getTransactionId();
        final ByteArray preImage = MutableByteArray.wrap(ByteUtil.longToBytes(transactionId));
        return HashUtil.sha256(preImage);
    }
}
