package com.softwareverde.bitcoin.server.message.type.dsproof;

import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

import java.util.Comparator;

public class DoubleSpendProofPreimageDeflater {
    protected MutableByteArray _longToBytes(final Long value) {
        final int byteCount = 4;
        final byte[] longBytes = ByteUtil.integerToBytes(value);
        final MutableByteArray byteArray = new MutableByteArray(byteCount);
        ByteUtil.setBytes(byteArray, ByteArray.wrap(longBytes));
        return byteArray;
    }

    protected ByteArray _serializeExtraTransactionOutputDigests(final DoubleSpendProofPreimage doubleSpendProofPreimage, final Boolean includeItemCount, final Endian digestEndian) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        final MutableList<HashType> hashTypes = new MutableList<>(doubleSpendProofPreimage.getExtraTransactionOutputsDigestHashTypes());
        hashTypes.sort(new Comparator<HashType>() {
            @Override
            public int compare(final HashType o1, final HashType o2) {
                return Byte.compare(o1.toByte(), o2.toByte());
            }
        });

        if (includeItemCount) {
            final int itemCount = hashTypes.getCount();
            final byte[] itemCountBytes = ByteUtil.variableLengthIntegerToBytes(itemCount);
            byteArrayBuilder.appendBytes(itemCountBytes);
        }

        for (final HashType hashType : hashTypes) {
            final Sha256Hash transactionOutputsDigest = doubleSpendProofPreimage.getTransactionOutputsDigest(hashType);
            byteArrayBuilder.appendByte((byte) (hashType.toByte() & Mode.BIT_MASK)); // Keep the HashType Mode only...
            byteArrayBuilder.appendBytes(transactionOutputsDigest, digestEndian);
        }

        return byteArrayBuilder;
    }

    public ByteArray serializeExtraTransactionOutputDigestsForSorting(final DoubleSpendProofPreimage doubleSpendProofPreimage) {
        return _serializeExtraTransactionOutputDigests(doubleSpendProofPreimage, false, Endian.LITTLE);
    }

    public ByteArray serializeExtraTransactionOutputDigests(final DoubleSpendProofPreimage doubleSpendProofPreimage) {
        return _serializeExtraTransactionOutputDigests(doubleSpendProofPreimage, true, Endian.BIG);
    }

    public ByteArray toBytes(final DoubleSpendProofPreimage doubleSpendProofPreimage) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        { // Transaction Version
            final Long transactionVersion = doubleSpendProofPreimage.getTransactionVersion();
            final MutableByteArray transactionVersionBytes = _longToBytes(transactionVersion);
            byteArrayBuilder.appendBytes(transactionVersionBytes.unwrap(), Endian.LITTLE); // Does not copy the byte array.
        }

        { // Sequence Number
            final SequenceNumber sequenceNumber = doubleSpendProofPreimage.getSequenceNumber();
            final MutableByteArray sequenceNumberBytes = _longToBytes(sequenceNumber.getValue());
            byteArrayBuilder.appendBytes(sequenceNumberBytes.unwrap(), Endian.LITTLE); // Does not copy the byte array.
        }

        { // Lock Time
            final LockTime lockTime = doubleSpendProofPreimage.getLockTime();
            final MutableByteArray lockTimeBytes = _longToBytes(lockTime.getValue());
            byteArrayBuilder.appendBytes(lockTimeBytes.unwrap(), Endian.LITTLE); // Does not copy the byte array.
        }

        { // Previous Outputs Digest
            final Sha256Hash previousOutputsDigest = doubleSpendProofPreimage.getPreviousOutputsDigest();
            byteArrayBuilder.appendBytes(previousOutputsDigest, Endian.BIG);
        }

        { // Sequence Numbers Digest
            final Sha256Hash sequenceNumbersDigest = doubleSpendProofPreimage.getSequenceNumbersDigest();
            byteArrayBuilder.appendBytes(sequenceNumbersDigest, Endian.BIG);
        }

        { // Transaction Outputs Digest
            final Sha256Hash transactionOutputsDigest = doubleSpendProofPreimage.getExecutedTransactionOutputsDigest();
            byteArrayBuilder.appendBytes(transactionOutputsDigest, Endian.BIG);
        }

        { // Pushed Data
            final List<ByteArray> pushedData = doubleSpendProofPreimage.getUnlockingScriptPushData();
            final int pushCount = pushedData.getCount();
            final ByteArray pushCountBytes = ByteArray.wrap(ByteUtil.variableLengthIntegerToBytes(pushCount));
            byteArrayBuilder.appendBytes(pushCountBytes, Endian.BIG);

            for (ByteArray byteArray : pushedData) {
                final int byteCount = byteArray.getByteCount();
                final ByteArray byteArrayByteCountBytes = ByteArray.wrap(ByteUtil.variableLengthIntegerToBytes(byteCount));
                byteArrayBuilder.appendBytes(byteArrayByteCountBytes, Endian.BIG);
                byteArrayBuilder.appendBytes(byteArray, Endian.BIG);
            }
        }

        { // TODO: append hash-outputs softfork data...

        }

        return byteArrayBuilder;
    }
}
