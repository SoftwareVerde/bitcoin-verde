package com.softwareverde.bitcoin.test.fake.database;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

import java.util.HashMap;

public class MockBlockchainDatabaseManager implements FakeBlockchainDatabaseManager {
    protected BlockchainSegmentId _headBlockchainSegmentId = null;
    protected MutableList<BlockchainSegmentId> _leafBlockchainSegmentIds = new MutableList<>();
    protected HashMap<BlockchainSegmentId, BlockId> _startingBlockIds = new HashMap<>();
    protected HashMap<BlockchainSegmentId, BlockId> _headBlockIds = new HashMap<>();
    protected HashMap<BlockchainSegmentId, BlockchainSegmentId> _parentBlockchainSegmentIds = new HashMap<>();

    public void setHeadBlockchainSegmentId(final BlockchainSegmentId blockchainSegmentId) {
        _headBlockchainSegmentId = blockchainSegmentId;
    }

    public void addLeafBlockchainSegmentId(final BlockchainSegmentId blockchainSegmentId) {
        _leafBlockchainSegmentIds.add(blockchainSegmentId);
    }

    public void setParentBlockchainSegmentId(final BlockchainSegmentId blockchainSegmentId, final BlockchainSegmentId parentBlockchainSegmentId) {
        _parentBlockchainSegmentIds.put(blockchainSegmentId, parentBlockchainSegmentId);
    }

    /**
     * Sets the tip/head/highest BlockId of the blockchain segment...
     */
    public void setHeadBlockId(final BlockchainSegmentId blockchainSegmentId, final BlockId blockId) {
        _headBlockIds.put(blockchainSegmentId, blockId);
    }

    /**
     * Sets the first/tail/lowest BlockId of the blockchain segment...
     */
    public void setTailBlockId(final BlockchainSegmentId blockchainSegmentId, final BlockId blockId) {
        _startingBlockIds.put(blockchainSegmentId, blockId);
    }

    @Override
    public BlockchainSegmentId getHeadBlockchainSegmentId() throws DatabaseException {
        return _headBlockchainSegmentId;
    }

    @Override
    public List<BlockchainSegmentId> getLeafBlockchainSegmentIds() throws DatabaseException {
        return _leafBlockchainSegmentIds;
    }

    @Override
    public BlockchainSegmentId getPreviousBlockchainSegmentId(final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        if (! _parentBlockchainSegmentIds.containsKey(blockchainSegmentId)) {
            Logger.warn("MOCK: Undefined previous BlockchainSegmentId for BlockchainSegment: " + blockchainSegmentId, new Exception());
        }
        return _parentBlockchainSegmentIds.get(blockchainSegmentId);
    }

    @Override
    public BlockId getFirstBlockIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        return _startingBlockIds.get(blockchainSegmentId);
    }

    @Override
    public BlockId getHeadBlockIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        return _headBlockIds.get(blockchainSegmentId);
    }
}
