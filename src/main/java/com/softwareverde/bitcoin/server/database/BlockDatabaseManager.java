package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.ImmutableDifficulty;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.type.merkleroot.MutableMerkleRoot;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

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

    protected BlockId _getBlockIdFromHash(final Hash blockHash) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE hash = ?")
                .setParameter(BitcoinUtil.toHexString(blockHash))
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

        final Hash previousBlockHash;
        {
            final Long previousBlockId = row.getLong("previous_block_id");
            final List<Row> rows = _databaseConnection.query(
                new Query("SELECT * FROM blocks WHERE id = ?")
                    .setParameter(previousBlockId)
            );

            if (rows.isEmpty()) {
                previousBlockHash = new MutableHash();
            }
            else {
                final Row previousBlockRow = rows.get(0);
                previousBlockHash = MutableHash.fromHexString(previousBlockRow.getString("hash"));
            }
        }

        final MutableBlockHeader blockHeader = new MutableBlockHeader();
        blockHeader.setPreviousBlockHash(previousBlockHash);
        blockHeader.setVersion(row.getInteger("version"));
        blockHeader.setMerkleRoot(new MutableMerkleRoot(BitcoinUtil.hexStringToByteArray(row.getString("merkle_root"))));
        blockHeader.setTimestamp(row.getLong("timestamp"));
        blockHeader.setDifficulty(ImmutableDifficulty.decode(BitcoinUtil.hexStringToByteArray(row.getString("difficulty"))));
        blockHeader.setNonce(row.getLong("nonce"));

        { // Assert that the hashes match after inflation...
            final Hash expectedHash = MutableHash.fromHexString(row.getString("hash"));
            final Hash actualHash = blockHeader.getHash();
            if (!expectedHash.equals(actualHash)) {
                throw new DatabaseException("Unable to inflate BlockHeader.");
            }
        }

        return blockHeader;
    }

    protected void _storeBlockTransactions(final BlockId blockId, final Block block) throws DatabaseException {
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(_databaseConnection);
        for (final Transaction transaction : block.getTransactions()) {
            transactionDatabaseManager.storeTransaction(blockId, transaction);
        }
    }

    protected void _updateBlockHeader(final BlockId blockId, final BlockHeader blockHeader) throws DatabaseException {
        final BlockId previousBlockId = _getBlockIdFromHash(blockHeader.getPreviousBlockHash());
        final Long previousBlockHeight = _getBlockHeightForBlockId(previousBlockId);
        final Long blockHeight = (previousBlockHeight == null ? 0 : (previousBlockHeight + 1));

        _databaseConnection.executeSql(
            new Query("UPDATE blocks SET hash = ?, previous_block_id = ?, block_height = ?, merkle_root = ?, version = ?, timestamp = ?, difficulty = ?, nonce = ? WHERE id = ?")
                .setParameter(BitcoinUtil.toHexString(blockHeader.getHash()))
                .setParameter(previousBlockId)
                .setParameter(blockHeight)
                .setParameter(BitcoinUtil.toHexString(blockHeader.getMerkleRoot()))
                .setParameter(blockHeader.getVersion())
                .setParameter(blockHeader.getTimestamp())
                .setParameter(BitcoinUtil.toHexString(blockHeader.getDifficulty().encode()))
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
                .setParameter(BitcoinUtil.toHexString(blockHeader.getHash()))
                .setParameter(previousBlockId)
                .setParameter(blockHeight)
                .setParameter(BitcoinUtil.toHexString(blockHeader.getMerkleRoot()))
                .setParameter(blockHeader.getVersion())
                .setParameter(blockHeader.getTimestamp())
                .setParameter(BitcoinUtil.toHexString(blockHeader.getDifficulty().encode()))
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

    public BlockId storeBlockHeader(final BlockHeader blockHeader) throws DatabaseException {
        return _storeBlockHeader(blockHeader);
    }

    public BlockId storeBlock(final Block block) throws DatabaseException {
        final BlockId blockId = _storeBlockHeader(block);

        _storeBlockTransactions(blockId, block);

        return blockId;
    }

    public Hash getMostRecentBlockHash() throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(new Query("SELECT hash FROM blocks ORDER BY timestamp DESC LIMIT 1"));
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return new MutableHash(BitcoinUtil.hexStringToByteArray(row.getString("hash")));
    }

    public BlockId getBlockIdFromHash(final Hash blockHash) throws DatabaseException {
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
}
