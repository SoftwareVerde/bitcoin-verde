package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.ImmutableDifficulty;
import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.type.merkleroot.MutableMerkleRoot;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.util.HexUtil;

import java.util.List;

public class BlockDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;

    public BlockDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    protected Long _getBlockHeightForBlockId(final BlockId blockId) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, block_height FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return row.getLong("block_height");
    }

    protected BlockId _getBlockIdFromHash(final Sha256Hash blockHash) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE hash = ?")
                .setParameter(HexUtil.toHexString(blockHash.getBytes()))
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("id"));
    }

    protected BlockHeader _inflateBlockHeader(final BlockId blockId) throws DatabaseException {
        final Row row;
        {
            final List<Row> rows = _databaseConnection.query(
                new Query("SELECT * FROM blocks WHERE id = ?")
                    .setParameter(blockId)
            );

            if (rows.isEmpty()) { return null; }
            row = rows.get(0);
        }

        final Sha256Hash previousBlockHash;
        {
            final Long previousBlockId = row.getLong("previous_block_id");
            final List<Row> rows = _databaseConnection.query(
                new Query("SELECT * FROM blocks WHERE id = ?")
                    .setParameter(previousBlockId)
            );

            if (rows.isEmpty()) {
                previousBlockHash = new MutableSha256Hash();
            }
            else {
                final Row previousBlockRow = rows.get(0);
                previousBlockHash = MutableSha256Hash.fromHexString(previousBlockRow.getString("hash"));
            }
        }

        final MutableBlockHeader blockHeader = new MutableBlockHeader();
        blockHeader.setPreviousBlockHash(previousBlockHash);
        blockHeader.setVersion(row.getInteger("version"));
        blockHeader.setMerkleRoot(MutableMerkleRoot.wrap(HexUtil.hexStringToByteArray(row.getString("merkle_root"))));
        blockHeader.setTimestamp(row.getLong("timestamp"));
        blockHeader.setDifficulty(ImmutableDifficulty.decode(HexUtil.hexStringToByteArray(row.getString("difficulty"))));
        blockHeader.setNonce(row.getLong("nonce"));

        { // Assert that the hashes match after inflation...
            final Sha256Hash expectedHash = MutableSha256Hash.fromHexString(row.getString("hash"));
            final Sha256Hash actualHash = blockHeader.getHash();
            if (! expectedHash.equals(actualHash)) {
                throw new DatabaseException("Unable to inflate BlockHeader.");
            }
        }

        return blockHeader;
    }

    protected void _storeBlockTransactions(final BlockChainSegmentId blockChainSegmentId, final BlockId blockId, final Block block) throws DatabaseException {
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(_databaseConnection);
        for (final Transaction transaction : block.getTransactions()) {
            transactionDatabaseManager.storeTransaction(blockChainSegmentId, blockId, transaction);
        }
    }

    protected void _updateBlockHeader(final BlockId blockId, final BlockHeader blockHeader) throws DatabaseException {
        final BlockId previousBlockId = _getBlockIdFromHash(blockHeader.getPreviousBlockHash());
        final Long previousBlockHeight = _getBlockHeightForBlockId(previousBlockId);
        final Long blockHeight = (previousBlockHeight == null ? 0 : (previousBlockHeight + 1));

        _databaseConnection.executeSql(
            new Query("UPDATE blocks SET hash = ?, previous_block_id = ?, block_height = ?, merkle_root = ?, version = ?, timestamp = ?, difficulty = ?, nonce = ? WHERE id = ?")
                .setParameter(HexUtil.toHexString(blockHeader.getHash().getBytes()))
                .setParameter(previousBlockId)
                .setParameter(blockHeight)
                .setParameter(HexUtil.toHexString(blockHeader.getMerkleRoot().getBytes()))
                .setParameter(blockHeader.getVersion())
                .setParameter(blockHeader.getTimestamp())
                .setParameter(HexUtil.toHexString(blockHeader.getDifficulty().encode()))
                .setParameter(blockHeader.getNonce())
                .setParameter(blockId)
        );
    }

    protected BlockId _insertBlockHeader(final BlockHeader blockHeader) throws DatabaseException {
        final BlockId previousBlockId = _getBlockIdFromHash(blockHeader.getPreviousBlockHash());
        final Long previousBlockHeight = _getBlockHeightForBlockId(previousBlockId);
        final Long blockHeight = (previousBlockHeight == null ? 0 : (previousBlockHeight + 1));

        return BlockId.wrap(_databaseConnection.executeSql(
            new Query("INSERT INTO blocks (hash, previous_block_id, block_height, merkle_root, version, timestamp, difficulty, nonce) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .setParameter(HexUtil.toHexString(blockHeader.getHash().getBytes()))
                .setParameter(previousBlockId)
                .setParameter(blockHeight)
                .setParameter(HexUtil.toHexString(blockHeader.getMerkleRoot().getBytes()))
                .setParameter(blockHeader.getVersion())
                .setParameter(blockHeader.getTimestamp())
                .setParameter(HexUtil.toHexString(blockHeader.getDifficulty().encode()))
                .setParameter(blockHeader.getNonce())
        ));
    }

    protected BlockId _storeBlockHeader(final BlockHeader blockHeader) throws DatabaseException {
        final BlockId blockId;
        {
            final BlockId existingBlockId = _getBlockIdFromHash(blockHeader.getHash());
            if (existingBlockId != null) {
                _updateBlockHeader(existingBlockId, blockHeader);
                blockId = existingBlockId;
            }
            else {
                blockId = _insertBlockHeader(blockHeader);
            }
        }
        return blockId;
    }

    protected void _setBlockChainSegmentId(final BlockId blockId, final BlockChainSegmentId blockChainSegmentId) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("UPDATE blocks SET block_chain_segment_id = ? WHERE id = ?")
                .setParameter(blockChainSegmentId)
                .setParameter(blockId)
        );
    }

    protected BlockHeader _getBlockAtBlockHeight(final BlockChainSegmentId blockChainSegmentId, final Long blockHeight) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE block_chain_segment_id = ? AND block_height = ?")
                .setParameter(blockChainSegmentId)
                .setParameter(blockHeight)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final BlockId blockId = BlockId.wrap(row.getLong("id"));
        return _inflateBlockHeader(blockId);
    }

    protected com.softwareverde.constable.list.List<Transaction> _getBlockTransactions(final BlockId blockId, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM transactions WHERE block_id = ?")
                .setParameter(blockId)
        );

        final ImmutableListBuilder<Transaction> listBuilder = new ImmutableListBuilder<Transaction>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
            final Transaction transaction = transactionDatabaseManager.fromDatabaseConnection(transactionId);
            listBuilder.add(transaction);
        }
        return listBuilder.build();
    }

    protected BlockHeader _blockHeaderFromDatabaseConnection(final BlockId blockId, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT * FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }
        final Row row = rows.get(0);

        final Integer version = row.getInteger("version");

        final Sha256Hash previousBlockHash;
        {
            final java.util.List<Row> previousBlockRows = databaseConnection.query(
                new Query("SELECT id, hash FROM blocks WHERE id = ?")
                    .setParameter(row.getLong("previous_block_id"))
            );
            if (previousBlockRows.isEmpty()) {
                previousBlockHash = new ImmutableSha256Hash();
            }
            else {
                final Row previousBlockRow = previousBlockRows.get(0);
                final String previousBlockHashString = previousBlockRow.getString("hash");
                previousBlockHash = MutableSha256Hash.fromHexString(previousBlockHashString);
            }
        }

        final MerkleRoot merkleRoot = MutableMerkleRoot.fromHexString(row.getString("merkle_root"));
        final Long timestamp = row.getLong("timestamp");
        final Difficulty difficulty = ImmutableDifficulty.decode(HexUtil.hexStringToByteArray(row.getString("difficulty")));
        final Long nonce = row.getLong("nonce");

        final MutableBlockHeader mutableBlockHeader = new MutableBlockHeader();

        mutableBlockHeader.setVersion(version);
        mutableBlockHeader.setPreviousBlockHash(previousBlockHash);
        mutableBlockHeader.setMerkleRoot(merkleRoot);
        mutableBlockHeader.setTimestamp(timestamp);
        mutableBlockHeader.setDifficulty(difficulty);
        mutableBlockHeader.setNonce(nonce);

        return mutableBlockHeader;
    }

    public BlockId storeBlockHeader(final BlockHeader blockHeader) throws DatabaseException {
        return _storeBlockHeader(blockHeader);
    }

    public BlockId storeBlock(final Block block) throws DatabaseException {
        final BlockId blockId = _storeBlockHeader(block);

        final BlockChainSegmentId blockChainSegmentId;
        {
            final Sha256Hash previousBlockHash = block.getPreviousBlockHash();
            final BlockId previousBlockId = _getBlockIdFromHash(previousBlockHash);
            if (previousBlockId != null) {
                final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(_databaseConnection);
                blockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(previousBlockId);
            }
            else {
                blockChainSegmentId = null;
            }
        }

        _storeBlockTransactions(blockChainSegmentId, blockId, block);

        return blockId;
    }

    /**
     * Returns the Sha256Hash of the block that has the tallest block-height.
     */
    public Sha256Hash getHeadBlockHash() throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(new Query("SELECT hash FROM blocks ORDER BY block_height DESC LIMIT 1"));
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return MutableSha256Hash.wrap(HexUtil.hexStringToByteArray(row.getString("hash")));
    }

    public BlockId getBlockIdFromHash(final Sha256Hash blockHash) throws DatabaseException {
        return _getBlockIdFromHash(blockHash);
    }

    public BlockHeader getBlockHeader(final BlockId blockId) throws DatabaseException {
        return _inflateBlockHeader(blockId);
    }

    public Integer getBlockDirectDescendantCount(final BlockId blockId) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE previous_block_id = ?")
                .setParameter(blockId)
        );
        return (rows.size());
    }

    public void setBlockChainSegmentId(final BlockId blockId, final BlockChainSegmentId blockChainSegmentId) throws DatabaseException {
        _setBlockChainSegmentId(blockId, blockChainSegmentId);
    }

    public BlockChainSegmentId getBlockChainSegmentId(final BlockId blockId) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, block_chain_segment_id FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockChainSegmentId.wrap(row.getLong("block_chain_segment_id"));
    }

    public Long getBlockHeightForBlockId(final BlockId blockId) throws DatabaseException {
        return _getBlockHeightForBlockId(blockId);
    }

    public BlockHeader findBlockAtBlockHeight(final BlockChainSegmentId startingBlockChainSegmentId, final Long blockHeight) throws DatabaseException {
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(_databaseConnection);

        BlockChainSegmentId blockChainSegmentId = startingBlockChainSegmentId;
        while (true) {
            final BlockChainSegment blockChainSegment = blockChainDatabaseManager.getBlockChainSegment(blockChainSegmentId);
            if (blockChainSegment == null) { break; }

            final long lowerBound = (blockChainSegment.getBlockHeight() - blockChainSegment.getBlockCount());
            final long upperBound = (blockChainSegment.getBlockHeight());
            if (lowerBound <= blockHeight && blockHeight <= upperBound) {
                return _getBlockAtBlockHeight(blockChainSegmentId, blockHeight);
            }

            final BlockId nextBlockId = blockChainSegment.getTailBlockId();
            final BlockChainSegmentId nextBlockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(nextBlockId);
            if (blockChainSegmentId.equals(nextBlockChainSegmentId)) { break; }

            blockChainSegmentId = nextBlockChainSegmentId;
        }

        return null;
    }

    public BlockHeader blockHeaderFromDatabaseConnection(final BlockId blockId, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        return _blockHeaderFromDatabaseConnection(blockId, databaseConnection);
    }

    public MutableBlock blockFromDatabaseConnection(final BlockId blockId, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final BlockHeader blockHeader = _blockHeaderFromDatabaseConnection(blockId, databaseConnection);
        if (blockHeader == null) { return null; }

        final com.softwareverde.constable.list.List<Transaction> transactions = _getBlockTransactions(blockId, databaseConnection);

        return new MutableBlock(blockHeader, transactions);
    }
}
