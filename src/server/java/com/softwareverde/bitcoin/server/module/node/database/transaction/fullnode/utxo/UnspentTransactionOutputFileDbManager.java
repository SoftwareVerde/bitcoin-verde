package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.transaction.output.MutableUnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputDeflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputInflater;
import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.token.CashToken;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.Visitor;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.filedb.EntryInflater;
import com.softwareverde.filedb.FileDb;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.ByteArrayReader;

import java.io.File;

public class UnspentTransactionOutputFileDbManager implements UnspentTransactionOutputDatabaseManager {
    protected final FileDb<TransactionOutputIdentifier, UnspentTransactionOutput> _fileDb;

    public UnspentTransactionOutputFileDbManager(final File dataDirectory) throws Exception {
        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();

        if (! FileDb.exists(dataDirectory)) {
            FileDb.initialize(dataDirectory, 1117902, 2097152, 7);
        }

        _fileDb = new FileDb<>(dataDirectory, new UnspentTransactionOutputEntryInflater());
    }

    @Override
    public void markTransactionOutputsAsSpent(final List<TransactionOutputIdentifier> spentTransactionOutputIdentifiers) throws DatabaseException {
        try {
            for (final TransactionOutputIdentifier transactionOutputIdentifier : spentTransactionOutputIdentifiers) {
                _fileDb.setDeleted(transactionOutputIdentifier, true);
            }
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }

    @Override
    public void insertUnspentTransactionOutputs(final List<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers, final List<TransactionOutput> transactionOutputs, final Long blockHeight, final Sha256Hash coinbaseTransactionHash) throws DatabaseException {
        try {
            final int outputCount = unspentTransactionOutputIdentifiers.getCount();
            for (int i = 0; i < outputCount; ++i) {
                final TransactionOutputIdentifier transactionOutputIdentifier = unspentTransactionOutputIdentifiers.get(i);
                final TransactionOutput transactionOutput = transactionOutputs.get(i);

                final Boolean isCoinbase = Util.areEqual(coinbaseTransactionHash, transactionOutputIdentifier.getTransactionHash());

                final UnspentTransactionOutput unspentTransactionOutput = new MutableUnspentTransactionOutput(transactionOutput, blockHeight, isCoinbase);

                _fileDb.put(transactionOutputIdentifier, unspentTransactionOutput);
            }
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }

    @Override
    public void undoCreationOfTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        try {
            for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
                _fileDb.setDeleted(transactionOutputIdentifier, true);
            }
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }

    @Override
    public void undoSpendingOfTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers, final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        try {
            for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
                _fileDb.setDeleted(transactionOutputIdentifier, false);
            }
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }

    @Override
    public UnspentTransactionOutput getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        try {
            return _fileDb.get(transactionOutputIdentifier);
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }

    @Override
    public UnspentTransactionOutput loadUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        return this.getUnspentTransactionOutput(transactionOutputIdentifier);
    }

    @Override
    public List<UnspentTransactionOutput> getUnspentTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        final MutableList<UnspentTransactionOutput> unspentTransactionOutputs = new MutableArrayList<>(
            transactionOutputIdentifiers.getCount()
        );

        try {
            for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
                final UnspentTransactionOutput unspentTransactionOutput = _fileDb.get(transactionOutputIdentifier);
                unspentTransactionOutputs.add(unspentTransactionOutput);
            }
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }

        return unspentTransactionOutputs;
    }

    @Override
    public List<TransactionOutputIdentifier> getFastSyncOutputIdentifiers(final Sha256Hash transactionHash) throws DatabaseException {
        final MutableList<TransactionOutputIdentifier> unspentTransactionOutputs = new MutableArrayList<>(1);

        try {
            int outputIndex = 0;
            while (true) {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                final UnspentTransactionOutput unspentTransactionOutput = _fileDb.get(transactionOutputIdentifier, true);
                if (unspentTransactionOutput == null) { break; }

                unspentTransactionOutputs.add(transactionOutputIdentifier);
                outputIndex += 1;
            }
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }

        return unspentTransactionOutputs;
    }

    @Override
    public Boolean commitUnspentTransactionOutputs(final DatabaseManagerFactory databaseManagerFactory, final CommitAsyncMode commitAsyncMode) throws DatabaseException {
        return true;
    }

    @Override
    public Long getUncommittedUnspentTransactionOutputCount() throws DatabaseException {
        return 0L;
    }

    @Override
    public Long getUncommittedUnspentTransactionOutputCount(final Boolean noLock) throws DatabaseException {
        return 0L;
    }

    @Override
    public Long getCommittedUnspentTransactionOutputBlockHeight() throws DatabaseException {
        return _blockHeight;
    }

    @Override
    public Long getCommittedUnspentTransactionOutputBlockHeight(final Boolean noLock) throws DatabaseException {
        return _blockHeight;
    }

    protected Long _blockHeight = 0L;
    @Override
    public void setUncommittedUnspentTransactionOutputBlockHeight(final Long blockHeight) throws DatabaseException {
        _blockHeight = blockHeight;
    }

    @Override
    public Long getUncommittedUnspentTransactionOutputBlockHeight() throws DatabaseException {
        return _blockHeight;
    }

    @Override
    public Long getUncommittedUnspentTransactionOutputBlockHeight(final Boolean noLock) throws DatabaseException {
        return _blockHeight;
    }

    @Override
    public void clearCommittedUtxoSet() throws DatabaseException {
        try {
            _fileDb.destroy();
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }

    @Override
    public void clearUncommittedUtxoSet() throws DatabaseException {

    }

    @Override
    public Long getMaxUtxoCount() {
        return Long.MAX_VALUE;
    }

    @Override
    public UnspentTransactionOutput findOutputData(final TransactionOutputIdentifier transactionOutputIdentifier, final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        try {
            return _fileDb.get(transactionOutputIdentifier, true);
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }

    @Override
    public void visitUnspentTransactionOutputs(final UnspentTransactionOutputVisitor visitor) throws DatabaseException {
        try {
            _fileDb.visitEntries(new Visitor<>() {
                @Override
                public boolean run(final Tuple<TransactionOutputIdentifier, Tuple<UnspentTransactionOutput, Boolean>> entry) {
                    try {
                        if (entry.second.second) { return true; }

                        visitor.run(entry.first, entry.second.first);

                        return true;
                    }
                    catch (final Exception exception) {
                        return false;
                    }
                }
            });
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }
}

class UnspentTransactionOutputEntryInflater implements EntryInflater<TransactionOutputIdentifier, UnspentTransactionOutput> {
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
    public int getKeyByteCount() {
        return Sha256Hash.BYTE_COUNT + 4;
    }

    @Override
    public UnspentTransactionOutput valueFromBytes(final ByteArray byteArray) {
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

    @Override
    public int getValueByteCount(final UnspentTransactionOutput unspentTransactionOutput) {
        int byteCount = 0;

        byteCount += CompactVariableLengthInteger.variableLengthIntegerToBytes(
            unspentTransactionOutput.getIndex()
        ).getByteCount();

        byteCount += CompactVariableLengthInteger.variableLengthIntegerToBytes(
            unspentTransactionOutput.getBlockHeight()
        ).getByteCount();

        byteCount += 1;

        byteCount += CompactVariableLengthInteger.variableLengthIntegerToBytes(
            unspentTransactionOutput.getAmount()
        ).getByteCount();

        byteCount += _transactionOutputDeflater.getLegacyScriptByteCount(unspentTransactionOutput);

        return byteCount;
    }
}