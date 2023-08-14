package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockUtxoDiff;
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
import com.softwareverde.bitcoin.util.BlockUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.Visitor;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.filedb.EntryInflater;
import com.softwareverde.filedb.FileDb;
import com.softwareverde.filedb.Item;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.timer.NanoTimer;

import java.io.File;

public class UnspentTransactionOutputFileDbManager implements UnspentTransactionOutputDatabaseManager, AutoCloseable {
    protected final Double _falsePositiveRate = 0.000001D;
    protected final FileDb<TransactionOutputIdentifier, UnspentTransactionOutput> _fileDb;

    public UnspentTransactionOutputFileDbManager(final File dataDirectory, final Long headBlockHeight) throws Exception {
        if (! FileDb.exists(dataDirectory)) {
            FileDb.initialize(dataDirectory);
        }

        _fileDb = new FileDb<>(dataDirectory, new UnspentTransactionOutputEntryInflater());
        _fileDb.setHeadBucketIndex(headBlockHeight);
        _fileDb.setTargetBucketMemoryByteCount(4L * ByteUtil.Unit.Binary.GIBIBYTES);
        _fileDb.setTargetFilterMemoryByteCount(4L * ByteUtil.Unit.Binary.GIBIBYTES);
        _fileDb.load();
        _fileDb.loadIntoMemory();
    }

    @Override
    public void applyBlock(final Block block, final Long blockHeight) throws DatabaseException {
        try {
            final BlockUtxoDiff blockUtxoDiff = BlockUtil.getBlockUtxoDiff(block);

            final Sha256Hash coinbaseTransactionHash = blockUtxoDiff.coinbaseTransactionHash;
            final List<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers = blockUtxoDiff.unspentTransactionOutputIdentifiers;
            final List<TransactionOutput> transactionOutputs = blockUtxoDiff.unspentTransactionOutputs;
            final List<TransactionOutputIdentifier> spentTransactionOutputIdentifiers = blockUtxoDiff.spentTransactionOutputIdentifiers;

            final int outputCount = unspentTransactionOutputIdentifiers.getCount();
            final int spentOutputCount = spentTransactionOutputIdentifiers.getCount();

            final NanoTimer nanoTimer = new NanoTimer();

            nanoTimer.start();
            _fileDb.resizeCapacity(outputCount + spentOutputCount, _falsePositiveRate);
            nanoTimer.stop();
            // Logger.debug("FileDb::resizeCapacity=" + nanoTimer.getMillisecondsElapsed() + "ms.");

            nanoTimer.start();
            for (int i = 0; i < outputCount; ++i) {
                final TransactionOutputIdentifier transactionOutputIdentifier = unspentTransactionOutputIdentifiers.get(i);
                final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                final Boolean isCoinbase = Util.areEqual(coinbaseTransactionHash, transactionHash);

                final TransactionOutput transactionOutput = transactionOutputs.get(i);
                final UnspentTransactionOutput unspentTransactionOutput = new MutableUnspentTransactionOutput(transactionOutput, blockHeight, isCoinbase);
                _fileDb.put(transactionOutputIdentifier, unspentTransactionOutput);
            }
            nanoTimer.stop();
            // Logger.debug("FileDb::put=" + nanoTimer.getMillisecondsElapsed() + "ms.");

            nanoTimer.start();
            for (final TransactionOutputIdentifier spentTransactionOutputIdentifier : spentTransactionOutputIdentifiers) {
                _fileDb.putDeleted(spentTransactionOutputIdentifier);
            }
            nanoTimer.stop();
            // Logger.debug("FileDb::putDeleted=" + nanoTimer.getMillisecondsElapsed() + "ms.");

            nanoTimer.start();
            _fileDb.finalizeBucket();
            nanoTimer.stop();
            // Logger.debug("FileDb::finalizeBucket=" + nanoTimer.getMillisecondsElapsed() + "ms.");
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }

    @Override
    public void undoBlock(final Block block, final Long blockHeight) throws DatabaseException {
        try {
            _fileDb.undoBucket();
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
        try {
            return _fileDb.get(transactionOutputIdentifiers, false);
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
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
        try {
            _fileDb.flush();
            return true;
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
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
                public boolean run(final Tuple<TransactionOutputIdentifier, Item<UnspentTransactionOutput>> entry) {
                    try {
                        if (entry.second.isDeleted()) { return true; }

                        visitor.run(entry.first, entry.second.getValue());

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

    @Override
    public void close() throws Exception {
        _fileDb.close();
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

    @Override
    public int getValueByteCount(final UnspentTransactionOutput unspentTransactionOutput) {
        if (unspentTransactionOutput == null) { return 0; } // Support null values.

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