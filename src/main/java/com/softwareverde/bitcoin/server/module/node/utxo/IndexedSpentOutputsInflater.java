package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.btreedb.BucketDb;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class IndexedSpentOutputsInflater implements BucketDb.BucketEntryInflater<TransactionOutputIdentifier, Sha256Hash> {
    @Override
    public TransactionOutputIdentifier keyFromBytes(final ByteArray byteArray) {
        final Sha256Hash transactionHash = Sha256Hash.wrap(byteArray.getBytes(0, Sha256Hash.BYTE_COUNT));
        final Integer outputIndex = ByteUtil.bytesToInteger(byteArray.getBytes(Sha256Hash.BYTE_COUNT, 4));
        return new TransactionOutputIdentifier(transactionHash, outputIndex);
    }

    @Override
    public ByteArray keyToBytes(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

        final MutableByteArray byteArray = new MutableByteArray(Sha256Hash.BYTE_COUNT + 4);
        byteArray.setBytes(0, transactionHash);
        byteArray.setBytes(Sha256Hash.BYTE_COUNT, ByteUtil.integerToBytes(outputIndex));
        return byteArray;
    }

    @Override
    public int getKeyByteCount() {
        return (Sha256Hash.BYTE_COUNT + 4);
    }

    @Override
    public Sha256Hash valueFromBytes(final ByteArray byteArray) {
        return Sha256Hash.wrap(byteArray.getBytes());
    }

    @Override
    public ByteArray valueToBytes(final Sha256Hash bytes) {
        return bytes;
    }

    @Override
    public int getValueByteCount(final Sha256Hash bytes) {
        return Sha256Hash.BYTE_COUNT;
    }

    @Override
    public Sha256Hash getHash(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return transactionOutputIdentifier.getTransactionHash();
    }
}
