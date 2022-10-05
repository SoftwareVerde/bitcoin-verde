package com.softwareverde.bitcoin.server.module.node.database.block.header;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.BlockWork;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.header.difficulty.work.MutableChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.merkleroot.MutableMerkleRoot;
import com.softwareverde.bitcoin.server.configuration.CheckpointConfiguration;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.module.node.database.BlockchainCacheFactory;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockMetadata;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockchainCache;
import com.softwareverde.bitcoin.server.module.node.database.block.MutableBlockchainCache;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;
import com.softwareverde.util.map.Visitor;

import java.util.HashMap;
import java.util.Map;

public class BlockHeaderDatabaseManagerCore implements BlockHeaderDatabaseManager {
    protected final BlockchainCacheFactory _blockchainCacheFactory;
    protected final DatabaseManager _databaseManager;
    protected final CheckpointConfiguration _checkpointConfiguration;

    public BlockHeaderDatabaseManagerCore(final DatabaseManager databaseManager, final CheckpointConfiguration checkpointConfiguration, final BlockchainCacheFactory blockchainCacheFactory) {
        _databaseManager = databaseManager;
        _checkpointConfiguration = checkpointConfiguration;
        _blockchainCacheFactory = blockchainCacheFactory;
    }

    protected Long _getBlockHeight(final BlockId blockId, final BlockchainCache blockchainCache) throws DatabaseException {
        if (blockId == null) { return null; }

        if (blockchainCache != null) {
            final Long blockHeight = blockchainCache.getBlockHeight(blockId);
            if (blockHeight != null) { return blockHeight; }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, block_height FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return row.getLong("block_height");
    }

    protected Long _getBlockTimestamp(final BlockId blockId, final BlockchainCache blockchainCache) throws DatabaseException {
        if (blockId == null) { return null; }

        if (blockchainCache != null) {
            final BlockHeader blockHeader = blockchainCache.getBlockHeader(blockId);
            if (blockHeader != null) {
                return blockHeader.getTimestamp();
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, timestamp FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return row.getLong("timestamp");
    }

    protected BlockId _getBlockHeaderId(final Sha256Hash blockHash, final BlockchainCache blockchainCache) throws DatabaseException {
        if (blockchainCache != null) {
            final BlockId blockId = blockchainCache.getBlockId(blockHash);
            if (blockId != null) { return blockId; }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE hash = ?")
                .setParameter(blockHash)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("id"));
    }

    protected Sha256Hash _getBlockHash(final BlockId blockId, final BlockchainCache blockchainCache) throws DatabaseException {
        if (blockId == null) { return null; }

        if (blockchainCache != null) {
            final BlockHeader blockHeader = blockchainCache.getBlockHeader(blockId);
            if (blockHeader != null) {
                return blockHeader.getHash();
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.wrap(row.getBytes("hash"));
    }

    protected BlockHeader _inflateBlockHeaderFromRow(final Row row, final BlockchainCache blockchainCache) throws DatabaseException {
        final Long version = row.getLong("version");

        final Sha256Hash previousBlockHash;
        {
            final BlockId previousBlockId = BlockId.wrap(row.getLong("previous_block_id"));
            final Sha256Hash nullablePreviousBlockHash = _getBlockHash(previousBlockId, blockchainCache);
            previousBlockHash = Util.coalesce(nullablePreviousBlockHash, Sha256Hash.EMPTY_HASH);
        }

        final MerkleRoot merkleRoot = MutableMerkleRoot.wrap(row.getBytes("merkle_root"));
        final Long timestamp = row.getLong("timestamp");
        final Difficulty difficulty = Difficulty.decode(MutableByteArray.wrap(row.getBytes("difficulty")));
        final Long nonce = row.getLong("nonce");

        final MutableBlockHeader blockHeader = new MutableBlockHeader();

        blockHeader.setVersion(version);
        blockHeader.setPreviousBlockHash(previousBlockHash);
        blockHeader.setMerkleRoot(merkleRoot);
        blockHeader.setTimestamp(timestamp);
        blockHeader.setDifficulty(difficulty);
        blockHeader.setNonce(nonce);

        { // Assert that the hashes match after inflation...
            final Sha256Hash expectedHash = Sha256Hash.wrap(row.getBytes("hash"));
            final Sha256Hash actualHash = blockHeader.getHash();
            if (! Util.areEqual(expectedHash, actualHash)) {
                Logger.warn("Unable to inflate block: " + expectedHash + " / " + blockHeader.getHash());
                return null;
            }
        }

        return blockHeader;
    }

    protected BlockHeader _inflateBlockHeader(final BlockId blockId, final BlockchainCache blockchainCache) throws DatabaseException {
        if (blockId == null) { return null; }

        if (blockchainCache != null) {
            final BlockHeader blockHeader = blockchainCache.getBlockHeader(blockId);
            if (blockHeader != null) { return blockHeader; }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT * FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }
        final Row row = rows.get(0);

        return _inflateBlockHeaderFromRow(row, blockchainCache);
    }

    protected void _updateBlockHeader(final BlockId blockId, final BlockHeader blockHeader, final MutableBlockchainCache blockchainCache) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Sha256Hash previousBlockHash = blockHeader.getPreviousBlockHash();
        final BlockId previousBlockId = _getBlockHeaderId(previousBlockHash, blockchainCache);
        final Long previousBlockHeight = _getBlockHeight(previousBlockId, blockchainCache);
        final Long blockHeight = (previousBlockHeight == null ? 0 : (previousBlockHeight + 1));

        databaseConnection.executeSql(
            new Query("UPDATE blocks SET hash = ?, previous_block_id = ?, block_height = ?, merkle_root = ?, version = ?, timestamp = ?, difficulty = ?, nonce = ? WHERE id = ?")
                .setParameter(blockHeader.getHash())
                .setParameter(previousBlockId)
                .setParameter(blockHeight)
                .setParameter(blockHeader.getMerkleRoot())
                .setParameter(blockHeader.getVersion())
                .setParameter(blockHeader.getTimestamp())
                .setParameter(blockHeader.getDifficulty())
                .setParameter(blockHeader.getNonce())
                .setParameter(blockId)
        );

        if (blockchainCache != null) {
            final ChainWork chainWork = blockchainCache.getChainWork(blockId);
            final MedianBlockTime medianBlockTime = blockchainCache.getMedianBlockTime(blockId);
            final Boolean hasTransactions = blockchainCache.hasTransactions(blockId);
            blockchainCache.addBlock(blockId, blockHeader, blockHeight, chainWork, medianBlockTime, hasTransactions);
        }
    }

    protected List<BlockId> _insertBlockHeadersAndUpdateBlockchainSegments(final List<BlockHeader> blockHeaders, final MutableBlockchainCache blockchainCache) throws DatabaseException {
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();

        final List<BlockId> blockIds = _insertBlockHeaders(blockHeaders, blockchainCache);
        if (blockIds.isEmpty()) { return blockIds; }

        final BlockId firstBlockId = blockIds.get(0);
        final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.updateBlockchainsForNewBlock(firstBlockId);

        _setBlockchainSegmentIds(blockIds, blockchainSegmentId, blockchainCache);

        return blockIds;
    }

    protected ChainWork _getChainWork(final BlockId blockId, final BlockchainCache blockchainCache) throws DatabaseException {
        if (blockchainCache != null) {
            final ChainWork chainWork = blockchainCache.getChainWork(blockId);
            if (chainWork != null) { return chainWork; }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, chain_work FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return ChainWork.wrap(row.getBytes("chain_work"));
    }

    protected BlockId _insertBlockHeader(final BlockHeader blockHeader, final MutableBlockchainCache blockchainCache) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Sha256Hash blockHash = blockHeader.getHash();
        final Sha256Hash previousBlockHash = blockHeader.getPreviousBlockHash();
        final BlockId previousBlockId = _getBlockHeaderId(previousBlockHash, blockchainCache);
        final Long previousBlockHeight = _getBlockHeight(previousBlockId, blockchainCache);
        final Long blockHeight = (previousBlockId == null ? 0 : (previousBlockHeight + 1));
        final Difficulty difficulty = blockHeader.getDifficulty();

        final BlockWork blockWork = difficulty.calculateWork();
        final ChainWork previousChainWork = (previousBlockId == null ? new MutableChainWork() : _getChainWork(previousBlockId, blockchainCache));
        final ChainWork chainWork = ChainWork.add(previousChainWork, blockWork);

        final MedianBlockTime medianBlockTime;
        {
            if (previousBlockId == null) {
                medianBlockTime = MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP);
            }
            else {
                final MutableMedianBlockTime medianTimePast = MedianBlockTimeDatabaseManagerUtil.calculateMedianBlockTime(this, previousBlockId, previousBlockHash);
                medianTimePast.addBlock(blockHeader); // Convert the MedianTimePast to MedianBlockTime.
                medianBlockTime = medianTimePast;
            }
        }

        if (_checkpointConfiguration.violatesCheckpoint(blockHeight, blockHash)) {
            throw new DatabaseException("Block violates checkpoint. " + blockHeight + " / " + blockHash);
        }

        final Long insertId = databaseConnection.executeSql(
            new Query("INSERT INTO blocks (hash, previous_block_id, block_height, merkle_root, version, timestamp, median_block_time, difficulty, nonce, chain_work) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .setParameter(blockHash)
                .setParameter(previousBlockId)
                .setParameter(blockHeight)
                .setParameter(blockHeader.getMerkleRoot())
                .setParameter(blockHeader.getVersion())
                .setParameter(blockHeader.getTimestamp())
                .setParameter(medianBlockTime.getCurrentTimeInSeconds())
                .setParameter(difficulty)
                .setParameter(blockHeader.getNonce())
                .setParameter(chainWork)
        );

        final BlockId blockId = BlockId.wrap(insertId);

        if (blockchainCache != null) {
            blockchainCache.addBlock(blockId, blockHeader, blockHeight, chainWork, medianBlockTime, false);
        }

        return blockId;
    }

    protected List<BlockId> _insertBlockHeaders(final List<BlockHeader> blockHeaders, final MutableBlockchainCache blockchainCache) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        if (blockHeaders.isEmpty()) {
            return new MutableList<>(0);
        }

        if (blockHeaders.getCount() == 1) {
            final BlockHeader blockHeader = blockHeaders.get(0);
            final BlockId blockId = _insertBlockHeader(blockHeader, blockchainCache);
            return new ImmutableList<>(blockId);
        }

        final MutableList<BlockId> blockIds = new MutableList<>(blockHeaders.getCount());

        final Container<Long> previousBlockHeight = new Container<>();
        final Container<ChainWork> previousChainWork = new Container<>();
        final Container<BlockId> lastInsertedBlockId = new Container<>();

        final MutableMedianBlockTime medianTimePast;
        {
            final BlockHeader firstBlockHeader = blockHeaders.get(0);
            final Sha256Hash headBlockHashBeforeBatch = firstBlockHeader.getPreviousBlockHash();

            medianTimePast = MedianBlockTimeDatabaseManagerUtil.calculateMedianBlockTime(this, headBlockHashBeforeBatch);
        }

        final Integer maxBatchSize = Math.min(1024, _databaseManager.getMaxQueryBatchSize());
        final BatchRunner<BlockHeader> batchRunner = new BatchRunner<>(maxBatchSize, false);
        batchRunner.run(blockHeaders, new BatchRunner.Batch<BlockHeader>() {
            @Override
            public void run(final List<BlockHeader> batchedBlockHeaders) throws Exception {
                final int batchCount = batchedBlockHeaders.getCount();
                int i = 0;

                // Since the next insert_id of the blocks table may not be the previous blockId + 1, insert the first blockHeader and retrieve its auto_increment value, then proceed with regular batching...
                if (lastInsertedBlockId.value == null) {
                    final BlockHeader blockHeader = batchedBlockHeaders.get(0);
                    final BlockId blockId = _insertBlockHeader(blockHeader, blockchainCache);

                    lastInsertedBlockId.value = blockId;
                    previousBlockHeight.value = _getBlockHeight(blockId, blockchainCache);
                    previousChainWork.value = _getChainWork(blockId, blockchainCache);
                    blockIds.add(blockId);

                    // medianTimePast for this block was calculated in _insertBlockHeader
                    // but we need to keep medianTimePast up-to-date for the upcoming blocks
                    medianTimePast.addBlock(blockHeader);

                    i += 1;
                }

                final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO blocks (hash, previous_block_id, block_height, merkle_root, version, timestamp, median_block_time, difficulty, nonce, chain_work) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

                long previousBlockId = lastInsertedBlockId.value.longValue();
                while (i < batchCount) {
                    final BlockHeader blockHeader = batchedBlockHeaders.get(i);
                    final Sha256Hash blockHash = blockHeader.getHash();

                    long blockHeight = (previousBlockHeight.value + 1L);
                    final Difficulty difficulty = blockHeader.getDifficulty();

                    final BlockWork blockWork = difficulty.calculateWork();
                    final ChainWork chainWork = ChainWork.add(previousChainWork.value, blockWork);

                    final MedianBlockTime medianBlockTime;
                    {
                        medianTimePast.addBlock(blockHeader);
                        medianBlockTime = medianTimePast;
                    }

                    if (_checkpointConfiguration.violatesCheckpoint(blockHeight, blockHash)) {
                        throw new DatabaseException("Block violates checkpoint. " + blockHeight + " / " + blockHash);
                    }

                    batchedInsertQuery.setParameter(blockHash);
                    batchedInsertQuery.setParameter(previousBlockId);
                    batchedInsertQuery.setParameter(blockHeight);
                    batchedInsertQuery.setParameter(blockHeader.getMerkleRoot());
                    batchedInsertQuery.setParameter(blockHeader.getVersion());
                    batchedInsertQuery.setParameter(blockHeader.getTimestamp());
                    batchedInsertQuery.setParameter(medianBlockTime.getCurrentTimeInSeconds());
                    batchedInsertQuery.setParameter(difficulty);
                    batchedInsertQuery.setParameter(blockHeader.getNonce());
                    batchedInsertQuery.setParameter(chainWork);

                    previousBlockId += 1L;
                    previousBlockHeight.value = blockHeight;
                    previousChainWork.value = chainWork;

                    final BlockId blockId = BlockId.wrap(previousBlockId);
                    blockIds.add(blockId);
                    lastInsertedBlockId.value = blockId;

                    if (blockchainCache != null) {
                        blockchainCache.addBlock(blockId, blockHeader, blockHeight, chainWork, medianBlockTime, false);
                    }

                    i += 1;
                }

                databaseConnection.executeSql(batchedInsertQuery);
            }
        });

        return blockIds;
    }

    protected void _setBlockchainSegmentId(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId, final MutableBlockchainCache blockchainCache) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("UPDATE blocks SET blockchain_segment_id = ? WHERE id = ?")
                .setParameter(blockchainSegmentId)
                .setParameter(blockId)
        );

        if (blockchainCache != null) {
            blockchainCache.setBlockchainSegmentId(blockId, blockchainSegmentId);
        }
    }

    protected void _setBlockchainSegmentIds(final List<BlockId> blockIds, final BlockchainSegmentId blockchainSegmentId, final MutableBlockchainCache blockchainCache) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final int maxBatchSize = Math.min(1024, _databaseManager.getMaxQueryBatchSize());

        int batchStartIndex = 0;
        while (batchStartIndex < blockIds.getCount()) {
            final int batchSize = Math.min(blockIds.getCount() - batchStartIndex, maxBatchSize);

            final MutableList<BlockId> blockIdBatch = new MutableList<>(batchSize);
            for (int i = batchStartIndex; i < (batchStartIndex + batchSize); i++) {
                blockIdBatch.add(blockIds.get(i));
            }

            databaseConnection.executeSql(
                new Query("UPDATE blocks SET blockchain_segment_id = ? WHERE id IN (?)")
                    .setParameter(blockchainSegmentId)
                    .setInClauseParameters(blockIdBatch, ValueExtractor.IDENTIFIER)
            );

            batchStartIndex += batchSize;
        }

        if (blockchainCache != null) {
            for (final BlockId blockId : blockIds) {
                blockchainCache.setBlockchainSegmentId(blockId, blockchainSegmentId);
            }
        }
    }

    protected BlockchainSegmentId _getBlockchainSegmentId(final BlockId blockId, final BlockchainCache blockchainCache) throws DatabaseException {
        if (blockchainCache != null) {
            final BlockchainSegmentId blockchainSegmentId = blockchainCache.getBlockchainSegmentId(blockId);
            if (blockchainSegmentId != null) { return blockchainSegmentId; }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, blockchain_segment_id FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );
        if (rows.isEmpty()) { return null; }
        final Row row = rows.get(0);

        return BlockchainSegmentId.wrap(row.getLong("blockchain_segment_id"));
    }

    protected Boolean _isBlockConnectedToChain(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId, final BlockRelationship blockRelationship, final BlockchainCache blockchainCache) throws DatabaseException {
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();

        final BlockchainSegmentId blockchainSegmentId1 = _getBlockchainSegmentId(blockId, blockchainCache);
        return blockchainDatabaseManager.areBlockchainSegmentsConnected(blockchainSegmentId1, blockchainSegmentId, blockRelationship);
    }

    protected BlockId _getChildBlockId(final BlockchainSegmentId blockchainSegmentId, final BlockId previousBlockId, final BlockchainCache blockchainCache) throws DatabaseException {
        if (blockchainCache != null) {
            final Long blockHeight = (blockchainCache.getBlockHeight(previousBlockId) + 1L);
            final BlockId blockId = blockchainCache.getBlockHeader(blockchainSegmentId, blockHeight);
            if (blockId != null) { return blockId; }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, blockchain_segment_id FROM blocks WHERE previous_block_id = ?")
                .setParameter(previousBlockId)
        );

        if (rows.isEmpty()) { return null; }

        if (rows.size() == 1) {
            final Row row = rows.get(0);
            final BlockId blockId = BlockId.wrap(row.getLong("id"));
            final Boolean blockIsConnectedToChain = _isBlockConnectedToChain(blockId, blockchainSegmentId, BlockRelationship.ANY, blockchainCache);
            if (! blockIsConnectedToChain) { return null; }
            return blockId;
        }

        // At this point, previousBlockId has multiple children.
        // If blockchainSegmentId is not provided, then just return the first-seen block.
        if (blockchainSegmentId == null) {
            final Row row = rows.get(0);
            return BlockId.wrap(row.getLong("id"));
        }

        // Since blockchainSegmentId is provided, the child along its chain is the blockId that shall be preferred...
        for (final Row row : rows) {
            final BlockId blockId = BlockId.wrap(row.getLong("id"));
            final BlockchainSegmentId blockchainSegmentId1 = BlockchainSegmentId.wrap(row.getLong("blockchain_segment_id"));
            final Boolean blockIsConnectedToChain = blockchainDatabaseManager.areBlockchainSegmentsConnected(blockchainSegmentId1, blockchainSegmentId, BlockRelationship.ANCESTOR);
            if (blockIsConnectedToChain) {
                return blockId;
            }
        }

        // None of the children blocks match the blockchainSegmentId, so null is returned...
        return null;
    }

    protected Sha256Hash _getHeadBlockHeaderHash(final BlockchainCache blockchainCache) throws DatabaseException {
        if (blockchainCache != null) {
            final BlockchainSegmentId blockchainSegmentId = blockchainCache.getHeadBlockchainSegmentId();
            if (blockchainSegmentId != null) {
                final BlockId headBlockId = blockchainCache.getHeadBlockIdOfBlockchainSegment(blockchainSegmentId, false);
                if (headBlockId != null) {
                    final BlockHeader blockHeader = blockchainCache.getBlockHeader(headBlockId);
                    if (blockHeader != null) {
                        return blockHeader.getHash();
                    }
                }
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM head_block_header")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.wrap(row.getBytes("hash"));
    }

    protected BlockId _getHeadBlockHeaderId(final BlockchainCache blockchainCache) throws DatabaseException {
        if (blockchainCache != null) {
            final BlockchainSegmentId blockchainSegmentId = blockchainCache.getHeadBlockchainSegmentId();
            if (blockchainSegmentId != null) {
                final BlockId headBlockId = blockchainCache.getHeadBlockIdOfBlockchainSegment(blockchainSegmentId, false);
                if (headBlockId != null) {
                    return headBlockId;
                }
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM head_block_header")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("id"));
    }

    protected Integer _getBlockHeaderDirectDescendantCount(final BlockId blockId, final BlockchainCache blockchainCache) throws DatabaseException {
        if (blockchainCache != null) {
            final List<BlockId> blockIds = blockchainCache.getChildBlockIds(blockId);
            if (blockIds != null) {
                return blockIds.getCount();
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE previous_block_id = ?")
                .setParameter(blockId)
        );

        return (rows.size());
    }

    protected BlockId _getPreviousBlockId(final BlockId blockId, final BlockchainCache blockchainCache) throws DatabaseException {
        if (blockchainCache != null) {
            final BlockHeader blockHeader = blockchainCache.getBlockHeader(blockId);
            if (blockHeader != null) {
                final Sha256Hash previousBlockHash = blockHeader.getPreviousBlockHash();
                final BlockId parentBlockId = blockchainCache.getBlockId(previousBlockHash);
                if (parentBlockId != null) { return parentBlockId; }
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, previous_block_id FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("previous_block_id"));
    }

    protected MedianBlockTime _getMedianBlockTime(final BlockId blockId, final BlockchainCache blockchainCache) throws DatabaseException {
        if (blockchainCache != null) {
            final MedianBlockTime medianBlockTime = blockchainCache.getMedianBlockTime(blockId);
            if (medianBlockTime != null) {
                return medianBlockTime;
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, median_block_time FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long medianBlockTimeLong = row.getLong("median_block_time");
        return MedianBlockTime.fromSeconds(medianBlockTimeLong);
    }

    @Override
    public BlockId insertBlockHeader(final BlockHeader blockHeader) throws DatabaseException {
        if (! Thread.holdsLock(MUTEX)) { throw new RuntimeException("Attempting to insertBlockHeader without obtaining lock."); }

        final MutableBlockchainCache blockchainCache = _blockchainCacheFactory.getMutableBlockchainCache();
        final BlockId blockId = _insertBlockHeader(blockHeader, blockchainCache);

        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();
        blockchainDatabaseManager.updateBlockchainsForNewBlock(blockId);

        return blockId;
    }

    @Override
    public void updateBlockHeader(final BlockId blockId, final BlockHeader blockHeader) throws DatabaseException {
        final MutableBlockchainCache blockchainCache = _blockchainCacheFactory.getMutableBlockchainCache();
        _updateBlockHeader(blockId, blockHeader, blockchainCache);
    }

    @Override
    public BlockId storeBlockHeader(final BlockHeader blockHeader) throws DatabaseException {
        if (! Thread.holdsLock(MUTEX)) { throw new RuntimeException("Attempting to storeBlockHeader without obtaining lock."); }

        { // Check for existing header...
            final Sha256Hash blockHash = blockHeader.getHash();
            final BlockchainCache blockchainCache = _blockchainCacheFactory.getMutableBlockchainCache();
            final BlockId existingBlockId = _getBlockHeaderId(blockHash, blockchainCache);

            if (existingBlockId != null) {
                return existingBlockId;
            }
        }

        final MutableBlockchainCache blockchainCache = _blockchainCacheFactory.getMutableBlockchainCache();
        final BlockId blockId = _insertBlockHeader(blockHeader, blockchainCache);

        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();
        blockchainDatabaseManager.updateBlockchainsForNewBlock(blockId);

        return blockId;
    }

    /**
     * Batch-Inserts the provided BlockHeaders.  The BlockHeaders must be provided in-order relative to one another in
     *  ascending order; aka, each BlockHeader must be the (only) child of the previous BlockHeader.
     * This function is intended to be used for bootstrapping a database with a known set of headers; be extra careful
     *  when using this function in other circumstances.
     * Each BlockHeader is inserted with the next assumed block height.  The first BlockHeader's height is calculated
     *  by attempting to look up its previous block via its PreviousBlockHash.  If it is not found, it is assumed to be
     *  a genesis block and is inserted at height 0.
     * This function is safe to invoke if other BlockHeaders have been stored, if, and only if, each header is guaranteed
     *  to not be contentious with another block.
     * The BlockchainSegmentId assigned to every BlockHeader (except the first) is the same as its parent.  The first
     *  BlockHeader's BlockchainSegmentId is assigned by the normal BlockchainDatabaseManager::updateBlockchainsForNewBlock
     *  method.
     */
    @Override
    public List<BlockId> insertBlockHeaders(final List<BlockHeader> blockHeaders) throws DatabaseException {
        if (! Thread.holdsLock(MUTEX)) { throw new RuntimeException("Attempting to storeBlockHeader without obtaining lock."); }
        if (blockHeaders.isEmpty()) { return new MutableList<>(0); }

        final MutableBlockchainCache mutableBlockchainCache = _blockchainCacheFactory.getMutableBlockchainCache();
        return _insertBlockHeadersAndUpdateBlockchainSegments(blockHeaders, mutableBlockchainCache);
    }

    @Override
    public void setBlockByteCount(final BlockId blockId, final Integer byteCount) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("UPDATE blocks SET byte_count = ? WHERE id = ?")
                .setParameter(byteCount)
                .setParameter(blockId)
        );
    }

    @Override
    public Integer getBlockByteCount(final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, byte_count FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return row.getInteger("byte_count");
    }

    /**
     * Returns the Sha256Hash of the block that has the tallest block-height.
     */
    @Override
    public Sha256Hash getHeadBlockHeaderHash() throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        return _getHeadBlockHeaderHash(blockchainCache);
    }

    /**
     * Returns the BlockId of the block that has the tallest block-height.
     */
    @Override
    public BlockId getHeadBlockHeaderId() throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        return _getHeadBlockHeaderId(blockchainCache);
    }

    @Override
    public BlockId getBlockHeaderId(final Sha256Hash blockHash) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        return _getBlockHeaderId(blockHash, blockchainCache);
    }

    @Override
    public BlockHeader getBlockHeader(final BlockId blockId) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        return _inflateBlockHeader(blockId, blockchainCache);
    }

    /**
     * Returns true if the BlockHeader has been downloaded and verified.
     */
    @Override
    public Boolean blockHeaderExists(final Sha256Hash blockHash) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        final BlockId blockId = _getBlockHeaderId(blockHash, blockchainCache);
        return (blockId != null);
    }

    @Override
    public Integer getBlockDirectDescendantCount(final BlockId blockId) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        return _getBlockHeaderDirectDescendantCount(blockId, blockchainCache);
    }

    @Override
    public void setBlockchainSegmentId(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        final MutableBlockchainCache blockchainCache = _blockchainCacheFactory.getMutableBlockchainCache();
        _setBlockchainSegmentId(blockId, blockchainSegmentId, blockchainCache);
    }

    @Override
    public BlockchainSegmentId getBlockchainSegmentId(final BlockId blockId) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        return _getBlockchainSegmentId(blockId, blockchainCache);
    }

    @Override
    public Long getBlockHeight(final BlockId blockId) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        return _getBlockHeight(blockId, blockchainCache);
    }

    @Override
    public Map<BlockId, Long> getBlockHeights(final List<BlockId> blockIds) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        if (blockchainCache != null) {
            final HashMap<BlockId, Long> blockHeights = new HashMap<>(blockIds.getCount());
            for (final BlockId blockId : blockIds) {
                final Long blockHeight = blockchainCache.getBlockHeight(blockId);
                blockHeights.put(blockId, blockHeight);
            }
            return blockHeights;
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final HashMap<BlockId, Long> blockHeights = new HashMap<>(blockIds.getCount());
        final Integer batchSize = Math.min(1024, _databaseManager.getMaxQueryBatchSize());
        final BatchRunner<BlockId> batchRunner = new BatchRunner<>(batchSize, false);
        batchRunner.run(blockIds, new BatchRunner.Batch<BlockId>() {
            @Override
            public void run(final List<BlockId> blockIds) throws Exception {
                final java.util.List<Row> rows = databaseConnection.query(
                    new Query("SELECT id, block_height FROM blocks WHERE id IN (?)")
                        .setInClauseParameters(blockIds, ValueExtractor.IDENTIFIER)
                );

                for (final Row row : rows) {
                    final BlockId blockId = BlockId.wrap(row.getLong("id"));
                    final Long blockHeight = row.getLong("block_height");
                    blockHeights.put(blockId, blockHeight);
                }
            }
        });
        return blockHeights;
    }

    @Override
    public Long getBlockTimestamp(final BlockId blockId) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        return _getBlockTimestamp(blockId, blockchainCache);
    }

    @Override
    public BlockId getChildBlockId(final BlockchainSegmentId blockchainSegmentId, final BlockId previousBlockId) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        return _getChildBlockId(blockchainSegmentId, previousBlockId, blockchainCache);
    }

    @Override
    public Boolean hasChildBlock(final BlockId blockId) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        if (blockchainCache != null) {
            final List<BlockId> blockIds = blockchainCache.getChildBlockIds(blockId);
            if (blockIds != null) {
                return (blockIds.getCount() > 0);
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE previous_block_id = ? LIMIT 1")
                .setParameter(blockId)
        );
        return (! rows.isEmpty());
    }

    /**
     *
     *     E         E'
     *     |         |
     *  #4 +----D----+ #5           Height: 3
     *          |
     *          C         C''       Height: 2
     *          |         |
     *       #2 +----B----+ #3      Height: 1
     *               |
     *               A #1           Height: 0
     *
     * Block C is an ancestor of Chain #4.
     * Block E is a descendant of Chain #1.
     *
     */
    @Override
    public Boolean isBlockConnectedToChain(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId, final BlockRelationship blockRelationship) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        return _isBlockConnectedToChain(blockId, blockchainSegmentId, blockRelationship, blockchainCache);
    }

    @Override
    public Sha256Hash getBlockHash(final BlockId blockId) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        return _getBlockHash(blockId, blockchainCache);
    }

    /**
     * Returns a list of Block hashes for the provided list of BlockIds.
     *  The order of the Block hashes corresponds to order within blockIds.
     *  If a Block hash could not be found then the item is set to null within the returned list.
     *  blockIds does not need to be sorted, and may be in any order.
     */
    @Override
    public List<Sha256Hash> getBlockHashes(final List<BlockId> blockIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM blocks WHERE id IN (?)")
                .setInClauseParameters(blockIds, ValueExtractor.IDENTIFIER)
        );

        final HashMap<BlockId, Sha256Hash> hashesMap = new HashMap<>(rows.size());
        for (final Row row : rows) {
            final BlockId blockId = BlockId.wrap(row.getLong("id"));
            final Sha256Hash blockHash = Sha256Hash.wrap(row.getBytes("hash"));

            hashesMap.put(blockId, blockHash);
        }

        final MutableList<Sha256Hash> blockHashes = new MutableList<>(blockIds.getCount());
        for (final BlockId blockId : blockIds) {
            blockHashes.add(hashesMap.get(blockId));
        }
        return blockHashes;
    }

    /**
     * Returns the BlockId of the nth-parent, where n is the parentCount.
     *  For instance, getAncestor(blockId, 0) returns blockId, and getAncestor(blockId, 1) returns blockId's parent.
     */
    @Override
    public BlockId getAncestorBlockId(final BlockId blockId, final Integer parentCount) throws DatabaseException {
        if (blockId == null) { return null; }
        if (parentCount == 0) { return blockId; }

        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();

        if (parentCount == 1) {
            // Optimization/Specialization for parentBlockId...
            return _getPreviousBlockId(blockId, blockchainCache);
        }

        // TODO: Use blockHeight and blockChainSegmentId for constant-time lookups...
        BlockId nextBlockId = blockId;
        for (int i = 0; i < parentCount; ++i) {
            final BlockHeader blockHeader = _inflateBlockHeader(nextBlockId, blockchainCache);
            if (blockHeader == null) { return null; }

            final Sha256Hash previousBlockHash = blockHeader.getPreviousBlockHash();
            nextBlockId = _getBlockHeaderId(previousBlockHash, blockchainCache);
        }
        return nextBlockId;
    }

    /**
     * Initializes a Mutable MedianBlockTime using most recent block headers.
     *  The MedianBlockTime returned includes the head Block's timestamp, and is therefore the MedianTimePast value for the next mined Block.
     */
    @Override
    public MutableMedianBlockTime calculateMedianBlockHeaderTime() throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();

        final Sha256Hash headBlockHash = _getHeadBlockHeaderHash(blockchainCache);
        final Sha256Hash blockHash = Util.coalesce(headBlockHash, BlockHeader.GENESIS_BLOCK_HASH);
        return MedianBlockTimeDatabaseManagerUtil.calculateMedianBlockTime(this, blockHash);
    }

    /**
     * Calculates the MedianBlockTime of the provided blockId.
     *  To calculate the MedianTimePast for a "Block A", provide the BlockId for Block A's parent.
     */
    @Override
    public MutableMedianBlockTime calculateMedianBlockTime(final BlockId blockId) throws DatabaseException {
        return MedianBlockTimeDatabaseManagerUtil.calculateMedianBlockTime(this, blockId);
    }

    /**
     * Returns the MedianBlockTime of the provided blockId.
     *  This value is cached within the database and is more performant than BlockHeaderDatabaseManager::calculateMedianBlockTime.
     *  To calculate the MedianTimePast for a "Block A", provide the BlockId for Block A's parent.
     */
    @Override
    public MedianBlockTime getMedianBlockTime(final BlockId blockId) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        return _getMedianBlockTime(blockId, blockchainCache);
    }

    @Override
    public MedianBlockTime getMedianTimePast(final BlockId blockId) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();

        final BlockId previousBlockId = _getPreviousBlockId(blockId, blockchainCache);
        return _getMedianBlockTime(previousBlockId, blockchainCache);
    }

    @Override
    public ChainWork getChainWork(final BlockId blockId) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();

        return _getChainWork(blockId, blockchainCache);
    }

    @Override
    public BlockId getBlockIdAtHeight(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        if (blockchainCache != null) {
            final BlockId blockId = blockchainCache.getBlockHeader(blockchainSegmentId, blockHeight);
            if (blockId != null) {
                return blockId;
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE block_height = ?")
                .setParameter(blockHeight)
        );

        for (final Row row : rows) {
            final BlockId blockId = BlockId.wrap(row.getLong("id"));

            if (blockchainSegmentId == null) {
                return blockId;
            }

            final Boolean blockIsConnectedToChain = _isBlockConnectedToChain(blockId, blockchainSegmentId, BlockRelationship.ANY, blockchainCache);
            if (blockIsConnectedToChain) {
                return blockId;
            }
        }

        return null;
    }

    @Override
    public Boolean isBlockInvalid(final Sha256Hash blockHash, final Integer maxFailedProcessedCount) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        if (blockchainCache != null) {
            final BlockId blockId = blockchainCache.getBlockId(blockHash);
            if (blockId != null) {
                final Integer processCount = blockchainCache.getProcessCount(blockId);
                if (processCount != null) {
                    return (processCount >= BlockHeaderDatabaseManager.INVALID_PROCESS_THRESHOLD);
                }
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT process_count FROM invalid_blocks WHERE hash = ?")
                .setParameter(blockHash)
        );
        if (rows.isEmpty()) { return false; }

        final Row row = rows.get(0);
        final Integer processCount = row.getInteger("process_count");
        return (processCount >= BlockHeaderDatabaseManager.INVALID_PROCESS_THRESHOLD);
    }

    @Override
    public void markBlockAsInvalid(final Sha256Hash blockHash, final Integer processIncrement) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("INSERT INTO invalid_blocks (hash, process_count) VALUES (?, ?) ON DUPLICATE KEY UPDATE process_count = process_count + ?")
                .setParameter(blockHash)
                .setParameter(processIncrement)
                .setParameter(processIncrement)
        );

        final MutableBlockchainCache blockchainCache = _blockchainCacheFactory.getMutableBlockchainCache();
        if (blockchainCache != null) {
            final BlockId blockId = blockchainCache.getBlockId(blockHash);
            if (blockId != null) {
                blockchainCache.incrementProcessCount(blockId, 1);
            }
        }
    }

    @Override
    public void clearBlockAsInvalid(final Sha256Hash blockHash, final Integer processDecrement) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("UPDATE invalid_blocks SET process_count = GREATEST(0, CAST(process_count AS SIGNED) - ?) WHERE hash = ?")
                .setParameter(processDecrement)
                .setParameter(blockHash)
        );

        databaseConnection.executeSql(
            new Query("DELETE FROM invalid_blocks WHERE hash = ? AND process_count < 1")
                .setParameter(blockHash)
        );

        final MutableBlockchainCache blockchainCache = _blockchainCacheFactory.getMutableBlockchainCache();
        if (blockchainCache != null) {
            final BlockId blockId = blockchainCache.getBlockId(blockHash);
            if (blockId != null) {
                blockchainCache.incrementProcessCount(blockId, -processDecrement);
            }
        }
    }

    @Override
    public void visitBlockHeaders(final Visitor<BlockId> visitor) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        BlockId lastBlockId = BlockId.wrap(0L);
        while (true) {
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT id FROM blocks WHERE id > ? ORDER BY id ASC LIMIT 1024")
                    .setParameter(lastBlockId)
            );
            if (rows.isEmpty()) { break; }

            for (final Row row : rows) {
                lastBlockId = BlockId.wrap(row.getLong("id"));

                try {
                    visitor.visit(lastBlockId);
                }
                catch (final Exception exception) {
                    throw new DatabaseException(exception);
                }
            }
        }
    }

    @Override
    public BlockMetadata getBlockMetadata(final BlockId blockId) throws DatabaseException {
        if (blockId == null) { return null; }

        final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
        if (blockchainCache != null) {
            final BlockchainSegmentId blockchainSegmentId = blockchainCache.getBlockchainSegmentId(blockId);
            final Long blockHeight = blockchainCache.getBlockHeight(blockId);
            final ChainWork chainWork = blockchainCache.getChainWork(blockId);
            final MedianBlockTime medianBlockTime = blockchainCache.getMedianBlockTime(blockId);
            final Boolean hasTransactions = blockchainCache.hasTransactions(blockId);
            final BlockHeader blockHeader = blockchainCache.getBlockHeader(blockId);

            if ( (blockchainSegmentId != null) && (blockHeight != null) && (chainWork != null) && (medianBlockTime != null) && (hasTransactions != null) && (blockHeader != null) ) {
                return new BlockMetadata(blockchainSegmentId, blockHeight, chainWork, medianBlockTime, hasTransactions, blockHeader);
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT * FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }
        final Row row = rows.get(0);

        final BlockHeader blockHeader = _inflateBlockHeaderFromRow(row, blockchainCache);
        final BlockchainSegmentId blockchainSegmentId = BlockchainSegmentId.wrap(row.getLong("blockchain_segment_id"));
        final Long blockHeight = row.getLong("block_height");
        final ChainWork chainWork = ChainWork.wrap(row.getBytes("chain_work"));
        final MedianBlockTime medianBlockTime = MedianBlockTime.fromSeconds(row.getLong("median_block_time"));
        final Boolean hasTransactions = row.getBoolean("has_transactions");
        // final Integer byteCount = row.getInteger("byte_count");

        return new BlockMetadata(blockchainSegmentId, blockHeight, chainWork, medianBlockTime, hasTransactions, blockHeader);
    }

    @Override
    public Map<BlockId, Integer> getBlockProcessCounts() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT * FROM invalid_blocks")
        );

        final HashMap<BlockId, Integer> processCounts = new HashMap<>(rows.size());
        for (final Row row : rows) {
            final Sha256Hash blockHash = Sha256Hash.wrap(row.getBytes("hash"));
            final Integer processCount = row.getInteger("process_count");

            final BlockchainCache blockchainCache = _blockchainCacheFactory.getBlockchainCache();
            final BlockId blockId = _getBlockHeaderId(blockHash, blockchainCache);
            if (blockId == null) { continue; }

            processCounts.put(blockId, processCount);
        }
        return processCounts;
    }
}
