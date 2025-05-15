package com.softwareverde.bitcoin.server.message.type.dsproof;

import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableSequenceNumber;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class DoubleSpendProofPreimageInflater {
    protected MutableDoubleSpendProofPreimage _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final MutableDoubleSpendProofPreimage doubleSpendProofPreimage = new MutableDoubleSpendProofPreimage();

        final Long transactionVersion = byteArrayReader.readLong(4, Endian.LITTLE);
        doubleSpendProofPreimage.setTransactionVersion(transactionVersion);

        final Long sequenceNumberValue = byteArrayReader.readLong(4, Endian.LITTLE);
        final SequenceNumber sequenceNumber = new ImmutableSequenceNumber(sequenceNumberValue);
        doubleSpendProofPreimage.setSequenceNumber(sequenceNumber);

        final Long lockTimeValue = byteArrayReader.readLong(4, Endian.LITTLE);
        final LockTime lockTime = new ImmutableLockTime(lockTimeValue);
        doubleSpendProofPreimage.setLockTime(lockTime);

        final Sha256Hash previousOutputsDigest = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.BIG));
        doubleSpendProofPreimage.setPreviousOutputsDigest(previousOutputsDigest);

        final Sha256Hash sequenceNumbersDigest = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.BIG));
        doubleSpendProofPreimage.setSequenceNumbersDigest(sequenceNumbersDigest);

        final Sha256Hash transactionOutputsDigest = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.BIG));
        doubleSpendProofPreimage.setExecutedTransactionOutputsDigest(transactionOutputsDigest);

        final CompactVariableLengthInteger pushDataCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
        if (! pushDataCount.isCanonical()) { return null; }
        // if (pushDataCount.value > Script.MAX_OPERATION_COUNT) { return null; } // Disabled in 20250515
        for (int i = 0; i < pushDataCount.value; ++i) {
            final CompactVariableLengthInteger pushDataByteCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
            if (! pushDataByteCount.isCanonical()) { return null; }
            if (pushDataByteCount.value > PushOperation.VALUE_MAX_BYTE_COUNT) { return null; }

            final ByteArray pushedData = ByteArray.wrap(byteArrayReader.readBytes(pushDataByteCount.intValue(), Endian.BIG));
            doubleSpendProofPreimage.addUnlockingScriptPushData(pushedData);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return doubleSpendProofPreimage;
    }

    public MutableDoubleSpendProofPreimage fromBytes(final ByteArray byteArray) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(byteArray);
        return _fromByteArrayReader(byteArrayReader);
    }

    public MutableDoubleSpendProofPreimage fromBytes(final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(byteArrayReader);
    }

    public void parseExtraTransactionOutputsDigests(final ByteArrayReader byteArrayReader, final MutableDoubleSpendProofPreimage doubleSpendProofPreimage) {
        if (byteArrayReader.remainingByteCount() < 1) { return; }

        final CompactVariableLengthInteger extraDigestCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
        if (! extraDigestCount.isCanonical()) { return; }
        // if (extraDigestCount.value > Script.MAX_OPERATION_COUNT) { return; } // Disabled in 20250515

        for (int i = 0; i < extraDigestCount.value; ++i) {
            final byte hashTypeByte = byteArrayReader.readByte();
            final Sha256Hash extraTransactionOutputDigest = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT));
            if (byteArrayReader.didOverflow()) { return; }

            final HashType hashType = HashType.fromByte(hashTypeByte);
            doubleSpendProofPreimage.setTransactionOutputsDigest(hashType, extraTransactionOutputDigest);
        }
    }
}
