package com.softwareverde.bitcoin.test.fake.database;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class MockBlockDatabaseManager extends FullNodeBlockDatabaseManager {
    protected final MutableMap<BlockId, MutableBlock> _blocks = new MutableHashMap<>();
    protected final MutableMap<BlockchainSegmentId, BlockId> _blockchainSegmentsHeadBlockIds = new MutableHashMap<>();

    protected final MutableMap<Sha256Hash, BlockId> _blockIds = new MutableHashMap<>();
    protected final MutableMap<BlockId, List<TransactionId>> _blockTransactionIds = new MutableHashMap<>();

    protected MutableMedianBlockTime _medianBlockTime;
    protected BlockId _headBlockId;
    protected Sha256Hash _headBlockHash;

    public void defineBlock(final BlockId blockId, final Sha256Hash blockHash, final List<TransactionId> transactionIds) {
        _blockIds.put(blockHash, blockId);
        _blockTransactionIds.put(blockId, transactionIds);
    }

    public void setHeadBlockId(final BlockId headBlockId) {
        _headBlockId = headBlockId;
    }

    public void setHeadBlockHash(final Sha256Hash headBlockHash) {
        _headBlockHash = headBlockHash;
    }

    public void setMedianBlockTime(final MutableMedianBlockTime medianBlockTime) {
        _medianBlockTime = medianBlockTime;
    }

    public void setHeadBlockIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId, final BlockId blockId) {
        _blockchainSegmentsHeadBlockIds.put(blockchainSegmentId, blockId);
    }

    public MockBlockDatabaseManager() {
        super(null, null, null);
    }

    @Override
    public MutableBlock getBlock(final BlockId blockId) throws DatabaseException {
        return _blocks.get(blockId);
    }

    @Override
    public BlockId storeBlock(final Block block) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public BlockId storeBlock(final Block block, final MutableList<TransactionId> returnedTransactionIds) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public BlockId storeBlock(final Block block, final MutableList<TransactionId> returnedTransactionIds, final DatabaseConnectionFactory databaseConnectionFactory, final Integer maxThreadCount) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<TransactionId> storeBlockTransactions(final Block block) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<TransactionId> storeBlockTransactions(final Block block, final DatabaseConnectionFactory databaseConnectionFactory, final Integer maxThreadCount) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public BlockId insertBlock(final Block block) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public BlockId insertBlock(final Block block, final MutableList<TransactionId> returnedTransactionIds) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public BlockId insertBlock(final Block block, final MutableList<TransactionId> returnedTransactionIds, final DatabaseConnectionFactory databaseConnectionFactory, final Integer maxThreadCount) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Sha256Hash getHeadBlockHash() throws DatabaseException {
        return _headBlockHash;
    }

    @Override
    public BlockId getHeadBlockId() throws DatabaseException {
        return _headBlockId;
    }

    @Override
    public BlockId getHeadBlockIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        if (! _blockchainSegmentsHeadBlockIds.containsKey(blockchainSegmentId)) {
            Logger.warn("MOCK: Undefined head BlockId of BlockchainSegment: " + blockchainSegmentId, new Exception());
        }
        return _blockchainSegmentsHeadBlockIds.get(blockchainSegmentId);
    }

    @Override
    public Boolean hasTransactions(final Sha256Hash blockHash) throws DatabaseException {
        final BlockId blockId = _blockIds.get(blockHash);
        return (_blockTransactionIds.get(blockId) != null);
    }

    @Override
    public Boolean hasTransactions(final BlockId blockId) throws DatabaseException {
        return (_blockTransactionIds.get(blockId) != null);
    }

    @Override
    public List<TransactionId> getTransactionIds(final BlockId blockId) throws DatabaseException {
        return _blockTransactionIds.get(blockId);
    }

    @Override
    public MutableMedianBlockTime calculateMedianBlockTime() throws DatabaseException {
        return _medianBlockTime;
    }

    @Override
    public Integer getTransactionCount(final BlockId blockId) throws DatabaseException {
        final List<TransactionId> transactionIds = _blockTransactionIds.get(blockId);
        if (transactionIds == null) { return 0; }
        return transactionIds.getCount();
    }
}
