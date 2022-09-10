package com.softwareverde.bitcoin.server.module.node.database.block;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Container;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class AbstractBlockchainCache implements BlockchainCache {
    public interface MapFactory {
        <Key, Value> VersionedMap<Key, Value> newMap();
        <Key, Value> VersionedMap<Key, Value> newMap(Integer itemCount);
    }

    protected final ReentrantReadWriteLock.ReadLock _readLock;
    protected final ReentrantReadWriteLock.WriteLock _writeLock;

    protected final VersionedMap<BlockId, BlockHeader> _blockHeaders;
    protected final VersionedMap<BlockId, Long> _blockHeights;
    protected final VersionedMap<Sha256Hash, BlockId> _blockIds;
    protected final VersionedMap<Long, MutableList<BlockId>> _blocksByHeight;
    protected final VersionedMap<BlockId, ChainWork> _chainWorks;
    protected final VersionedMap<BlockId, MedianBlockTime> _medianBlockTimes;
    protected final VersionedMap<BlockId, Boolean> _blockTransactionMap;
    protected final VersionedMap<BlockId, Integer> _blockProcessCount;

    protected final VersionedMap<BlockId, BlockchainSegmentId> _blockchainSegmentIds;

    protected BlockId _headBlockId = null;
    protected BlockId _headBlockHeaderId = null;

    protected final VersionedMap<BlockchainSegmentId, BlockchainSegmentId> _blockchainSegmentParents; // key=child, value=parent
    protected final VersionedMap<BlockchainSegmentId, Long> _blockchainSegmentNestedSetLeft;
    protected final VersionedMap<BlockchainSegmentId, Long> _blockchainSegmentNestedSetRight;
    protected final VersionedMap<BlockchainSegmentId, Long> _blockchainSegmentMaxBlockHeight;

    protected abstract Integer _getVersion();

    protected Boolean _areBlockchainSegmentsConnected(final Long nestedSetLeft0, final Long nestedSetRight0, final BlockchainSegmentId blockchainSegmentId, final BlockRelationship blockRelationship, final Integer version) {
        final Long nestedSetLeft1 = _blockchainSegmentNestedSetLeft.get(blockchainSegmentId, version);
        final Long nestedSetRight1 = _blockchainSegmentNestedSetRight.get(blockchainSegmentId, version);

        if (nestedSetLeft0 == null) { throw new NullPointerException("nestedSetLeft0"); }
        if (nestedSetLeft1 == null) { throw new NullPointerException("nestedSetLeft1"); }
        if (nestedSetRight0 == null) { throw new NullPointerException("nestedSetRight0"); }
        if (nestedSetRight1 == null) { throw new NullPointerException("nestedSetRight1"); }

        switch (blockRelationship) {
            case ANCESTOR: {
                // (2:9), (4:5) - True
                // (4:5), (2:9) - False (Descendant)
                // (2:9), (11:16) - False (Unrelated)
                return (nestedSetLeft0 <= nestedSetLeft1) && (nestedSetRight0 >= nestedSetRight1);
            }
            case DESCENDANT: {
                // (4:5), (2:9) - True
                // (2:9), (4:5) - False (Ancestor)
                // (2:9), (11:16) - False (Unrelated)
                return (nestedSetLeft0 >= nestedSetLeft1) && (nestedSetRight0 <= nestedSetRight1);
            }
            default: {
                // (4:5), (2:9) - True
                // (2:9), (4:5) - True (Ancestor)
                // (2:9), (11:16) - False (Unrelated)
                return ((nestedSetLeft0 <= nestedSetLeft1) && (nestedSetRight0 >= nestedSetRight1)) || ((nestedSetLeft0 >= nestedSetLeft1) && (nestedSetRight0 <= nestedSetRight1));
            }
        }
    }

    protected AbstractBlockchainCache(final Integer estimatedBlockCount, final MapFactory mapFactory) {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _readLock = readWriteLock.readLock();
        _writeLock = readWriteLock.writeLock();

        _blockchainSegmentParents = mapFactory.newMap();
        _blockchainSegmentNestedSetLeft = mapFactory.newMap();
        _blockchainSegmentNestedSetRight = mapFactory.newMap();
        _blockchainSegmentMaxBlockHeight = mapFactory.newMap();

        _blockHeaders = mapFactory.newMap(estimatedBlockCount);
        _blockHeights = mapFactory.newMap(estimatedBlockCount);
        _blockIds = mapFactory.newMap(estimatedBlockCount);
        _blocksByHeight = mapFactory.newMap(estimatedBlockCount);
        _chainWorks = mapFactory.newMap(estimatedBlockCount);
        _medianBlockTimes = mapFactory.newMap(estimatedBlockCount);
        _blockTransactionMap = mapFactory.newMap(estimatedBlockCount);
        _blockchainSegmentIds = mapFactory.newMap(estimatedBlockCount);
        _blockProcessCount = mapFactory.newMap(estimatedBlockCount);
    }

    protected AbstractBlockchainCache(final ReentrantReadWriteLock.ReadLock readLock, final ReentrantReadWriteLock.WriteLock writeLock, final VersionedMap<BlockId, BlockHeader> blockHeaders, final VersionedMap<BlockId, Long> blockHeights, final VersionedMap<Sha256Hash, BlockId> blockIds, final VersionedMap<Long, MutableList<BlockId>> blocksByHeight, final VersionedMap<BlockId, ChainWork> chainWorks, final VersionedMap<BlockId, MedianBlockTime> medianBlockTimes, final VersionedMap<BlockId, Boolean> blockTransactionMap, final VersionedMap<BlockId, Integer> blockProcessCount, final VersionedMap<BlockId, BlockchainSegmentId> blockchainSegmentIds, final BlockId headBlockId, final BlockId headBlockHeaderId, final VersionedMap<BlockchainSegmentId, BlockchainSegmentId> blockchainSegmentParents, final VersionedMap<BlockchainSegmentId, Long> blockchainSegmentNestedSetLeft, final VersionedMap<BlockchainSegmentId, Long> blockchainSegmentNestedSetRight, final VersionedMap<BlockchainSegmentId, Long> blockchainSegmentMaxBlockHeight) {
        _readLock = readLock;
        _writeLock = writeLock;
        _blockHeaders = blockHeaders;
        _blockHeights = blockHeights;
        _blockIds = blockIds;
        _blocksByHeight = blocksByHeight;
        _chainWorks = chainWorks;
        _medianBlockTimes = medianBlockTimes;
        _blockTransactionMap = blockTransactionMap;
        _blockProcessCount = blockProcessCount;
        _blockchainSegmentIds = blockchainSegmentIds;
        _headBlockId = headBlockId;
        _headBlockHeaderId = headBlockHeaderId;
        _blockchainSegmentParents = blockchainSegmentParents;
        _blockchainSegmentNestedSetLeft = blockchainSegmentNestedSetLeft;
        _blockchainSegmentNestedSetRight = blockchainSegmentNestedSetRight;
        _blockchainSegmentMaxBlockHeight = blockchainSegmentMaxBlockHeight;
    }

    @Override
    public BlockId getBlockId(final Sha256Hash blockHash) {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            return _blockIds.get(blockHash, version);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockHeader getBlockHeader(final BlockId blockId) {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            return _blockHeaders.get(blockId, version);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockHeader getBlockHeader(final Sha256Hash blockHash) {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            final BlockId blockId = _blockIds.get(blockHash, version);
            if (blockId == null) { return null; }
            return _blockHeaders.get(blockId, version);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public Long getBlockHeight(final BlockId blockId) {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            return _blockHeights.get(blockId, version);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public ChainWork getChainWork(final BlockId blockId) {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            return _chainWorks.get(blockId, version);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final BlockId blockId) {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            return _medianBlockTimes.get(blockId, version);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public Boolean hasTransactions(final BlockId blockId) {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            return _blockTransactionMap.get(blockId, version);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public Integer getProcessCount(final BlockId blockId) {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            if (! _blockProcessCount.containsKey(blockId, version)) { return null; }
            return Util.coalesce(_blockProcessCount.get(blockId, version));
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockId getBlockHeader(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight) {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            final List<BlockId> blockIds = _blocksByHeight.get(blockHeight, version);
            if (blockIds == null) { return null; }

            // Attempt to match exactly...
            for (final BlockId blockId : blockIds) {
                final BlockchainSegmentId exactMatchBlockchainSegmentId = _blockchainSegmentIds.get(blockId, version);
                if (Util.areEqual(blockchainSegmentId, exactMatchBlockchainSegmentId)) {
                    return blockId;
                }
            }

            { // Attempt to find a matching blockchainSegment parent... (any nestedSetLeft that is greater than the blockchainSegmentId's nestedSetLeft)
                final Long maxNestedSetLeft = _blockchainSegmentNestedSetLeft.get(blockchainSegmentId, version);
                final MutableList<BlockchainSegmentId> parentBlockchainSegmentIds = new MutableList<>();
                _blockchainSegmentNestedSetLeft.visit(new Map.Visitor<BlockchainSegmentId, Long>() {
                    @Override
                    public boolean run(final Tuple<BlockchainSegmentId, Long> nestedSetLeft) {
                        if (nestedSetLeft.second <= maxNestedSetLeft) {
                            parentBlockchainSegmentIds.add(nestedSetLeft.first);
                        }
                        return true;
                    }
                }, version);

                for (final BlockId blockId : blockIds) {
                    final BlockchainSegmentId blockBlockchainSegmentId = _blockchainSegmentIds.get(blockId, version);
                    if (parentBlockchainSegmentIds.contains(blockBlockchainSegmentId)) {
                        return blockId;
                    }
                }
            }

            return null;
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockId getHeadBlockHeaderId() {
        _readLock.lock();
        try {
            return _headBlockHeaderId;
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockId getHeadBlockId() {
        _readLock.lock();
        try {
            return _headBlockId;
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockchainSegmentId getHeadBlockchainSegmentId() {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            final BlockId blockId = _headBlockHeaderId;
            if (blockId == null) { return null; }
            return _blockchainSegmentIds.get(blockId, version);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockchainSegmentId getRootBlockchainSegmentId() {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            final Container<BlockchainSegmentId> container = new Container<>();
            _blockchainSegmentParents.visit(new Map.Visitor<BlockchainSegmentId, BlockchainSegmentId>() {
                @Override
                public boolean run(final Tuple<BlockchainSegmentId, BlockchainSegmentId> entry) {
                    if (entry.second == null) {
                        container.value = entry.first;
                        return false;
                    }
                    return true;
                }
            }, version);
            return container.value;
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockchainSegmentId getBlockchainSegmentId(final BlockId blockId) {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            return _blockchainSegmentIds.get(blockId, version);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public List<BlockId> getChildBlockIds(final BlockId blockId) {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            final MutableList<BlockId> blockIds = new MutableList<>();
            final Long blockHeight = _blockHeights.get(blockId, version);
            if (blockHeight == null) { return null; }

            final BlockHeader blockHeader = _blockHeaders.get(blockId, version);
            final Sha256Hash blockHash = blockHeader.getHash();

            final List<BlockId> blocksAtChildHeight = _blocksByHeight.get((blockHeight + 1L), version);
            if (blocksAtChildHeight != null) {
                for (final BlockId childBlockId : blocksAtChildHeight) {
                    final BlockHeader childBlockHeader = _blockHeaders.get(childBlockId, version);
                    final Sha256Hash childParentHash = childBlockHeader.getPreviousBlockHash();
                    if (Util.areEqual(blockHash, childParentHash)) {
                        blockIds.add(childBlockId);
                    }
                }
            }

            return blockIds;
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public List<BlockchainSegmentId> getChildSegmentIds(final BlockchainSegmentId parentBlockchainSegmentId) {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            final MutableList<BlockchainSegmentId> blockchainSegmentIds = new MutableList<>();
            _blockchainSegmentParents.visit(new Map.Visitor<BlockchainSegmentId, BlockchainSegmentId>() {
                @Override
                public boolean run(final Tuple<BlockchainSegmentId, BlockchainSegmentId> entry) {
                    final BlockchainSegmentId blockchainSegmentId = entry.first;
                    final BlockchainSegmentId entryParentBlockchainSegmentId = entry.second;
                    if (Util.areEqual(parentBlockchainSegmentId, entryParentBlockchainSegmentId)) {
                        blockchainSegmentIds.add(blockchainSegmentId);
                    }
                    return true;
                }
            }, version);
            return blockchainSegmentIds;
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockchainSegment getBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            final BlockchainSegmentId parentBlockchainSegmentId = _blockchainSegmentParents.get(blockchainSegmentId, version);
            final Long nestedSetLeft = _blockchainSegmentNestedSetLeft.get(blockchainSegmentId, version);
            final Long nestedSetRight = _blockchainSegmentNestedSetRight.get(blockchainSegmentId, version);
            return new BlockchainSegment(blockchainSegmentId, parentBlockchainSegmentId, nestedSetLeft, nestedSetRight);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public Boolean areBlockchainSegmentsConnected(final BlockchainSegmentId blockchainSegmentId0, final BlockchainSegmentId blockchainSegmentId1, final BlockRelationship blockRelationship) {
        _readLock.lock();
        try {
            if (blockchainSegmentId0 == null) { return false; }
            if (blockchainSegmentId1 == null) { return false; }

            final Integer version = _getVersion();
            final Long nestedSetLeft0 = _blockchainSegmentNestedSetLeft.get(blockchainSegmentId0, version);
            final Long nestedSetRight0 = _blockchainSegmentNestedSetRight.get(blockchainSegmentId0, version);

            if (nestedSetLeft0 == null) {
                throw new NullPointerException("nestedSetLeft0=null; blockchainSegmentId0=" + blockchainSegmentId0 + "; version=" + version + "; class=" + this.getClass());
            }

            return _areBlockchainSegmentsConnected(nestedSetLeft0, nestedSetRight0, blockchainSegmentId1, blockRelationship, version);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public java.util.Map<BlockchainSegmentId, Boolean> areBlockchainSegmentsConnected(final BlockchainSegmentId blockchainSegmentId0, final List<BlockchainSegmentId> blockchainSegmentIds, final BlockRelationship blockRelationship) {
        _readLock.lock();
        try {
            final java.util.Map<BlockchainSegmentId, Boolean> areConnected = new java.util.HashMap<>(blockchainSegmentIds.getCount());
            if (blockchainSegmentId0 == null) { return areConnected; }

            final Integer version = _getVersion();
            final Long nestedSetLeft0 = _blockchainSegmentNestedSetLeft.get(blockchainSegmentId0, version);
            final Long nestedSetRight0 = _blockchainSegmentNestedSetRight.get(blockchainSegmentId0, version);

            for (final BlockchainSegmentId blockchainSegmentId : blockchainSegmentIds) {
                if (blockchainSegmentId == null) {
                    areConnected.put(blockchainSegmentId, false);
                    continue;
                }

                final Boolean isConnected = _areBlockchainSegmentsConnected(nestedSetLeft0, nestedSetRight0, blockchainSegmentId, blockRelationship, version);
                areConnected.put(blockchainSegmentId, isConnected);
            }
            return areConnected;
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockId getHeadBlockIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            final Long blockHeight = _blockchainSegmentMaxBlockHeight.get(blockchainSegmentId, version);
            if (blockHeight == null) { return null; }

            final List<BlockId> blockIds = _blocksByHeight.get(blockHeight, version);
            if (blockIds == null) { return null; }
            if (blockIds.isEmpty()) { return null; }

            for (final BlockId blockId : blockIds) {
                final BlockchainSegmentId blockBlockchainSegmentId = _blockchainSegmentIds.get(blockId, version);
                if (Util.areEqual(blockchainSegmentId, blockBlockchainSegmentId)) {
                    return blockId;
                }
            }
            return null;
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockId getFirstBlockIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            final BlockchainSegmentId parentBlockchainSegmentId = _blockchainSegmentParents.get(blockchainSegmentId, version);
            if (parentBlockchainSegmentId == null) { // First block is the genesis block...
                final List<BlockId> blockIds = _blocksByHeight.get(0L, version);
                if (blockIds == null) { return null; }
                if (blockIds.isEmpty()) { return null; }
                return blockIds.get(0);
            }

            final Long previousSegmentMaxBlockHeight = _blockchainSegmentMaxBlockHeight.get(parentBlockchainSegmentId, version);
            final Long minBlockHeight = (previousSegmentMaxBlockHeight + 1L);

            final List<BlockId> blockIds = _blocksByHeight.get(minBlockHeight, version);
            if (blockIds == null) { return null; }
            if (blockIds.isEmpty()) { return null; }

            for (final BlockId blockId : blockIds) {
                final BlockchainSegmentId blockBlockchainSegmentId = _blockchainSegmentIds.get(blockId, version);
                if (Util.areEqual(blockchainSegmentId, blockBlockchainSegmentId)) {
                    return blockId;
                }
            }
            return null;
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockchainSegmentId getHeadBlockchainSegmentIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            final Tuple<Long, Long> nestedSet = new Tuple<>();
            nestedSet.first = _blockchainSegmentNestedSetLeft.get(blockchainSegmentId, version);
            nestedSet.second = _blockchainSegmentNestedSetRight.get(blockchainSegmentId, version);

            final MutableList<BlockchainSegmentId> childrenLeaves = new MutableList<>();
            _blockchainSegmentMaxBlockHeight.visit(new Map.Visitor<BlockchainSegmentId, Long>() {
                @Override
                public boolean run(final Tuple<BlockchainSegmentId, Long> entry) {
                    final Tuple<Long, Long> nestedSet2 = new Tuple<>();
                    nestedSet2.first = _blockchainSegmentNestedSetLeft.get(entry.first, version);
                    nestedSet2.second = _blockchainSegmentNestedSetRight.get(entry.first, version);

                    final boolean isLeafNode = (nestedSet2.first == (nestedSet2.second - 1));
                    if (! isLeafNode) { return true; }

                    if (nestedSet2.first <= nestedSet.first) { return true; }
                    if (nestedSet2.second >= nestedSet.second) { return true; }

                    childrenLeaves.add(entry.first);

                    return true;
                }
            }, version);

            BlockId headBlockId = null;
            ChainWork maxChainWork = null;
            for (final BlockchainSegmentId childBlockchainSegmentId : childrenLeaves) {
                final Long blockHeight = _blockchainSegmentMaxBlockHeight.get(childBlockchainSegmentId, version);
                if (blockHeight == null) { continue; }

                final List<BlockId> blockIds = _blocksByHeight.get(blockHeight, version);
                if (blockIds == null) { continue; }

                for (final BlockId blockId : blockIds) {
                    final BlockchainSegmentId blockBlockchainSegmentId = _blockchainSegmentIds.get(blockId, version);
                    if (! Util.areEqual(blockchainSegmentId, blockBlockchainSegmentId)) { continue; }

                    if (maxChainWork == null) {
                        maxChainWork = _chainWorks.get(blockId, version);
                        headBlockId = blockId;
                    }
                    else {
                        final ChainWork chainWork = _chainWorks.get(blockId, version);
                        if ( (chainWork != null) && (maxChainWork.compareTo(chainWork) <= 0) ) {
                            maxChainWork = chainWork;
                            headBlockId = blockId;
                        }
                    }
                }
            }

            if (headBlockId == null) { return null; }
            return _blockchainSegmentIds.get(headBlockId, version);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public List<BlockchainSegmentId> getLeafBlockchainSegmentIds() {
        _readLock.lock();
        try {
            final Integer version = _getVersion();
            final MutableList<BlockchainSegmentId> childrenLeaves = new MutableList<>();
            _blockchainSegmentMaxBlockHeight.visit(new Map.Visitor<BlockchainSegmentId, Long>() {
                @Override
                public boolean run(final Tuple<BlockchainSegmentId, Long> entry) {
                    final Tuple<Long, Long> nestedSet2 = new Tuple<>();
                    nestedSet2.first = _blockchainSegmentNestedSetLeft.get(entry.first, version);
                    nestedSet2.second = _blockchainSegmentNestedSetRight.get(entry.first, version);

                    final boolean isLeafNode = (nestedSet2.first == (nestedSet2.second - 1));
                    if (!isLeafNode) { return true; }

                    childrenLeaves.add(entry.first);
                    return true;
                }
            }, version);
            return childrenLeaves;
        }
        finally {
            _readLock.unlock();
        }
    }
}
