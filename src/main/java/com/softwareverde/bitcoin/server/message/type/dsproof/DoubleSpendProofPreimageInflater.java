package com.softwareverde.bitcoin.server.message.type.dsproof;

import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableSequenceNumber;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.runner.ScriptRunner;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
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

        final Long pushDataCount = byteArrayReader.readVariableSizedInteger();
        if (pushDataCount > ScriptRunner.MAX_OPERATION_COUNT) { return null; }
        for (int i = 0; i < pushDataCount; ++i) {
            final Long pushDataByteCount = byteArrayReader.readVariableSizedInteger();
            if (pushDataByteCount > PushOperation.VALUE_MAX_BYTE_COUNT) { return null; }

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

        final Long extraDigestCount = byteArrayReader.readVariableSizedInteger();
        if (extraDigestCount > ScriptRunner.MAX_OPERATION_COUNT) { return; }

        for (int i = 0; i < extraDigestCount; ++i) {
            final byte hashTypeByte = byteArrayReader.readByte();
            final Sha256Hash extraTransactionOutputDigest = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT));
            if (byteArrayReader.didOverflow()) { return; }

            final HashType hashType = HashType.fromByte(hashTypeByte);
            doubleSpendProofPreimage.setTransactionOutputsDigest(hashType, extraTransactionOutputDigest);
        }
    }
}
