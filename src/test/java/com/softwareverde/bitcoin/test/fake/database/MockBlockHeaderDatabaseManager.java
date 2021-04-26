package com.softwareverde.bitcoin.test.fake.database;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class MockBlockHeaderDatabaseManager implements FakeBlockHeaderDatabaseManager {
    protected final HashMap<Sha256Hash, BlockId> _blockIds = new HashMap<>();
    protected final HashMap<BlockId, BlockHeader> _blockHeaders = new HashMap<>();
    protected final HashMap<BlockId, MutableMedianBlockTime> _medianBlockTimes = new HashMap<>();
    protected final HashMap<BlockId, MutableMedianBlockTime> _medianTimesPast = new HashMap<>();
    protected final HashMap<Sha256Hash, Integer> _invalidBlockCounts = new HashMap<>();
    protected final HashMap<BlockId, BlockchainSegmentId> _blockchainSegmentIds = new HashMap<>();
    protected final HashMap<BlockId, Long> _blockHeights = new HashMap<>();
    protected final HashMap<BlockId, Long> _blockTimestamps = new HashMap<>();
    protected final HashMap<BlockchainSegmentId, HashSet<BlockchainSegmentId>> _connectedBlockchainSegmentIds = new HashMap<>();
    protected final HashMap<BlockId, ChainWork> _chainWorks = new HashMap<>();

    protected BlockId _headBlockId;
    protected MutableMedianBlockTime _medianBlockTime;

    public void setHeadBlockId(final BlockId headBlockId) {
        _headBlockId = headBlockId;
    }

    public void setMedianBlockTime(final MutableMedianBlockTime medianBlockTime) {
        _medianBlockTime = medianBlockTime;
    }

    public void defineBlock(final BlockchainSegmentId blockchainSegmentId, final BlockId blockId, final Sha256Hash blockHash, final Long blockHeight) {
        this.defineBlock(blockchainSegmentId, blockId, blockHash, blockHeight, null, null);
    }

    public void defineBlock(final BlockchainSegmentId blockchainSegmentId, final BlockId blockId, final Sha256Hash blockHash, final Long blockHeight, final MutableMedianBlockTime medianBlockTime, final BlockHeader blockHeader) {
        _blockIds.put(blockHash, blockId);
        _blockHeaders.put(blockId, blockHeader);
        _medianBlockTimes.put(blockId, medianBlockTime);
        _blockchainSegmentIds.put(blockId, blockchainSegmentId);
        _blockHeights.put(blockId, blockHeight);
    }

    public void linkBlockchainSegments(final BlockchainSegmentId blockchainSegmentId0, final BlockchainSegmentId blockchainSegmentId1) {
        {
            _connectedBlockchainSegmentIds.putIfAbsent(blockchainSegmentId0, new HashSet<>());
            final HashSet<BlockchainSegmentId> connectedBlockchainSegmentIds = _connectedBlockchainSegmentIds.get(blockchainSegmentId0);
            connectedBlockchainSegmentIds.add(blockchainSegmentId1);
        }

        {
            _connectedBlockchainSegmentIds.putIfAbsent(blockchainSegmentId1, new HashSet<>());
            final HashSet<BlockchainSegmentId> connectedBlockchainSegmentIds = _connectedBlockchainSegmentIds.get(blockchainSegmentId1);
            connectedBlockchainSegmentIds.add(blockchainSegmentId0);
        }
    }

    public void setTimestamp(final BlockId blockId, final Long timestamp) {
        _blockTimestamps.put(blockId, timestamp);
    }

    public void setMedianTimePast(final BlockId blockId, final MutableMedianBlockTime medianBlockTime) {
        _medianTimesPast.put(blockId, medianBlockTime);
    }

    public void setChainWork(final BlockId blockId, final ChainWork chainWork) {
        _chainWorks.put(blockId, chainWork);
    }

    @Override
    public Sha256Hash getHeadBlockHeaderHash() throws DatabaseException {
        for (final Map.Entry<Sha256Hash, BlockId> entry : _blockIds.entrySet()) {
            if (Util.areEqual(_headBlockId, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public BlockId getHeadBlockHeaderId() throws DatabaseException {
        return _headBlockId;
    }

    @Override
    public BlockId getBlockHeaderId(final Sha256Hash blockHash) throws DatabaseException {
        return _blockIds.get(blockHash);
    }

    @Override
    public BlockHeader getBlockHeader(final BlockId blockId) throws DatabaseException {
        return _blockHeaders.get(blockId);
    }

    @Override
    public Boolean blockHeaderExists(final Sha256Hash blockHash) throws DatabaseException {
        final BlockId blockId = _blockIds.get(blockHash);
        if (blockId == null) { return false; }
        return _blockHeaders.containsKey(blockId);
    }

    @Override
    public Integer getBlockDirectDescendantCount(final BlockId blockId) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBlockchainSegmentId(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        _blockchainSegmentIds.put(blockId, blockchainSegmentId);
    }

    @Override
    public BlockchainSegmentId getBlockchainSegmentId(final BlockId blockId) throws DatabaseException {
        return _blockchainSegmentIds.get(blockId);
    }

    @Override
    public Long getBlockHeight(final BlockId blockId) throws DatabaseException {
        return _blockHeights.get(blockId);
    }

    @Override
    public Long getBlockTimestamp(final BlockId blockId) throws DatabaseException {
        return _blockTimestamps.get(blockId);
    }

    @Override
    public BlockId getChildBlockId(final BlockchainSegmentId blockchainSegmentId, final BlockId previousBlockId) throws DatabaseException {
        final Long blockHeight = _blockHeights.get(previousBlockId);

        for (final Map.Entry<BlockId, Long> entry : _blockHeights.entrySet()) {
            final BlockId entryBlockId = entry.getKey();
            final Long entryBlockHeight = entry.getValue();
            if (Util.areEqual(entryBlockHeight, (blockHeight + 1L))) {
                final BlockchainSegmentId entryBlockchainSegmentId = _blockchainSegmentIds.get(entryBlockId);
                if (Util.areEqual(entryBlockchainSegmentId, blockchainSegmentId)) { return entryBlockId; }

                final HashSet<BlockchainSegmentId> connectedBlockchainSegmentIds = _connectedBlockchainSegmentIds.get(entryBlockchainSegmentId);
                if ( (connectedBlockchainSegmentIds != null) && connectedBlockchainSegmentIds.contains(blockchainSegmentId) ) { return entryBlockId; }
            }
        }

        Logger.warn("MOCK: Undefined child BlockId for BlockId: " + previousBlockId, new Exception());

        return null;
    }

    @Override
    public Boolean hasChildBlock(final BlockId blockId) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isBlockConnectedToChain(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId, final BlockRelationship blockRelationship) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Sha256Hash getBlockHash(final BlockId blockId) throws DatabaseException {
        for (final Map.Entry<Sha256Hash, BlockId> entry : _blockIds.entrySet()) {
            if (Util.areEqual(blockId, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public List<Sha256Hash> getBlockHashes(final List<BlockId> blockIds) throws DatabaseException {
        final MutableList<Sha256Hash> blockHashes = new MutableList<>();
        for (final BlockId blockId : blockIds) {
            blockHashes.add(this.getBlockHash(blockId));
        }
        return blockHashes;
    }

    @Override
    public BlockId getAncestorBlockId(final BlockId blockId, final Integer parentCount) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public MutableMedianBlockTime calculateMedianBlockHeaderTime() throws DatabaseException {
        return _medianBlockTime;
    }

    @Override
    public MutableMedianBlockTime calculateMedianBlockTime(final BlockId blockId) throws DatabaseException {
        return _medianBlockTimes.get(blockId);
    }

    @Override
    public ChainWork getChainWork(final BlockId blockId) throws DatabaseException {
        return _chainWorks.get(blockId);
    }

    @Override
    public BlockId getBlockIdAtHeight(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight) throws DatabaseException {
        final MutableList<BlockId> blockIds = new MutableList<>();
        for (final Map.Entry<BlockId, Long> entry : _blockHeights.entrySet()) {
            if (Util.areEqual(blockHeight, entry.getValue())) {
                blockIds.add(entry.getKey());
            }
        }

        for (final BlockId blockId : blockIds) {
            final BlockchainSegmentId blockBlockchainSegmentId = _blockchainSegmentIds.get(blockId);
            if (Util.areEqual(blockBlockchainSegmentId, blockBlockchainSegmentId)) {
                return blockId;
            }

            final HashSet<BlockchainSegmentId> connectedBlockchainSegments = _connectedBlockchainSegmentIds.get(blockBlockchainSegmentId);
            if ( (connectedBlockchainSegments != null) && connectedBlockchainSegments.contains(blockBlockchainSegmentId) ) {
                return blockId;
            }
        }

        Logger.warn("MOCK: Undefined blockId for Height: " + blockHeight, new Exception());

        return null;
    }

    @Override
    public Map<BlockId, Long> getBlockHeights(final List<BlockId> blockIds) throws DatabaseException {
        return FakeBlockHeaderDatabaseManager.super.getBlockHeights(blockIds);
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final BlockId blockId) throws DatabaseException {
        return _medianBlockTimes.get(blockId);
    }

    @Override
    public MedianBlockTime getMedianTimePast(final BlockId blockId) throws DatabaseException {
        return _medianTimesPast.get(blockId);
    }

    @Override
    public Boolean isBlockInvalid(final Sha256Hash blockHash, final Integer maxFailedProcessedCount) throws DatabaseException {
        final Integer invalidCount = Util.coalesce(_invalidBlockCounts.get(blockHash));
        return (invalidCount >= maxFailedProcessedCount);
    }

    @Override
    public void markBlockAsInvalid(final Sha256Hash blockHash, final Integer processIncrement) throws DatabaseException {
        final Integer invalidCount = Util.coalesce(_invalidBlockCounts.get(blockHash));
        _invalidBlockCounts.put(blockHash, invalidCount + processIncrement);
    }

    @Override
    public void clearBlockAsInvalid(final Sha256Hash blockHash, final Integer processDecrement) throws DatabaseException {
        _invalidBlockCounts.remove(blockHash);
    }
}
