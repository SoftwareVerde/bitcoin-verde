package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.ImmutableDifficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.BlockWork;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.header.difficulty.work.MutableChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.type.merkleroot.MutableMerkleRoot;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

public class BlockHeaderDatabaseManager {
    public static final Object MUTEX = new Object();

    /**
     * Initializes a MedianBlockTime from the database.
     *  NOTE: The headBlockHash is included within the MedianBlockTime.
     */
    protected static MutableMedianBlockTime _newInitializedMedianBlockTime(final BlockHeaderDatabaseManager blockDatabaseManager, final Sha256Hash headBlockHash) throws DatabaseException {
        // Initializes medianBlockTime with the N most recent blocks...

        final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();

        final java.util.List<BlockHeader> blockHeadersInDescendingOrder = new java.util.ArrayList<BlockHeader>(MedianBlockTime.BLOCK_COUNT);

        Sha256Hash blockHash = headBlockHash;
        for (int i = 0; i < MedianBlockTime.BLOCK_COUNT; ++i) {
            final BlockId blockId = blockDatabaseManager.getBlockHeaderId(blockHash);
            if (blockId == null) { break; }

            final BlockHeader blockHeader = blockDatabaseManager.getBlockHeader(blockId);
            blockHeadersInDescendingOrder.add(blockHeader);
            blockHash = blockHeader.getPreviousBlockHash();
        }

        // Add the blocks to the MedianBlockTime in ascending order (lowest block-height is added first)...
        for (int i = 0; i < blockHeadersInDescendingOrder.size(); ++i) {
            final BlockHeader blockHeader = blockHeadersInDescendingOrder.get(blockHeadersInDescendingOrder.size() - i - 1);
            medianBlockTime.addBlock(blockHeader);
        }

        return medianBlockTime;
    }

    protected final MysqlDatabaseConnection _databaseConnection;
    protected final DatabaseManagerCache _databaseManagerCache;

    public BlockHeaderDatabaseManager(final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnection = databaseConnection;
        _databaseManagerCache = databaseManagerCache;
    }

    protected Long _getBlockHeight(final BlockId blockId) throws DatabaseException {
        final Long cachedBlockHeight = _databaseManagerCache.getCachedBlockHeight(blockId);
        if (cachedBlockHeight != null) { return cachedBlockHeight; }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, block_height FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long blockHeight = row.getLong("block_height");
        _databaseManagerCache.cacheBlockHeight(blockId, blockHeight);
        return blockHeight;
    }

    protected BlockId _getBlockHeaderId(final Sha256Hash blockHash) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE hash = ?")
                .setParameter(blockHash)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("id"));
    }

    protected Sha256Hash _getBlockHash(final BlockId blockId) throws DatabaseException {
        if (blockId == null) { return new MutableSha256Hash(); }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, hash FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) {
            return new MutableSha256Hash();
        }
        else {
            final Row previousBlockRow = rows.get(0);
            final String hashString = previousBlockRow.getString("hash");
            return Sha256Hash.fromHexString(hashString);
        }
    }

    protected BlockHeader _inflateBlockHeader(final BlockId blockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT * FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }
        final Row row = rows.get(0);

        final Long version = row.getLong("version");

        final Sha256Hash previousBlockHash;
        {
            final BlockId previousBlockId = BlockId.wrap(row.getLong("previous_block_id"));
            previousBlockHash = _getBlockHash(previousBlockId);
        }

        final MerkleRoot merkleRoot = MutableMerkleRoot.fromHexString(row.getString("merkle_root"));
        final Long timestamp = row.getLong("timestamp");
        final Difficulty difficulty = ImmutableDifficulty.decode(HexUtil.hexStringToByteArray(row.getString("difficulty")));
        final Long nonce = row.getLong("nonce");

        final MutableBlockHeader blockHeader = new MutableBlockHeader();

        blockHeader.setVersion(version);
        blockHeader.setPreviousBlockHash(previousBlockHash);
        blockHeader.setMerkleRoot(merkleRoot);
        blockHeader.setTimestamp(timestamp);
        blockHeader.setDifficulty(difficulty);
        blockHeader.setNonce(nonce);

        { // Assert that the hashes match after inflation...
            final Sha256Hash expectedHash = Sha256Hash.fromHexString(row.getString("hash"));
            final Sha256Hash actualHash = blockHeader.getHash();
            if (! Util.areEqual(expectedHash, actualHash)) {
                Logger.log("ERROR: Unable to inflate block: " + blockHeader.getHash());
                return null;
            }
        }

        return blockHeader;
    }

    protected void _updateBlockHeader(final BlockId blockId, final BlockHeader blockHeader) throws DatabaseException {
        final BlockId previousBlockId = _getBlockHeaderId(blockHeader.getPreviousBlockHash());
        final Long previousBlockHeight = _getBlockHeight(previousBlockId);
        final Long blockHeight = (previousBlockHeight == null ? 0 : (previousBlockHeight + 1));

        _databaseConnection.executeSql(
            new Query("UPDATE blocks SET hash = ?, previous_block_id = ?, block_height = ?, merkle_root = ?, version = ?, timestamp = ?, difficulty = ?, nonce = ? WHERE id = ?")
                .setParameter(blockHeader.getHash())
                .setParameter(previousBlockId)
                .setParameter(blockHeight)
                .setParameter(blockHeader.getMerkleRoot())
                .setParameter(blockHeader.getVersion())
                .setParameter(blockHeader.getTimestamp())
                .setParameter(blockHeader.getDifficulty().encode())
                .setParameter(blockHeader.getNonce())
                .setParameter(blockId)
        );
    }

    protected ChainWork _getChainWork(final BlockId blockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, chain_work FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return ChainWork.fromHexString(row.getString("chain_work"));
    }

    protected BlockId _insertBlockHeader(final BlockHeader blockHeader) throws DatabaseException {
        final BlockId previousBlockId = _getBlockHeaderId(blockHeader.getPreviousBlockHash());
        final Long previousBlockHeight = _getBlockHeight(previousBlockId);
        final Long blockHeight = (previousBlockId == null ? 0 : (previousBlockHeight + 1));
        final Difficulty difficulty = blockHeader.getDifficulty();

        final BlockWork blockWork = difficulty.calculateWork();
        final ChainWork previousChainWork = (previousBlockId == null ? new MutableChainWork() : _getChainWork(previousBlockId));
        final ChainWork chainWork = ChainWork.add(previousChainWork, blockWork);

        return BlockId.wrap(_databaseConnection.executeSql(
            new Query("INSERT INTO blocks (hash, previous_block_id, block_height, merkle_root, version, timestamp, difficulty, nonce, chain_work) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .setParameter(blockHeader.getHash())
                .setParameter(previousBlockId)
                .setParameter(blockHeight)
                .setParameter(blockHeader.getMerkleRoot())
                .setParameter(blockHeader.getVersion())
                .setParameter(blockHeader.getTimestamp())
                .setParameter(difficulty.encode())
                .setParameter(blockHeader.getNonce())
                .setParameter(chainWork)
        ));
    }

    protected void _setBlockchainSegmentId(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        _databaseManagerCache.cacheBlockchainSegmentId(blockId, blockchainSegmentId);

        _databaseConnection.executeSql(
            new Query("UPDATE blocks SET blockchain_segment_id = ? WHERE id = ?")
                .setParameter(blockchainSegmentId)
                .setParameter(blockId)
        );
    }

    protected BlockchainSegmentId _getBlockchainSegmentId(final BlockId blockId) throws DatabaseException {
        { // Attempt to find BlockchainSegmentId from cache...
            final BlockchainSegmentId cachedBlockchainSegmentId = _databaseManagerCache.getCachedBlockchainSegmentId(blockId);
            if (cachedBlockchainSegmentId != null) { return cachedBlockchainSegmentId; }
        }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, blockchain_segment_id FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );
        if (rows.isEmpty()) { return null; }
        final Row row = rows.get(0);

        final BlockchainSegmentId blockchainSegmentId = BlockchainSegmentId.wrap(row.getLong("blockchain_segment_id"));

        _databaseManagerCache.cacheBlockchainSegmentId(blockId, blockchainSegmentId);

        return blockchainSegmentId;
    }

    protected BlockId _getBlockIdAtBlockHeight(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE blockchain_segment_id = ? AND block_height = ?")
                .setParameter(blockchainSegmentId)
                .setParameter(blockHeight)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("id"));
    }

    protected Boolean _isBlockConnectedToChain(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId, final BlockRelationship blockRelationship) throws DatabaseException {
        final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(_databaseConnection, _databaseManagerCache);
        final BlockchainSegmentId blockchainSegmentId1 = _getBlockchainSegmentId(blockId);
        return blockchainDatabaseManager.areBlockchainSegmentsConnected(blockchainSegmentId1, blockchainSegmentId, blockRelationship);
    }

    protected BlockId _getChildBlockId(final BlockchainSegmentId blockchainSegmentId, final BlockId previousBlockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, blockchain_segment_id FROM blocks WHERE previous_block_id = ?")
                .setParameter(previousBlockId)
        );

        if (rows.isEmpty()) { return null; }

        if (rows.size() == 1) {
            final Row row = rows.get(0);
            return BlockId.wrap(row.getLong("id"));
        }

        // At this point, previousBlockId has multiple children.
        // If blockchainSegmentId is not provided, then just return the first-seen block.
        if (blockchainSegmentId == null) {
            final Row row = rows.get(0);
            return BlockId.wrap(row.getLong("id"));
        }

        // Since blockchainSegmentId is provided, the child along its chain is the blockId that shall be preferred...
        final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(_databaseConnection, _databaseManagerCache);
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

    protected Sha256Hash _getHeadBlockHeaderHash() throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, hash FROM blocks ORDER BY block_height DESC LIMIT 1")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.fromHexString(row.getString("hash"));
    }

    protected BlockId _getHeadBlockHeaderId() throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, hash FROM blocks ORDER BY block_height DESC LIMIT 1")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("id"));
    }

    protected Integer _getBlockHeaderDirectDescendantCount(final BlockId blockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE previous_block_id = ?")
                .setParameter(blockId)
        );

        return (rows.size());
    }

    protected BlockId _getPreviousBlockId(final BlockId blockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, previous_block_id FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("previous_block_id"));
    }

    public BlockId insertBlockHeader(final BlockHeader blockHeader) throws DatabaseException {
        if (! Thread.holdsLock(MUTEX)) { throw new RuntimeException("Attempting to insertBlockHeader without obtaining lock."); }

        final BlockId blockId = _insertBlockHeader(blockHeader);

        final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(_databaseConnection, _databaseManagerCache);
        blockchainDatabaseManager.updateBlockchainsForNewBlock(blockId);

        return blockId;
    }

    public void updateBlockHeader(final BlockId blockId, final BlockHeader blockHeader) throws DatabaseException {
        _updateBlockHeader(blockId, blockHeader);
    }

    public BlockId storeBlockHeader(final BlockHeader blockHeader) throws DatabaseException {
        if (! Thread.holdsLock(MUTEX)) { throw new RuntimeException("Attempting to storeBlockHeader without obtaining lock."); }

        final BlockId existingBlockId = _getBlockHeaderId(blockHeader.getHash());

        if (existingBlockId != null) {
            return existingBlockId;
        }

        final BlockId blockId = _insertBlockHeader(blockHeader);

        final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(_databaseConnection, _databaseManagerCache);
        blockchainDatabaseManager.updateBlockchainsForNewBlock(blockId);

        return blockId;
    }

    public void setBlockByteCount(final BlockId blockId, final Integer byteCount) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("UPDATE blocks SET byte_count = ? WHERE id = ?")
                .setParameter(byteCount)
                .setParameter(blockId)
        );
    }

    public Integer getBlockByteCount(final BlockId blockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
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
    public Sha256Hash getHeadBlockHeaderHash() throws DatabaseException {
        return _getHeadBlockHeaderHash();
    }

    /**
     * Returns the BlockId of the block that has the tallest block-height.
     */
    public BlockId getHeadBlockHeaderId() throws DatabaseException {
        return _getHeadBlockHeaderId();
    }

    public BlockId getBlockHeaderId(final Sha256Hash blockHash) throws DatabaseException {
        return _getBlockHeaderId(blockHash);
    }

    public BlockHeader getBlockHeader(final BlockId blockId) throws DatabaseException {
        return _inflateBlockHeader(blockId);
    }

    /**
     * Returns true if the BlockHeader has been downloaded and verified.
     */
    public Boolean blockHeaderExists(final Sha256Hash blockHash) throws DatabaseException {
        final BlockId blockId = _getBlockHeaderId(blockHash);
        return (blockId != null);
    }

    public Integer getBlockDirectDescendantCount(final BlockId blockId) throws DatabaseException {
        return _getBlockHeaderDirectDescendantCount(blockId);
    }

    public void setBlockchainSegmentId(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        _setBlockchainSegmentId(blockId, blockchainSegmentId);
    }

    public BlockchainSegmentId getBlockchainSegmentId(final BlockId blockId) throws DatabaseException {
        return _getBlockchainSegmentId(blockId);
    }

    public Long getBlockHeight(final BlockId blockId) throws DatabaseException {
        return _getBlockHeight(blockId);
    }

    public BlockId getChildBlockId(final BlockchainSegmentId blockchainSegmentId, final BlockId previousBlockId) throws DatabaseException {
        return _getChildBlockId(blockchainSegmentId, previousBlockId);
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
    public Boolean isBlockConnectedToChain(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId, final BlockRelationship blockRelationship) throws DatabaseException {
        return _isBlockConnectedToChain(blockId, blockchainSegmentId, blockRelationship);
    }

    public Sha256Hash getBlockHash(final BlockId blockId) throws DatabaseException {
        return _getBlockHash(blockId);
    }

    /**
     * Returns the BlockId of the nth-parent, where n is the parentCount.
     *  For instance, getAncestor(blockId, 0) returns blockId, and getAncestor(blockId, 1) returns blockId's parent.
     */
    public BlockId getAncestorBlockId(final BlockId blockId, final Integer parentCount) throws DatabaseException {
        if (blockId == null) { return null; }

        if (parentCount == 1) {
            // Optimization/Specialization for parentBlockId...
            return _getPreviousBlockId(blockId);
        }

        BlockId nextBlockId = blockId;
        for (int i = 0; i < parentCount; ++i) {
            final BlockHeader blockHeader = _inflateBlockHeader(nextBlockId);
            if (blockHeader == null) { return null; }

            nextBlockId = _getBlockHeaderId(blockHeader.getPreviousBlockHash());
        }
        return nextBlockId;
    }

    /**
     * Initializes a Mutable MedianBlockTime using only blocks that have been fully validated.
     */
    public MutableMedianBlockTime initializeMedianBlockTime() throws DatabaseException {
        Sha256Hash blockHash = Util.coalesce(_getHeadBlockHeaderHash(), BlockHeader.GENESIS_BLOCK_HASH);
        return _newInitializedMedianBlockTime(this, blockHash);
    }

    /**
     * Initializes a Mutable MedianBlockTime using most recent block headers.
     *  The significant difference between MutableMedianBlockTime.newInitializedMedianBlockHeaderTime and MutableMedianBlockTime.newInitializedMedianBlockTime
     *  is that BlockHeaders are downloaded and validated more quickly than blocks; therefore when validating blocks
     *  MutableMedianBlockTime.newInitializedMedianBlockTime should be used, not this function.
     */
    public MutableMedianBlockTime initializeMedianBlockHeaderTime() throws DatabaseException {
        Sha256Hash blockHash = Util.coalesce(_getHeadBlockHeaderHash(), BlockHeader.GENESIS_BLOCK_HASH);
        return _newInitializedMedianBlockTime(this, blockHash);
    }

    /**
     * Calculates the MedianBlockTime of the provided startingBlockId.
     * NOTE: startingBlockId is exclusive. The MedianBlockTime does NOT include the provided startingBlockId; instead,
     *  it includes the MedianBlockTime.BLOCK_COUNT (11) number of blocks before the startingBlockId.
     */
    public MedianBlockTime calculateMedianBlockTime(final BlockId startingBlockId) throws DatabaseException {
        final BlockHeader startingBlock = _inflateBlockHeader(startingBlockId);
        if (startingBlock == null) { return null; }

        final Sha256Hash blockHash = startingBlock.getPreviousBlockHash();
        return _newInitializedMedianBlockTime(this, blockHash);
    }

    public ChainWork getChainWork(final BlockId blockId) throws DatabaseException {
        return _getChainWork(blockId);
    }

    public BlockId getBlockIdAtHeight(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE block_height = ?")
                .setParameter(blockHeight)
        );

        for (final Row row : rows) {
            final BlockId blockId = BlockId.wrap(row.getLong("id"));

            final Boolean blockIsConnectedToChain = _isBlockConnectedToChain(blockId, blockchainSegmentId, BlockRelationship.ANY);
            if (blockIsConnectedToChain) {
                return blockId;
            }
        }

        return null;
    }
}
