package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class CommittedUnspentTransactionOutputInflater {
    public static final Integer NON_LOCKING_SCRIPT_BYTE_COUNT = 52; // The number of bytes for each utxo excluding the LockingScript.

    protected CommittedUnspentTransactionOutput _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final Sha256Hash transactionHash = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT));
        final ByteArray outputIndexBytes = ByteArray.wrap(byteArrayReader.readBytes(4, Endian.LITTLE));
        final ByteArray blockHeightBytes = ByteArray.wrap(byteArrayReader.readBytes(4, Endian.LITTLE));
        final ByteArray amountBytes = ByteArray.wrap(byteArrayReader.readBytes(8, Endian.LITTLE));
        final ByteArray lockingScriptByteCountBytes = ByteArray.wrap(byteArrayReader.readBytes(4, Endian.LITTLE));

        final Integer lockingScriptByteCount = ByteUtil.bytesToInteger(lockingScriptByteCountBytes);
        if (lockingScriptByteCount > LockingScript.MAX_SPENDABLE_SCRIPT_BYTE_COUNT) { return null; }

        final ByteArray lockingScriptBytes = ByteArray.wrap(byteArrayReader.readBytes(lockingScriptByteCount));

        if (byteArrayReader.didOverflow()) { return null; }

        final Long blockHeight = ByteUtil.bytesToLong(blockHeightBytes);
        final Long amount = ByteUtil.bytesToLong(amountBytes);
        final Integer outputIndex = ByteUtil.bytesToInteger(outputIndexBytes);

        final MutableCommittedUnspentTransactionOutput unspentTransactionOutput = new MutableCommittedUnspentTransactionOutput();
        unspentTransactionOutput.setTransactionHash(transactionHash);
        unspentTransactionOutput.setIndex(outputIndex);
        unspentTransactionOutput.setBlockHeight(blockHeight);
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
