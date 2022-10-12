package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockUtxoDiff;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitment;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentId;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentManager;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UndoLogDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputJvmManager;
import com.softwareverde.bitcoin.server.module.node.database.utxo.UtxoCommitmentDatabaseManager;
import com.softwareverde.bitcoin.server.properties.PropertiesStore;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.util.BlockUtil;
import com.softwareverde.concurrent.service.PausableSleepyService;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.EcMultiset;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.query.parameter.InClauseParameter;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.Container;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;
import com.softwareverde.util.timer.MultiTimer;
import com.softwareverde.util.timer.NanoTimer;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class UtxoCommitmentGenerator extends PausableSleepyService {
    protected static final String STAGED_COMMITMENT_BLOCK_HEIGHT_KEY = "staged_utxo_commitment_block_height";
    protected static final Integer UTXO_COMMITMENT_BLOCK_LAG = UndoLogDatabaseManager.MAX_REORG_DEPTH;
    protected static final Long MIN_BLOCK_HEIGHT = 650000L;

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final Long _publishCommitInterval = 10000L;
    protected final Integer _maxCommitmentsToKeep = 2;
    protected final String _outputDirectory;
    protected final Integer _utxoCommitmentBlockLag;

    protected Long _cachedStagedUtxoCommitmentBlockHeight = null;

    protected Long _getStagedUtxoCommitmentBlockHeight(final DatabaseManager databaseManager) throws DatabaseException {
        final PropertiesStore propertiesStore = databaseManager.getPropertiesStore();
        final Long blockHeight = Util.coalesce(propertiesStore.getLong(STAGED_COMMITMENT_BLOCK_HEIGHT_KEY));

        _cachedStagedUtxoCommitmentBlockHeight = blockHeight;

        return blockHeight;
    }

    protected void _setStagedUtxoCommitmentBlockHeight(final Long blockHeight, final DatabaseManager databaseManager) {
        final PropertiesStore propertiesStore = databaseManager.getPropertiesStore();
        propertiesStore.set(STAGED_COMMITMENT_BLOCK_HEIGHT_KEY, blockHeight);

        _cachedStagedUtxoCommitmentBlockHeight = blockHeight;
    }

    protected void _importFromCommittedUtxos(final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();

        final int batchSize = 1048576; // Uses a higher batch size instead of databaseManager.getMaxQueryBatchSize since results are not returned...

        databaseConnection.executeSql(
            new Query("DELETE FROM staged_utxo_commitment")
        );
        _setStagedUtxoCommitmentBlockHeight(0L, databaseManager);

        UnspentTransactionOutputJvmManager.COMMITTED_UTXO_TABLE_WRITE_LOCK.lock();
        try {
            Logger.debug("UTXO double buffer lock acquired.");

            final BlockId headBlockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            if (headBlockId == null) { return; }

            final Long headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);
            final Long committedUtxoBlockHeight = unspentTransactionOutputDatabaseManager.getCommittedUnspentTransactionOutputBlockHeight(true);
            if (committedUtxoBlockHeight > (headBlockHeight - _utxoCommitmentBlockLag)) {
                Logger.debug("Committed UTXO set surpasses UTXO Commitment lag, not importing UTXOs from committed buffer.");
                return;
            }

            TransactionOutputIdentifier previousTransactionOutputIdentifier = new TransactionOutputIdentifier(Sha256Hash.EMPTY_HASH, -1);
            while (true) {
                final Sha256Hash previousTransactionHash = previousTransactionOutputIdentifier.getTransactionHash();
                final Integer previousOutputIndex = previousTransactionOutputIdentifier.getOutputIndex();

                databaseConnection.executeSql(
                    new Query("INSERT INTO staged_utxo_commitment SELECT * FROM committed_unspent_transaction_outputs WHERE ((transaction_hash > ?) OR (transaction_hash = ? AND `index` > ?)) AND (amount > 0) ORDER BY transaction_hash ASC, `index` ASC LIMIT " + batchSize)
                        .setParameter(previousTransactionHash)
                        .setParameter(previousTransactionHash)
                        .setParameter(previousOutputIndex)
                );

                final java.util.List<Row> rows = databaseConnection.query(
                    new Query("SELECT transaction_hash, `index` FROM staged_utxo_commitment ORDER BY transaction_hash DESC, `index` DESC LIMIT 1")
                );
                if (rows.isEmpty()) { break; }

                final Row row = rows.get(0);
                final Sha256Hash transactionHash = Sha256Hash.wrap(row.getBytes("transaction_hash"));
                final Integer outputIndex = row.getInteger("index");

                if (Util.areEqual(previousTransactionOutputIdentifier.getTransactionHash(), transactionHash) && Util.areEqual(previousTransactionOutputIdentifier.getOutputIndex(), outputIndex)) {
                    break;
                }

                previousTransactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
            }

            _setStagedUtxoCommitmentBlockHeight(committedUtxoBlockHeight, databaseManager);
        }
        finally {
            UnspentTransactionOutputJvmManager.COMMITTED_UTXO_TABLE_WRITE_LOCK.unlock();
            Logger.debug("UTXO double buffer lock released.");
        }
    }

    protected int _calculateBucketIndex(final Sha256Hash blockHash, final TransactionOutputIdentifier transactionOutputIdentifier) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(blockHash, Endian.LITTLE);
        byteArrayBuilder.appendBytes(transactionOutputIdentifier.getTransactionHash(), Endian.LITTLE);
        byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(transactionOutputIdentifier.getOutputIndex()), Endian.LITTLE);

        final Sha256Hash utxoHash = HashUtil.sha256(byteArrayBuilder);
        return (utxoHash.getByte(0) & 0x7F);
    }

    protected UtxoCommitment _publishUtxoCommitment(final BlockId blockId, final Sha256Hash blockHash, final Long commitBlockHeight, final FullNodeDatabaseManager databaseManager) throws Exception {
        final UtxoCommitmentDatabaseManager utxoCommitmentDatabaseManager = databaseManager.getUtxoCommitmentDatabaseManager();
        { // Check if a UTXO commitment already exists for this blockId...
            // NOTE: This can happen if the node has imported a UTXO set and/or if UTXO sets are being manually regenerated.
            final UtxoCommitmentManager utxoCommitmentManager = databaseManager.getUtxoCommitmentManager();
            final UtxoCommitmentId utxoCommitmentId = utxoCommitmentManager.getUtxoCommitmentId(blockId);
            if (utxoCommitmentId != null) {
                final Sha256Hash utxoCommitmentHash = utxoCommitmentDatabaseManager.getUtxoCommitmentHash(utxoCommitmentId);
                if (utxoCommitmentHash != null) {
                    Logger.debug("UTXO commitment already exists for block " + blockHash + ".");
                    return null;
                }

                final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
                databaseConnection.executeSql(
                    new Query("DELETE FROM utxo_commitments WHERE id = ?")
                        .setParameter(utxoCommitmentId)
                );
            }
        }

        Logger.debug("Creating UTXO commitment.");
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final File outputDirectory = new File(_outputDirectory);
        if ( (! outputDirectory.exists()) || (! outputDirectory.canWrite()) ) {
            throw new DatabaseException("Unable to write UTXO commit to directory: " + _outputDirectory);
        }

        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final UtxoCommitmentId utxoCommitmentId = utxoCommitmentDatabaseManager.createUtxoCommitment(blockId);
        final EcMultiset utxoCommitMultiset = new EcMultiset();
        final MutableList<File> utxoCommitmentFiles = new MutableList<>(UtxoCommitment.BUCKET_COUNT);

        final int batchSize = Math.min(1024, databaseManager.getMaxQueryBatchSize());
        final AtomicInteger objectMemoryCount = new AtomicInteger(0);

        final MutableList<BucketFile> bucketQueues = new MutableList<>(UtxoCommitment.BUCKET_COUNT);
        for (int i = 0; i < UtxoCommitment.BUCKET_COUNT; ++i) {
            bucketQueues.add(new BucketFile(i, _outputDirectory, UtxoCommitment.MAX_BUCKET_BYTE_COUNT));
        }

        final Runtime runtime = Runtime.getRuntime();
        final int threadCount = Math.max(1, (runtime.availableProcessors() / 2));
        final Container<Exception> exceptionContainer = new Container<>(null);
        final MutableList<Tuple<Thread, AtomicBoolean>> threads = new MutableList<>(threadCount);
        for (int i = 0; i < threadCount; ++i) {
            final int minIndex = i * (UtxoCommitment.BUCKET_COUNT / threadCount);
            final int maxIndex;
            if (i < (threadCount - 1)) {
                maxIndex = (((i + 1) * (UtxoCommitment.BUCKET_COUNT / threadCount)) - 1);
            }
            else { // Account for bucketCount non-divisible by threadCount...
                maxIndex = (UtxoCommitment.BUCKET_COUNT - 1);
            }

            final AtomicBoolean jobIsComplete = new AtomicBoolean(false);
            final Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    final Thread thread = Thread.currentThread();
                    try {
                        while (! thread.isInterrupted()) {
                            final NanoTimer nanoTimer = new NanoTimer();
                            nanoTimer.start();

                            int processedCount = 0;
                            for (int i = minIndex; i <= maxIndex; ++i) {
                                final BucketFile bucket = bucketQueues.get(i);
                                final CommittedUnspentTransactionOutput transactionOutput = bucket.pollFromQueue();
                                if (transactionOutput == null) { continue; }

                                objectMemoryCount.decrementAndGet();
                                bucket.addOutput(transactionOutput);
                                processedCount += 1;
                            }

                            if (processedCount == 0) {
                                if (jobIsComplete.get()) {
                                    break;
                                }

                                Thread.sleep(100);
                            }
                            nanoTimer.stop();

                            if (Logger.isTraceEnabled()) {
                                Logger.trace(thread.getName() + " processed " + processedCount + " outputs in " + nanoTimer.getMillisecondsElapsed() + "ms.");
                            }
                        }
                    }
                    catch (final Exception exception) {
                        exceptionContainer.value = exception;
                        Logger.trace(exception);
                    }
                    finally {
                        for (int i = minIndex; i <= maxIndex; ++i) {
                            try {
                                final BucketFile bucket = bucketQueues.get(i);
                                bucket.close();
                            }
                            catch (final Exception exception) {
                                Logger.debug(exception);
                            }
                        }

                        Logger.debug(thread.getName() + " exited.");
                    }
                }
            });
            thread.setName("UtxoCommitGenerator Thread " + minIndex + "-" + maxIndex);
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(final Thread thread, final Throwable exception) {
                    Logger.debug(exception);
                }
            });

            threads.add(new Tuple<>(thread, jobIsComplete));
            thread.start();
        }

        try {
            final UtxoCommitmentCore utxoCommitment = new UtxoCommitmentCore();
            TransactionOutputIdentifier previousTransactionOutputIdentifier = new TransactionOutputIdentifier(Sha256Hash.EMPTY_HASH, -1);

            while (true) {
                if (_shouldAbort()) { throw new DatabaseException("UTXO Commit aborted."); }
                if (exceptionContainer.value != null) { break; }

                final java.util.List<Row> rows;
                {
                    final NanoTimer queryTimer = new NanoTimer();
                    queryTimer.start();
                    final Sha256Hash transactionHash = previousTransactionOutputIdentifier.getTransactionHash();
                    final Integer outputIndex = previousTransactionOutputIdentifier.getOutputIndex();
                    rows = databaseConnection.query(
                        new Query("SELECT transaction_hash, `index`, block_height, is_coinbase, amount, locking_script FROM staged_utxo_commitment WHERE (transaction_hash > ?) OR (transaction_hash = ? AND `index` > ?) ORDER BY transaction_hash ASC, `index` ASC LIMIT " + batchSize)
                            .setParameter(transactionHash)
                            .setParameter(transactionHash)
                            .setParameter(outputIndex)
                    );
                    queryTimer.stop();
                    Logger.trace("queryTimer=" + queryTimer.getMillisecondsElapsed() + "ms.");
                }
                if (rows.isEmpty()) { break; }

                final MultiTimer bucketQueueTimer = new MultiTimer();
                bucketQueueTimer.start();

                for (final Row row : rows) {
                    final Sha256Hash transactionHash = Sha256Hash.wrap(row.getBytes("transaction_hash"));
                    final Integer outputIndex = row.getInteger("index");

                    final Long blockHeight = row.getLong("block_height");
                    final Boolean isCoinbase = row.getBoolean("is_coinbase");
                    final Long amount = row.getLong("amount");
                    final ByteArray lockingScriptBytes = ByteArray.wrap(row.getBytes("locking_script"));
                    bucketQueueTimer.mark("row values");

                    if (amount <= 0L) { continue; } // NOTE: Should not happen, and is excessively defensive. (committed_unspent_transaction_outputs table possibly using negative amounts during reorg, but are excluded from the IBD import...)

                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);

                    final MutableCommittedUnspentTransactionOutput committedUnspentTransactionOutput = new MutableCommittedUnspentTransactionOutput();
                    committedUnspentTransactionOutput.setTransactionHash(transactionHash);
                    committedUnspentTransactionOutput.setIndex(outputIndex);
                    committedUnspentTransactionOutput.setBlockHeight(blockHeight);
                    committedUnspentTransactionOutput.setIsCoinbase(isCoinbase);
                    committedUnspentTransactionOutput.setAmount(amount);
                    committedUnspentTransactionOutput.setLockingScript(lockingScriptBytes);
                    bucketQueueTimer.mark("committedUnspentTransactionOutput inflation");

                    final int bucketIndex = _calculateBucketIndex(blockHash, transactionOutputIdentifier);
                    bucketQueueTimer.mark("calculateBucketIndex");
                    final BucketFile bucket = bucketQueues.get(bucketIndex);
                    bucketQueueTimer.mark("get bucket");
                    try {
                        bucketQueueTimer.mark("queue add");
                        while (objectMemoryCount.get() > 16384) { // NOTE: About 1.25 MB.
                            Thread.sleep(10); // Throttle to prevent oom...
                            bucketQueueTimer.mark("sleep");
                        }

                        bucket.addToQueue(committedUnspentTransactionOutput);
                        objectMemoryCount.incrementAndGet();
                        bucketQueueTimer.mark("atomic int");
                    }
                    catch (final InterruptedException exception) {
                        throw new DatabaseException(exception);
                    }
                }

                bucketQueueTimer.stop("end");
                if (Logger.isTraceEnabled()) {
                    // Logger.trace("bucketQueueTimer=" + bucketQueueTimer.getMillisecondsElapsed() + "ms, rowCount=" + rows.size() + " queue=" + objectMemoryCount.get());
                    Logger.trace(bucketQueueTimer);
                }

                { // Update the previousTransactionOutputIdentifier for the next loop...
                    final int rowCount = rows.size();
                    final Row row = rows.get(rowCount - 1);
                    final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("transaction_hash"));
                    final Integer outputIndex = row.getInteger("index");

                    previousTransactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                }
            }

            if (exceptionContainer.value != null) {
                throw exceptionContainer.value;
            }

            for (final Tuple<Thread, AtomicBoolean> tuple : threads) {
                final Thread thread = tuple.first;
                final AtomicBoolean jobsIsComplete = tuple.second;

                jobsIsComplete.set(true);

                try {
                    thread.join();
                }
                catch (final Exception exception) {
                    final Thread currentThread = Thread.currentThread();
                    currentThread.interrupt();
                }
            }

            for (final BucketFile bucket : bucketQueues) {
                final Integer bucketIndex = bucket.getIndex();
                final PublicKey bucketPublicKey = bucket.getPublicKey();
                final Long bucketId = utxoCommitmentDatabaseManager.createUtxoCommitmentBucket(utxoCommitmentId, bucketIndex, bucketPublicKey);

                int subBucketIndex = 0;
                for (final BucketFile.SubBucketFile subBucket : bucket.getSubBuckets()) {
                    utxoCommitmentDatabaseManager.createUtxoCommitmentFile(bucketId, subBucketIndex, subBucket.publicKey, subBucket.utxoCount, subBucket.byteCount);

                    utxoCommitmentFiles.add(subBucket.file);
                    subBucketIndex += 1;
                }

                utxoCommitMultiset.add(bucketPublicKey);
            }

            final PublicKey publicKey = utxoCommitMultiset.getPublicKey();
            final Sha256Hash commitHash = utxoCommitMultiset.getHash();
            utxoCommitmentDatabaseManager.setUtxoCommitmentHash(utxoCommitmentId, commitHash, publicKey);

            utxoCommitment._blockId = blockId;
            utxoCommitment._blockHeight = commitBlockHeight;
            utxoCommitment._multiset = utxoCommitMultiset;
            utxoCommitment._files.addAll(utxoCommitmentFiles);

            nanoTimer.stop();
            Logger.trace("Created UTXO Commitment in " + nanoTimer.getMillisecondsElapsed() + "ms.");

            return utxoCommitment;
        }
        finally {
            for (final Tuple<Thread, AtomicBoolean> tuple : threads) {
                final Thread thread = tuple.first;
                thread.interrupt();
            }
        }
    }

    protected void _deleteOldUtxoCommitments(final DatabaseConnection databaseConnection) throws DatabaseException {
        final HashSet<UtxoCommitmentId> commitmentsToKeep = new HashSet<>(_maxCommitmentsToKeep);
        { // Select old UTXO commitments...
            final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT utxo_commitments.id FROM utxo_commitments INNER JOIN blocks ON blocks.id = utxo_commitments.block_id ORDER BY blocks.block_height DESC LIMIT " + _maxCommitmentsToKeep));
            for (final Row row : rows) {
                final UtxoCommitmentId utxoCommitmentId = UtxoCommitmentId.wrap(row.getLong("id"));
                commitmentsToKeep.add(utxoCommitmentId);
            }
        }

        { // Keep the "trusted" UTXO commitments from that recent versions of Bitcoin Verde nodes are expected to download.
            final List<UtxoCommitmentMetadata> utxoCommitmentMetadataList = BitcoinConstants.getUtxoCommitments();
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT id FROM utxo_commitments WHERE public_key IN (?)")
                    .setInClauseParameters(utxoCommitmentMetadataList, new ValueExtractor<UtxoCommitmentMetadata>() {
                        @Override
                        public InClauseParameter extractValues(final UtxoCommitmentMetadata value) {
                            return ValueExtractor.BYTE_ARRAY.extractValues(value.publicKey);
                        }
                    })
            );
            for (final Row row : rows) {
                final UtxoCommitmentId utxoCommitmentId = UtxoCommitmentId.wrap(row.getLong("id"));
                commitmentsToKeep.add(utxoCommitmentId);
            }
        }

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM utxo_commitments WHERE id NOT IN (?)")
                .setInClauseParameters(commitmentsToKeep, ValueExtractor.IDENTIFIER)
        );
        for (final Row row : rows) {
            final UtxoCommitmentId utxoCommitmentId = UtxoCommitmentId.wrap(row.getLong("id"));
            final Sha256Hash commitmentHash = Sha256Hash.wrap(row.getBytes("hash"));
            final java.util.List<Row> fileRows = databaseConnection.query(
                new Query("SELECT utxo_commitment_files.public_key FROM utxo_commitment_files INNER JOIN utxo_commitment_buckets ON utxo_commitment_buckets.id = utxo_commitment_files.utxo_commitment_bucket_id WHERE utxo_commitment_buckets.utxo_commitment_id = ?")
                    .setParameter(utxoCommitmentId)
            );

            for (final Row fileRow : fileRows) {
                final PublicKey publicKey = PublicKey.fromBytes(fileRow.getBytes("public_key"));
                final File newFile = new File(_outputDirectory, publicKey.toString());
                newFile.delete();
            }

            databaseConnection.executeSql(
                new Query("DELETE FROM utxo_commitments WHERE id = ?")
                    .setParameter(utxoCommitmentId)
            );

            Logger.info("Deleted UTXO commitment: " + commitmentHash);
        }
    }

    protected void _updateStagedCommitment(final FullNodeDatabaseManager databaseManager) throws Exception {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

        final int batchSize = Math.min(1024, databaseManager.getMaxQueryBatchSize());
        final BatchRunner<TransactionOutputIdentifier> identifierBatchRunner = new BatchRunner<>(batchSize);

        long stagedUtxoBlockHeight = _getStagedUtxoCommitmentBlockHeight(databaseManager);
        final BlockId headBlockHeaderId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
        final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
        if ( (headBlockId == null) || (headBlockHeaderId == null) ) { return; }

        final Long headBlockHeaderHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockHeaderId);
        final Long headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);
        final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(headBlockId); // TODO: Ensure this does not change mid-processing due to reorg.

        stagedUtxoBlockHeight = (stagedUtxoBlockHeight + 1L);
        BlockId stagedUtxoBlockId = blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, stagedUtxoBlockHeight);
        while (stagedUtxoBlockId != null) {
            if (_shouldAbort()) { break; }
            if ((stagedUtxoBlockHeight + _utxoCommitmentBlockLag) > headBlockHeight) { return; }

            final boolean isCloseToHeadBlockHeight;
            { // Ensure _maxCommitmentsToKeep commits will be generated during IBD...
                final long blockHeightOffset = (_maxCommitmentsToKeep * _publishCommitInterval);
                isCloseToHeadBlockHeight = (stagedUtxoBlockHeight > (headBlockHeaderHeight - blockHeightOffset));
            }

            final boolean shouldCreateCommit = ((stagedUtxoBlockHeight % _publishCommitInterval) == 0);

            if (shouldCreateCommit && isCloseToHeadBlockHeight) {
                // NOTE: a Utxo Commitment for Block N is the Utxo set required to process Block N; i.e. UTXO Commitment N excludes Block N's coinbase.
                final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, stagedUtxoBlockHeight);
                final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);
                final UtxoCommitment utxoCommitment = _publishUtxoCommitment(stagedUtxoBlockId, blockHash, stagedUtxoBlockHeight, databaseManager);
                if (utxoCommitment != null) {
                    Logger.debug("Created UTXO Commitment: " + utxoCommitment.getPublicKey() + " @ " + utxoCommitment.getBlockHeight());
                }
            }

            final NanoTimer nanoTimer = new NanoTimer();
            nanoTimer.start();

            final Block block = blockDatabaseManager.getBlock(stagedUtxoBlockId);
            if (block == null) {
                Logger.debug("Unable to load Block#" + stagedUtxoBlockHeight);
                break;
            }

            final Sha256Hash blockHash = block.getHash();
            Logger.trace("Applying " + blockHash + " to staged UTXO commitment.");

            final BlockUtxoDiff blockUtxoDiff = BlockUtil.getBlockUtxoDiff(block);

            final int spentTransactionOutputCount = blockUtxoDiff.spentTransactionOutputIdentifiers.getCount();
            final int transactionOutputCount = blockUtxoDiff.unspentTransactionOutputIdentifiers.getCount();

            final Map<TransactionOutputIdentifier, TransactionOutput> unspentTransactionOutputMap;
            final MutableList<TransactionOutputIdentifier> sortedSpentIdentifiers = new MutableList<>(spentTransactionOutputCount);
            final MutableList<TransactionOutputIdentifier> sortedUnspentIdentifiers = new MutableList<>(transactionOutputCount);
            {
                final TreeMap<TransactionOutputIdentifier, TransactionOutput> sortedUnspentTransactionOutputIdentifierMap = new TreeMap<>();
                for (int i = 0; i < transactionOutputCount; ++i) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = blockUtxoDiff.unspentTransactionOutputIdentifiers.get(i);
                    final TransactionOutput transactionOutput = blockUtxoDiff.unspentTransactionOutputs.get(i);

                    sortedUnspentTransactionOutputIdentifierMap.put(transactionOutputIdentifier, transactionOutput);
                }

                final TreeSet<TransactionOutputIdentifier> sortedSpentIdentifiersHashSet = new TreeSet<>();
                for (final TransactionOutputIdentifier transactionOutputIdentifier : blockUtxoDiff.spentTransactionOutputIdentifiers) {
                    sortedSpentIdentifiersHashSet.add(transactionOutputIdentifier);
                }

                // Exclude the creation of block outputs that are also deleted by this block...
                for (final TransactionOutputIdentifier transactionOutputIdentifier : sortedUnspentTransactionOutputIdentifierMap.keySet()) {
                    final boolean outputIdentifierWasSpentInThisBlock = sortedSpentIdentifiersHashSet.remove(transactionOutputIdentifier);
                    if (! outputIdentifierWasSpentInThisBlock) {
                        sortedUnspentIdentifiers.add(transactionOutputIdentifier);
                    }
                }

                for (final TransactionOutputIdentifier transactionOutputIdentifier : sortedSpentIdentifiersHashSet) {
                    sortedSpentIdentifiers.add(transactionOutputIdentifier);
                }

                unspentTransactionOutputMap = sortedUnspentTransactionOutputIdentifierMap;
            }

            databaseManager.startTransaction();

            final long blockHeight = stagedUtxoBlockHeight;
            identifierBatchRunner.run(sortedUnspentIdentifiers, new BatchRunner.Batch<TransactionOutputIdentifier>() {
                @Override
                public void run(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws Exception {
                    // NOTE: Overwriting the block_height is necessary for duplicate transactions correctness. (i.e. E3BF3D07D4B0375638D5F1DB5255FE07BA2C4CB067CD81B84EE974B6585FB468 & D5D27987D2A3DFC724E359870C6644B40E497BDC0589A033220FE15429D88599)
                    // NOTE: Overwriting the amount is not strictly necessary, but provides future-proofing since the committed_unspent_transaction_outputs table can use negative amounts in the case of recovering from a deep-ish reorg.
                    final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO staged_utxo_commitment (transaction_hash, `index`, block_height, is_coinbase, amount, locking_script) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE block_height = VALUES(block_height), amount = VALUES(amount)");
                    for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
                        final TransactionOutput transactionOutput = unspentTransactionOutputMap.get(transactionOutputIdentifier);

                        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();
                        final LockingScript lockingScript = transactionOutput.getLockingScript();
                        final ByteArray lockingScriptBytes = lockingScript.getBytes();

                        final Boolean isCoinbase = (Util.areEqual(blockUtxoDiff.coinbaseTransactionHash, transactionHash));

                        batchedInsertQuery.setParameter(transactionHash);
                        batchedInsertQuery.setParameter(outputIndex);
                        batchedInsertQuery.setParameter(blockHeight);
                        batchedInsertQuery.setParameter(isCoinbase);
                        batchedInsertQuery.setParameter(transactionOutput.getAmount());
                        batchedInsertQuery.setParameter(lockingScriptBytes);
                    }
                    databaseConnection.executeSql(batchedInsertQuery);
                }
            });

            identifierBatchRunner.run(sortedSpentIdentifiers, new BatchRunner.Batch<TransactionOutputIdentifier>() {
                @Override
                public void run(final List<TransactionOutputIdentifier> spentTransactionOutputIdentifiers) throws Exception {
                    databaseConnection.executeSql(
                        new Query("DELETE FROM staged_utxo_commitment WHERE (transaction_hash, `index`) IN (?)")
                            .setExpandedInClauseParameters(spentTransactionOutputIdentifiers, ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER) // NOTE: DELETE ... WHERE IN <tuple> will not use the table index, so expanded in-clauses are necessary.
                    );
                }
            });

            _setStagedUtxoCommitmentBlockHeight(stagedUtxoBlockHeight, databaseManager);

            databaseManager.commitTransaction();

            if (shouldCreateCommit) {
                _deleteOldUtxoCommitments(databaseConnection);
            }

            stagedUtxoBlockId = blockHeaderDatabaseManager.getChildBlockId(blockchainSegmentId, stagedUtxoBlockId);
            stagedUtxoBlockHeight += 1L;

            nanoTimer.stop();
            Logger.trace("Applied " + blockHash + " to staged UTXO commitment in " + nanoTimer.getMillisecondsElapsed() + "ms.");
        }
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _execute() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
            final Long committedUtxoBlockHeight = unspentTransactionOutputDatabaseManager.getCommittedUnspentTransactionOutputBlockHeight();
            if (committedUtxoBlockHeight < MIN_BLOCK_HEIGHT) { return false; } // Don't stage any UTXO commitments until the min block height has been reached by the committed utxo set...

            final Long stagedUtxoCommitBlockHeight = _getStagedUtxoCommitmentBlockHeight(databaseManager);
            if (stagedUtxoCommitBlockHeight <= 0L) {
                _importFromCommittedUtxos(databaseManager);
            }

            _updateStagedCommitment(databaseManager);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }

        return false;
    }

    @Override
    protected void _onSleep() { }

    protected UtxoCommitmentGenerator(final FullNodeDatabaseManagerFactory databaseManagerFactory, final String outputDirectory, final Integer utxoCommitmentBlockLag) {
        _databaseManagerFactory = databaseManagerFactory;
        _outputDirectory = outputDirectory;
        _utxoCommitmentBlockLag = utxoCommitmentBlockLag;
    }

    public UtxoCommitmentGenerator(final FullNodeDatabaseManagerFactory databaseManagerFactory, final String outputDirectory) {
        this(databaseManagerFactory, outputDirectory, UtxoCommitmentGenerator.UTXO_COMMITMENT_BLOCK_LAG);
    }

    public Boolean requiresBlock(final Long blockHeight, final Sha256Hash blockHash) {
        if (blockHeight < MIN_BLOCK_HEIGHT) { return false; }

        final Long stagedUtxoCommitmentBlockHeight;
        {
            final Long cachedStagedUtxoCommitmentBlockHeight = _cachedStagedUtxoCommitmentBlockHeight;
            if (cachedStagedUtxoCommitmentBlockHeight != null) {
                stagedUtxoCommitmentBlockHeight = cachedStagedUtxoCommitmentBlockHeight;
            }
            else {
                try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                    stagedUtxoCommitmentBlockHeight = _getStagedUtxoCommitmentBlockHeight(databaseManager);
                }
                catch (final DatabaseException exception) {
                    Logger.debug(exception);
                    return true;
                }
            }
        }

        return (blockHeight >= stagedUtxoCommitmentBlockHeight);
    }
}
