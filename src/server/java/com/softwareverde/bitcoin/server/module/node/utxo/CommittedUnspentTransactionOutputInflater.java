package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.Endian;

public class CommittedUnspentTransactionOutputInflater {
    public static final Integer NON_LOCKING_SCRIPT_BYTE_COUNT = 52; // The number of bytes for each utxo excluding the LockingScript.

    protected CommittedUnspentTransactionOutput _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final Sha256Hash transactionHash = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.LITTLE));
        final ByteArray outputIndexBytes = ByteArray.wrap(byteArrayReader.readBytes(4, Endian.LITTLE));
        final MutableByteArray blockHeightAndIsCoinbaseBytes = MutableByteArray.wrap(byteArrayReader.readBytes(4, Endian.LITTLE));
        final ByteArray amountBytes = ByteArray.wrap(byteArrayReader.readBytes(8, Endian.LITTLE));
        final Long lockingScriptByteCount = byteArrayReader.readVariableLengthInteger();

        if (lockingScriptByteCount > LockingScript.MAX_SPENDABLE_SCRIPT_BYTE_COUNT) { return null; }
        if (lockingScriptByteCount < 0L) { return null; }

        final ByteArray lockingScriptBytes = ByteArray.wrap(byteArrayReader.readBytes(lockingScriptByteCount.intValue()));

        if (byteArrayReader.didOverflow()) { return null; }

        final Boolean isCoinbase = blockHeightAndIsCoinbaseBytes.getBit(CommittedUnspentTransactionOutput.IS_COINBASE_FLAG_BIT_INDEX);
        blockHeightAndIsCoinbaseBytes.setBit(CommittedUnspentTransactionOutput.IS_COINBASE_FLAG_BIT_INDEX, false);

        final Long blockHeight = ByteUtil.bytesToLong(blockHeightAndIsCoinbaseBytes);
        final Long amount = ByteUtil.bytesToLong(amountBytes);
        final Integer outputIndex = ByteUtil.bytesToInteger(outputIndexBytes);

        final MutableCommittedUnspentTransactionOutput unspentTransactionOutput = new MutableCommittedUnspentTransactionOutput();
        unspentTransactionOutput.setTransactionHash(transactionHash);
        unspentTransactionOutput.setIndex(outputIndex);
        unspentTransactionOutput.setBlockHeight(blockHeight);
        unspentTransactionOutput.setIsCoinbase(isCoinbase);
        unspentTransactionOutput.setAmount(amount);
        unspentTransactionOutput.setLockingScript(lockingScriptBytes);

        return unspentTransactionOutput;
    }

    public CommittedUnspentTransactionOutput fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(byteArrayReader);
    }

    public CommittedUnspentTransactionOutput fromBytes(final ByteArray byteArray) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(byteArray);
        return _fromByteArrayReader(byteArrayReader);
    }
}
