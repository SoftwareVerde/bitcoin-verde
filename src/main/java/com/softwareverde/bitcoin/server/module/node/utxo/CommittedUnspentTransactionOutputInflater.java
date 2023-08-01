package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.transaction.output.TransactionOutputInflater;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.token.CashToken;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class CommittedUnspentTransactionOutputInflater {
    public static final Integer NON_LOCKING_SCRIPT_BYTE_COUNT = 52; // The number of bytes for each utxo excluding the LockingScript.

    protected CommittedUnspentTransactionOutput _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();

        final Sha256Hash transactionHash = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.LITTLE));

        final CompactVariableLengthInteger outputIndexBytes = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
        if (! outputIndexBytes.isCanonical()) { return null; }

        final MutableByteArray blockHeightAndIsCoinbaseBytes = MutableByteArray.wrap(byteArrayReader.readBytes(4, Endian.LITTLE));

        final CompactVariableLengthInteger amountBytes = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
        if (! amountBytes.isCanonical()) { return null; }

        final CompactVariableLengthInteger lockingScriptByteCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
        if (! lockingScriptByteCount.isCanonical()) { return null; }
        if (lockingScriptByteCount.value > LockingScript.MAX_SPENDABLE_SCRIPT_BYTE_COUNT) { return null; }
        if (lockingScriptByteCount.value < 0L) { return null; }

        final ByteArray legacyLockingScriptBytes = ByteArray.wrap(byteArrayReader.readBytes(lockingScriptByteCount.intValue()));
        final Tuple<LockingScript, CashToken> lockingScriptTuple = transactionOutputInflater.fromLegacyScriptBytes(legacyLockingScriptBytes);

        if (byteArrayReader.didOverflow()) { return null; }

        final Boolean isCoinbase = blockHeightAndIsCoinbaseBytes.getBit(CommittedUnspentTransactionOutput.IS_COINBASE_FLAG_BIT_INDEX);

        final Long blockHeight = ByteUtil.bytesToLong(blockHeightAndIsCoinbaseBytes) >> CommittedUnspentTransactionOutput.BLOCK_HEIGHT_BIT_SHIFT_COUNT; // NOTE: The bit shift discards the isCoinbase flag.
        final Long amount = amountBytes.value;
        final Integer outputIndex = (int) outputIndexBytes.value;

        final MutableCommittedUnspentTransactionOutput unspentTransactionOutput = new MutableCommittedUnspentTransactionOutput();
        unspentTransactionOutput.setTransactionHash(transactionHash);
        unspentTransactionOutput.setIndex(outputIndex);
        unspentTransactionOutput.setBlockHeight(blockHeight);
        unspentTransactionOutput.setIsCoinbase(isCoinbase);
        unspentTransactionOutput.setAmount(amount);
        unspentTransactionOutput.setLockingScript(lockingScriptTuple.first);
        unspentTransactionOutput.setCashToken(lockingScriptTuple.second);

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
