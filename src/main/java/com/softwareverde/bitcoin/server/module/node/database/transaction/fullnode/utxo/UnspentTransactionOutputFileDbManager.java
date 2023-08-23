package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockUtxoDiff;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.transaction.output.MutableUnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.BlockUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.Visitor;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.filedb.FileDb;
import com.softwareverde.filedb.Item;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.io.File;

public class UnspentTransactionOutputFileDbManager implements UnspentTransactionOutputDatabaseManager, AutoCloseable {
    protected final Double _falsePositiveRate = 0.000001D;
    protected final FileDb<TransactionOutputIdentifier, UnspentTransactionOutput> _fileDb;

    public UnspentTransactionOutputFileDbManager(final File dataDirectory) throws Exception {
        if (! FileDb.exists(dataDirectory)) {
            FileDb.initialize(dataDirectory);
        }

        _fileDb = new FileDb<>(dataDirectory, new UnspentTransactionOutputEntryInflater());
        _fileDb.setName("UtxoDb");
        _fileDb.setTargetBucketMemoryByteCount(0L);
        _fileDb.setTargetFilterMemoryByteCount(ByteUtil.Unit.Binary.GIBIBYTES);
        _fileDb.load();
        _fileDb.loadIntoMemory();
        // _fileDb.createBloomFilters(_falsePositiveRate);
        _fileDb.createMetaFilters();
    }

    public void stashBlocks(final Long count) {
        _fileDb.stashBuckets(count);
    }

    public void applyStashedBlocks() {
        _fileDb.applyStashedBuckets();
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

            if (blockHeight % 1024L == 0L) {
                _fileDb.createMetaFilters();
            }
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
