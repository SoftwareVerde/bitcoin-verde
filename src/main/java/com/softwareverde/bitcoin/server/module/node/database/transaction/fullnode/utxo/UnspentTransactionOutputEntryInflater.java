package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.google.leveldb.LevelDb;
import com.softwareverde.bitcoin.transaction.output.MutableUnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputDeflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputInflater;
import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.token.CashToken;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class UnspentTransactionOutputEntryInflater implements LevelDb.EntryInflater<TransactionOutputIdentifier, UnspentTransactionOutput> {
    protected final TransactionOutputInflater _transactionOutputInflater = new TransactionOutputInflater();
    protected final TransactionOutputDeflater _transactionOutputDeflater = new TransactionOutputDeflater();

    @Override
    public TransactionOutputIdentifier keyFromBytes(final ByteArray byteArray) {
        final Sha256Hash transactionHash = Sha256Hash.wrap(byteArray.getBytes(0, Sha256Hash.BYTE_COUNT));
        final Integer outputIndex = ByteUtil.bytesToInteger(byteArray.getBytes(Sha256Hash.BYTE_COUNT, 4));
        return new TransactionOutputIdentifier(transactionHash, outputIndex);
    }

    @Override
    public ByteArray keyToBytes(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(transactionOutputIdentifier.getTransactionHash());
        byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(transactionOutputIdentifier.getOutputIndex()));
        return byteArrayBuilder;
    }

    @Override
    public UnspentTransactionOutput valueFromBytes(final ByteArray byteArray) {
        if (byteArray == null) { return null; }
        if (byteArray.isEmpty()) { return null; } // Support null values.

        final ByteArrayReader byteArrayReader = new ByteArrayReader(byteArray);

        final Integer outputIndex = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader).intValue();
        final Long blockHeight = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader).value;
        final Boolean isCoinbase = byteArrayReader.readBoolean();
        final Long amount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader).value;

        final MutableUnspentTransactionOutput transactionOutput = new MutableUnspentTransactionOutput();
        transactionOutput.setIndex(outputIndex);
        transactionOutput.setBlockHeight(blockHeight);
        transactionOutput.setIsCoinbase(isCoinbase);
        transactionOutput.setAmount(amount);

        final ByteArray legacyLockingScriptBytes = ByteArray.wrap(
            byteArrayReader.readBytes(
                byteArrayReader.remainingByteCount()
            )
        );
        final Tuple<LockingScript, CashToken> lockingScriptTuple = _transactionOutputInflater.fromLegacyScriptBytes(legacyLockingScriptBytes);
        transactionOutput.setLockingScript(lockingScriptTuple.first);
        transactionOutput.setCashToken(lockingScriptTuple.second);

        return transactionOutput;
    }

    @Override
    public ByteArray valueToBytes(final UnspentTransactionOutput unspentTransactionOutput) {
        if (unspentTransactionOutput == null) { return new MutableByteArray(0); } // Support null values.

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(
            CompactVariableLengthInteger.variableLengthIntegerToBytes(
                unspentTransactionOutput.getIndex()
            )
        );
        byteArrayBuilder.appendBytes(
            CompactVariableLengthInteger.variableLengthIntegerToBytes(
                unspentTransactionOutput.getBlockHeight()
            )
        );
        byteArrayBuilder.appendByte(
            (byte) (unspentTransactionOutput.isCoinbase() ? 0x01 : 0x00)
        );
        byteArrayBuilder.appendBytes(
            CompactVariableLengthInteger.variableLengthIntegerToBytes(
                unspentTransactionOutput.getAmount()
            )
        );
        byteArrayBuilder.appendBytes(
            _transactionOutputDeflater.toLegacyScriptBytes(unspentTransactionOutput)
        );

        return byteArrayBuilder;
    }
}
