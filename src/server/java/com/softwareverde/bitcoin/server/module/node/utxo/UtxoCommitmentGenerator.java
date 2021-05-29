package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitment;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentId;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UndoLogDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputJvmManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputManager;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.concurrent.service.GracefulSleepyService;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.MultisetHash;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class UtxoCommitmentGenerator extends GracefulSleepyService {
    protected static final String STAGED_COMMITMENT_BLOCK_HEIGHT_KEY = "staged_utxo_commitment_block_height";
    protected static final Integer UTXO_COMMITMENT_BLOCK_LAG = UndoLogDatabaseManager.MAX_REORG_DEPTH;
    protected static final Long MIN_BLOCK_HEIGHT = 650000L;

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final Long _maxByteCountPerFile;
    protected final Long _publishCommitInterval = 10000L;
    protected final Integer _maxCommitmentsToKeep = 2;
    protected final String _outputDirectory;

    protected Long _cachedStagedUtxoCommitmentBlockHeight = null;

    protected Long _getStagedUtxoCommitmentBlockHeight(final DatabaseManager databaseManager) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT value FROM properties WHERE `key` = ?")
                .setParameter(STAGED_COMMITMENT_BLOCK_HEIGHT_KEY)
        );
        if (rows.isEmpty()) { return 0L; }

        final Row row = rows.get(0);
        final Long blockHeight = row.getLong("value");

        _cachedStagedUtxoCommitmentBlockHeight = blockHeight;

        return blockHeight;
    }

    protected void _setStagedUtxoCommitmentBlockHeight(final Long blockHeight, final DatabaseManager databaseManager) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("INSERT INTO properties (`key`, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value = VALUES (value)")
                .setParameter(STAGED_COMMITMENT_BLOCK_HEIGHT_KEY)
                .setParameter(blockHeight)
        );

        _cachedStagedUtxoCommitmentBlockHeight = blockHeight;
    }

    protected final UtxoCommitmentId _createUtxoCommit(final BlockId blockId, final DatabaseManager databaseManager) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

        final Long utxoCommitId = databaseConnection.executeSql(
            new Query("INSERT INTO utxo_commitments (block_id) VALUES (?)")
                .setParameter(blockId)
        );

        return UtxoCommitmentId.wrap(utxoCommitId);
    }

    protected final void _setUtxoCommitHash(final UtxoCommitmentId utxoCommitmentId, final Sha256Hash hash, final DatabaseManager databaseManager) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("UPDATE utxo_commitments SET hash = ? WHERE id = ?")
                .setParameter(hash)
                .setParameter(utxoCommitmentId)
        );
    }

    protected File _createPartialUtxoCommitFile(final UtxoCommitmentId utxoCommitmentId, final MultisetHash multisetHash, final File partialFile, final Integer utxoCount, final Long fileByteCount, final DatabaseManager databaseManager) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

        final PublicKey publicKey = multisetHash.getPublicKey();

        databaseConnection.executeSql(
            new Query("INSERT INTO utxo_commitment_files (utxo_commitment_id, public_key, utxo_count, byte_count) VALUES (?, ?, ?, ?)")
                .setParameter(utxoCommitmentId)
                .setParameter(publicKey)
                .setParameter(utxoCount)
                .setParameter(fileByteCount)
        );

        final File newFile = new File(_outputDirectory, publicKey.toString());
        final boolean renameWasSuccessful = partialFile.renameTo(newFile);
        if (! renameWasSuccessful) {
            throw new DatabaseException("Unable to create partial commit file: " + newFile.getAbsolutePath());
        }

        Logger.trace("Partial UTXO Commitment file created: " + newFile + ", " + fileByteCount + " bytes, " + utxoCount + " utxos.");

        return newFile;
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
            if (committedUtxoBlockHeight > (headBlockHeight - UTXO_COMMITMENT_BLOCK_LAG)) {
                Logger.debug("Committed UTXO set surpasses UTXO Commitment lag, not importing UTXOs from committed buffer.");
                return;
            }

            TransactionOutputIdentifier previousTransactionOutputIdentifier = new TransactionOutputIdentifier(Sha256Hash.EMPTY_HASH, -1);
            while (true) {
                final Sha256Hash previousTransactionHash = previousTransactionOutputIdentifier.getTransactionHash();
                final Integer previousOutputIndex = previousTransactionOutputIdentifier.getOutputIndex();

                databaseConnection.executeSql(
                    new Query("INSERT INTO staged_utxo_commitment SELECT * FROM committed_unspent_transaction_outputs WHERE (transaction_hash > ?) OR (transaction_hash = ? AND `index` > ?) ORDER BY transaction_hash ASC, `index` ASC LIMIT " + batchSize)
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

    protected UtxoCommitment _publishUtxoCommitment(final BlockId blockId, final Long commitBlockHeight, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        Logger.debug("Creating UTXO commitment.");
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final File outputDirectory = new File(_outputDirectory);
        if ( (! outputDirectory.exists()) || (! outputDirectory.canWrite()) ) {
            throw new DatabaseException("Unable to write UTXO commit to directory: " + _outputDirectory);
        }

        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

        final int batchSize = Math.min(1024, databaseManager.getMaxQueryBatchSize());
        final int pageSize = (int) (16L * ByteUtil.Unit.Binary.MEBIBYTES);
        final MultisetHash multisetHash = new MultisetHash();
        final File protoFile = new File(outputDirectory, "utxo.dat");

        OutputStream outputStream = null;
        try {
            final UtxoCommitmentId utxoCommitmentId = _createUtxoCommit(blockId, databaseManager);
            final UtxoCommitmentCore utxoCommitment = new UtxoCommitmentCore();

            int utxoCount = 0;
            long bytesWrittenToStream = 0L;
            outputStream = new BufferedOutputStream(new FileOutputStream(protoFile), pageSize);
            TransactionOutputIdentifier previousTransactionOutputIdentifier = new TransactionOutputIdentifier(Sha256Hash.EMPTY_HASH, -1);

            MultisetHash intermediateMultisetHash = new MultisetHash();
            while (true) {
                final java.util.List<Row> rows;
                {
                    final Sha256Hash transactionHash = previousTransactionOutputIdentifier.getTransactionHash();
                    final Integer outputIndex = previousTransactionOutputIdentifier.getOutputIndex();
                    rows = databaseConnection.query(
                        new Query("SELECT transaction_hash, `index`, block_height, is_coinbase, amount, locking_script FROM staged_utxo_commitment WHERE (transaction_hash > ?) OR (transaction_hash = ? AND `index` > ?) ORDER BY transaction_hash ASC, `index` ASC LIMIT " + batchSize)
                            .setParameter(transactionHash)
                            .setParameter(transactionHash)
                            .setParameter(outputIndex)
                    );
                }
                if (rows.isEmpty()) { break; }

                for (final Row row : rows) {
                    final Sha256Hash transactionHash = Sha256Hash.wrap(row.getBytes("transaction_hash"));
                    final Integer outputIndex = row.getInteger("index");

                    final Long blockHeight = row.getLong("block_height");
                    final Boolean isCoinbase = row.getBoolean("is_coinbase");
                    final Long amount = row.getLong("amount");
                    final ByteArray lockingScriptBytes = ByteArray.wrap(row.getBytes("locking_script"));

                    final MutableCommittedUnspentTransactionOutput committedUnspentTransactionOutput = new MutableCommittedUnspentTransactionOutput();
                    committedUnspentTransactionOutput.setTransactionHash(transactionHash);
                    committedUnspentTransactionOutput.setIndex(outputIndex);
                    committedUnspentTransactionOutput.setBlockHeight(blockHeight);
                    committedUnspentTransactionOutput.setIsCoinbase(isCoinbase);
                    committedUnspentTransactionOutput.setAmount(amount);
                    committedUnspentTransactionOutput.setLockingScript(lockingScriptBytes);

                    final ByteArray byteArray = committedUnspentTransactionOutput.getBytes();

                    if (bytesWrittenToStream + byteArray.getByteCount() > _maxByteCountPerFile) {
                        outputStream.flush();
                        outputStream.close();

                        final File newFile = _createPartialUtxoCommitFile(utxoCommitmentId, intermediateMultisetHash, protoFile, utxoCount, bytesWrittenToStream, databaseManager);
                        utxoCommitment._files.add(newFile);

                        outputStream = new BufferedOutputStream(new FileOutputStream(protoFile), pageSize);
                        multisetHash.add(intermediateMultisetHash);
                        intermediateMultisetHash = new MultisetHash();
                        bytesWrittenToStream = 0L;
                        utxoCount = 0;
                    }

                    intermediateMultisetHash.addItem(byteArray);
                    final int byteCount = byteArray.getByteCount();
                    for (int i = 0; i < byteCount; ++i) {
                        final byte b = byteArray.getByte(i);
                        outputStream.write(b);
                    }
                    bytesWrittenToStream += byteCount;
                    utxoCount += 1;
                }

                { // Update the previousTransactionOutputIdentifier for the next loop...
                    final int rowCount = rows.size();
                    final Row row = rows.get(rowCount - 1);
                    final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("transaction_hash"));
                    final Integer outputIndex = row.getInteger("index");

                    previousTransactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                }
            }

            outputStream.flush();
            outputStream.close();
            outputStream = null;

            if (bytesWrittenToStream > 0) {
                final File newFile = _createPartialUtxoCommitFile(utxoCommitmentId, intermediateMultisetHash, protoFile, utxoCount, bytesWrittenToStream, databaseManager);
                utxoCommitment._files.add(newFile);
                multisetHash.add(intermediateMultisetHash);
            }

            final Sha256Hash commitHash = multisetHash.getHash();
            _setUtxoCommitHash(utxoCommitmentId, commitHash, databaseManager);

            utxoCommitment._blockId = blockId;
            utxoCommitment._blockHeight = commitBlockHeight;
            utxoCommitment._multisetHash = multisetHash;

            nanoTimer.stop();
            Logger.trace("Created UTXO Commitment in " + nanoTimer.getMillisecondsElapsed() + "ms.");

            return utxoCommitment;
        }
        catch (final Exception exception) {
            if (outputStream != null) {
                try { outputStream.close(); }
                catch (final Exception ignored) { }
            }

            throw new DatabaseException(exception);
        }
    }

    protected void _deleteOldUtxoCommitments(final DatabaseConnection databaseConnection) throws DatabaseException {
        final HashSet<UtxoCommitmentId> commitmentsToKeep = new HashSet<>(_maxCommitmentsToKeep);
        {
            final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT utxo_commitments.id FROM utxo_commitments INNER JOIN blocks ON blocks.id = utxo_commitments.block_id ORDER BY blocks.block_height DESC LIMIT " + _maxCommitmentsToKeep));
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
                new Query("SELECT public_key FROM utxo_commitment_files WHERE utxo_commitment_id = ?")
                    .setParameter(utxoCommitmentId)
            );

            for (final Row fileRow : fileRows) {
                final PublicKey publicKey = PublicKey.fromBytes(fileRow.getBytes("public_key"));
                final File newFile = new File(_outputDirectory, publicKey.toString());
                newFile.delete();
            }

            databaseConnection.executeSql(
                new Query("DELETE utxo_commitment_files, utxo_commitments FROM utxo_commitments INNER JOIN utxo_commitment_files ON utxo_commitments.id = utxo_commitment_files.utxo_commitment_id WHERE utxo_commitments.id = ?")
                    .setParameter(utxoCommitmentId)
            );

            Logger.info("Deleted UTXO commitment: " + commitmentHash);
        }
    }

    protected void _updateStagedCommitment(final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

        final int batchSize = Math.min(1024, databaseManager.getMaxQueryBatchSize());
        final BatchRunner<TransactionOutputIdentifier> identifierBatchRunner = new BatchRunner<>(batchSize);

        long stagedUtxoBlockHeight = _getStagedUtxoCommitmentBlockHeight(databaseManager);
        final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
        if (headBlockId == null) { return; }

        final Long headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);
        if ((stagedUtxoBlockHeight + UTXO_COMMITMENT_BLOCK_LAG) > headBlockHeight) { return; }

        final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(headBlockId);

        stagedUtxoBlockHeight = (stagedUtxoBlockHeight + 1L);
        BlockId stagedUtxoBlockId = blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, stagedUtxoBlockHeight);
        while (stagedUtxoBlockId != null) {
            if (_shouldAbort()) { break; }

            final NanoTimer nanoTimer = new NanoTimer();
            nanoTimer.start();

            final Block block = blockDatabaseManager.getBlock(stagedUtxoBlockId);
            if (block == null) {
                Logger.debug("Unable to load Block#" + stagedUtxoBlockHeight);
                break;
            }

            final Sha256Hash blockHash = block.getHash();
            Logger.trace("Applying " + blockHash + " to staged UTXO commitment.");

            final UnspentTransactionOutputManager.BlockUtxoDiff blockUtxoDiff = UnspentTransactionOutputManager.getBlockUtxoDiff(block);

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

            TransactionUtil.startTransaction(databaseConnection);

            final long blockHeight = stagedUtxoBlockHeight;
            identifierBatchRunner.run(sortedUnspentIdentifiers, new BatchRunner.Batch<TransactionOutputIdentifier>() {
                @Override
                public void run(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws Exception {
                    final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO staged_utxo_commitment (transaction_hash, `index`, block_height, is_coinbase, amount, locking_script) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount)");
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

            final boolean isCloseToHeadBlockHeight;
            {
                final long blockHeightOffset = (_maxCommitmentsToKeep * _publishCommitInterval);
                isCloseToHeadBlockHeight = (blockHeight >= (headBlockHeight - blockHeightOffset));
            }

            final boolean shouldCreateCommit = ((blockHeight % _publishCommitInterval) == 0);

            if (shouldCreateCommit && isCloseToHeadBlockHeight) {
                _publishUtxoCommitment(stagedUtxoBlockId, blockHeight, databaseManager);
            }

            _setStagedUtxoCommitmentBlockHeight(stagedUtxoBlockHeight, databaseManager);

            TransactionUtil.commitTransaction(databaseConnection);

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
    protected Boolean _run() {
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
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }

        return false;
    }

    @Override
    protected void _onSleep() { }

    public UtxoCommitmentGenerator(final FullNodeDatabaseManagerFactory databaseManagerFactory, final String outputDirectory) {
        _databaseManagerFactory = databaseManagerFactory;
        _maxByteCountPerFile = (32L * ByteUtil.Unit.Binary.MEBIBYTES);
        _outputDirectory = outputDirectory;
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
