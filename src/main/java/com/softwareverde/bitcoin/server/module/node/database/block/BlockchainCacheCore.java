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
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BlockchainCacheCore implements BlockchainCache {
    public interface MapFactory {
        <Key, Value> Map<Key, Value> newMap();
        <Key, Value> Map<Key, Value> newMap(Integer itemCount);
        <Value> List<Value> newList();
        <Value> List<Value> newList(Value... values);
    }

    protected <Value> Value _getListValue(final List<Value> list) {
        final int version = _version.get();
        if (version >= list.getCount()) { return null; }
        return list.get(version);
    }

    protected final ReentrantReadWriteLock.ReadLock _readLock;
    protected final ReentrantReadWriteLock.WriteLock _writeLock;

    protected final AtomicInteger _version = new AtomicInteger(0);

    protected final Map<BlockId, BlockHeader> _blockHeaders;
    protected final Map<BlockId, Long> _blockHeights;
    protected final Map<Sha256Hash, BlockId> _blockIds;
    protected final Map<Long, MutableList<BlockId>> _blocksByHeight;
    protected final Map<BlockId, ChainWork> _chainWorks;
    protected final Map<BlockId, MedianBlockTime> _medianBlockTimes;
    protected final Map<BlockId, Boolean> _blockTransactionMap;
    protected final Map<BlockId, Integer> _blockProcessCount;

    protected final Map<BlockId, BlockchainSegmentId> _blockchainSegmentIds;

    protected final List<BlockId> _headBlockId;
    protected final List<BlockId> _headBlockHeaderId;

    protected final Map<BlockchainSegmentId, BlockchainSegmentId> _blockchainSegmentParents; // key=child, value=parent
    protected final Map<BlockchainSegmentId, Long> _blockchainSegmentNestedSetLeft;
    protected final Map<BlockchainSegmentId, Long> _blockchainSegmentNestedSetRight;
    protected final Map<BlockchainSegmentId, Long> _blockchainSegmentMaxBlockHeight;

    protected Boolean _areBlockchainSegmentsConnected(final Long nestedSetLeft0, final Long nestedSetRight0, final BlockchainSegmentId blockchainSegmentId, final BlockRelationship blockRelationship) {
        final Long nestedSetLeft1 = _blockchainSegmentNestedSetLeft.get(blockchainSegmentId);
        final Long nestedSetRight1 = _blockchainSegmentNestedSetRight.get(blockchainSegmentId);

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

    protected BlockchainCacheCore(final Integer estimatedBlockCount, final MapFactory mapFactory) {
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

        _headBlockId = mapFactory.newList();
        _headBlockHeaderId = mapFactory.newList();
    }

    protected BlockchainCacheCore(final ReentrantReadWriteLock.ReadLock readLock, final ReentrantReadWriteLock.WriteLock writeLock, final Map<BlockId, BlockHeader> blockHeaders, final Map<BlockId, Long> blockHeights, final Map<Sha256Hash, BlockId> blockIds, final Map<Long, MutableList<BlockId>> blocksByHeight, final Map<BlockId, ChainWork> chainWorks, final Map<BlockId, MedianBlockTime> medianBlockTimes, final Map<BlockId, Boolean> blockTransactionMap, final Map<BlockId, Integer> blockProcessCount, final Map<BlockId, BlockchainSegmentId> blockchainSegmentIds, final BlockId headBlockId, final BlockId headBlockHeaderId, final Map<BlockchainSegmentId, BlockchainSegmentId> blockchainSegmentParents, final Map<BlockchainSegmentId, Long> blockchainSegmentNestedSetLeft, final Map<BlockchainSegmentId, Long> blockchainSegmentNestedSetRight, final Map<BlockchainSegmentId, Long> blockchainSegmentMaxBlockHeight, final MapFactory mapFactory) {
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
        _blockchainSegmentParents = blockchainSegmentParents;
        _blockchainSegmentNestedSetLeft = blockchainSegmentNestedSetLeft;
        _blockchainSegmentNestedSetRight = blockchainSegmentNestedSetRight;
        _blockchainSegmentMaxBlockHeight = blockchainSegmentMaxBlockHeight;

        _headBlockId = mapFactory.newList(headBlockId);
        _headBlockHeaderId = mapFactory.newList(headBlockHeaderId);
    }

    @Override
    public BlockId getBlockId(final Sha256Hash blockHash) {
        _readLock.lock();
        try {
            return _blockIds.get(blockHash);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockHeader getBlockHeader(final BlockId blockId) {
        _readLock.lock();
        try {
            return _blockHeaders.get(blockId);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockHeader getBlockHeader(final Sha256Hash blockHash) {
        _readLock.lock();
        try {
            final BlockId blockId = _blockIds.get(blockHash);
            if (blockId == null) { return null; }
            return _blockHeaders.get(blockId);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public Long getBlockHeight(final BlockId blockId) {
        _readLock.lock();
        try {
            return _blockHeights.get(blockId);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public ChainWork getChainWork(final BlockId blockId) {
        _readLock.lock();
        try {
            return _chainWorks.get(blockId);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final BlockId blockId) {
        _readLock.lock();
        try {
            return _medianBlockTimes.get(blockId);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public Boolean hasTransactions(final BlockId blockId) {
        _readLock.lock();
        try {
            if (Logger.isTraceEnabled()) {
                if (blockId.longValue() == 1L) {
                    Logger.trace("Genesis HasTransactions=" + _blockTransactionMap.get(blockId) + " " + _version.get() + " " + _blockTransactionMap.getKeys().getCount());
                }
            }
            return _blockTransactionMap.get(blockId);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public Integer getProcessCount(final BlockId blockId) {
        _readLock.lock();
        try {
            if (! _blockProcessCount.containsKey(blockId)) { return null; }
            return Util.coalesce(_blockProcessCount.get(blockId));
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockId getBlockHeader(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight) {
        _readLock.lock();
        try {
            final List<BlockId> blockIds = _blocksByHeight.get(blockHeight);
            if (blockIds == null) { return null; }

            // Attempt to match exactly...
            for (final BlockId blockId : blockIds) {
                final BlockchainSegmentId exactMatchBlockchainSegmentId = _blockchainSegmentIds.get(blockId);
                if (Util.areEqual(blockchainSegmentId, exactMatchBlockchainSegmentId)) {
                    return blockId;
                }
            }

            { // Attempt to find a matching blockchainSegment parent... (any nestedSetLeft that is greater than the blockchainSegmentId's nestedSetLeft)
                final Long maxNestedSetLeft = _blockchainSegmentNestedSetLeft.get(blockchainSegmentId);
                final MutableList<BlockchainSegmentId> parentBlockchainSegmentIds = new MutableList<>();
                _blockchainSegmentNestedSetLeft.visit(new Map.Visitor<BlockchainSegmentId, Long>() {
                    @Override
                    public boolean run(final Tuple<BlockchainSegmentId, Long> nestedSetLeft) {
                        if (nestedSetLeft.second <= maxNestedSetLeft) {
                            parentBlockchainSegmentIds.add(nestedSetLeft.first);
                        }
                        return true;
                    }
                });

                for (final BlockId blockId : blockIds) {
                    final BlockchainSegmentId blockBlockchainSegmentId = _blockchainSegmentIds.get(blockId);
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
            return _getListValue(_headBlockHeaderId);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockId getHeadBlockId() {
        _readLock.lock();
        try {
            return _getListValue(_headBlockId);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockchainSegmentId getHeadBlockchainSegmentId() {
        _readLock.lock();
        try {
            final BlockId blockId = _getListValue(_headBlockHeaderId);
            if (blockId == null) { return null; }
            return _blockchainSegmentIds.get(blockId);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockchainSegmentId getRootBlockchainSegmentId() {
        _readLock.lock();
        try {
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
            });
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
            return _blockchainSegmentIds.get(blockId);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public List<BlockId> getChildBlockIds(final BlockId blockId) {
        _readLock.lock();
        try {
            final MutableList<BlockId> blockIds = new MutableList<>();
            final Long blockHeight = _blockHeights.get(blockId);
            if (blockHeight == null) { return null; }

            final BlockHeader blockHeader = _blockHeaders.get(blockId);
            final Sha256Hash blockHash = blockHeader.getHash();

            final List<BlockId> blocksAtChildHeight = _blocksByHeight.get((blockHeight + 1L));
            if (blocksAtChildHeight != null) {
                for (final BlockId childBlockId : blocksAtChildHeight) {
                    final BlockHeader childBlockHeader = _blockHeaders.get(childBlockId);
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
            });
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
            final BlockchainSegmentId parentBlockchainSegmentId = _blockchainSegmentParents.get(blockchainSegmentId);
            final Long nestedSetLeft = _blockchainSegmentNestedSetLeft.get(blockchainSegmentId);
            final Long nestedSetRight = _blockchainSegmentNestedSetRight.get(blockchainSegmentId);
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

            final Long nestedSetLeft0 = _blockchainSegmentNestedSetLeft.get(blockchainSegmentId0);
            final Long nestedSetRight0 = _blockchainSegmentNestedSetRight.get(blockchainSegmentId0);

            return _areBlockchainSegmentsConnected(nestedSetLeft0, nestedSetRight0, blockchainSegmentId1, blockRelationship);
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

            final Long nestedSetLeft0 = _blockchainSegmentNestedSetLeft.get(blockchainSegmentId0);
            final Long nestedSetRight0 = _blockchainSegmentNestedSetRight.get(blockchainSegmentId0);

            for (final BlockchainSegmentId blockchainSegmentId : blockchainSegmentIds) {
                if (blockchainSegmentId == null) {
                    areConnected.put(blockchainSegmentId, false);
                    continue;
                }

                final Boolean isConnected = _areBlockchainSegmentsConnected(nestedSetLeft0, nestedSetRight0, blockchainSegmentId, blockRelationship);
                areConnected.put(blockchainSegmentId, isConnected);
            }
            return areConnected;
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockId getHeadBlockIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId, final Boolean hasTransaction) {
        _readLock.lock();
        try {
            { // OPTIMIZATION: Check if the cached HeadBlock/Header values are on the requested BlockchainSegmentId...
                if (hasTransaction) {
                    final BlockId headBlockId = _getListValue(_headBlockId);

                    final BlockchainSegmentId headBlockBlockchainSegment = _blockchainSegmentIds.get(headBlockId);
                    if (Util.areEqual(blockchainSegmentId, headBlockBlockchainSegment)) {
                        return headBlockId;
                    }
                }
                else {
                    final BlockId headBlockHeaderId = _getListValue(_headBlockHeaderId);

                    final BlockchainSegmentId headBlockHeaderBlockchainSegment = _blockchainSegmentIds.get(headBlockHeaderId);
                    if (Util.areEqual(blockchainSegmentId, headBlockHeaderBlockchainSegment)) {
                        return headBlockHeaderId;
                    }
                }
            }

            final Long blockHeight = _blockchainSegmentMaxBlockHeight.get(blockchainSegmentId);
            if (blockHeight == null) { return null; }

            final List<BlockId> blockIds = _blocksByHeight.get(blockHeight);
            if (blockIds == null) { return null; }
            if (blockIds.isEmpty()) { return null; }

            for (final BlockId blockId : blockIds) {
                final BlockchainSegmentId blockBlockchainSegmentId = _blockchainSegmentIds.get(blockId);
                if (Util.areEqual(blockchainSegmentId, blockBlockchainSegmentId)) {
                    if (hasTransaction) {
                        if (! _blockTransactionMap.get(blockId)) { continue; } // NOTE: Not efficient if the head block header is far ahead of head block.
                    }

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
            final BlockchainSegmentId parentBlockchainSegmentId = _blockchainSegmentParents.get(blockchainSegmentId);
            if (parentBlockchainSegmentId == null) { // First block is the genesis block...
                final List<BlockId> blockIds = _blocksByHeight.get(0L);
                if (blockIds == null) { return null; }
                if (blockIds.isEmpty()) { return null; }
                return blockIds.get(0);
            }

            final Long previousSegmentMaxBlockHeight = _blockchainSegmentMaxBlockHeight.get(parentBlockchainSegmentId);
            final Long minBlockHeight = (previousSegmentMaxBlockHeight + 1L);

            final List<BlockId> blockIds = _blocksByHeight.get(minBlockHeight);
            if (blockIds == null) { return null; }
            if (blockIds.isEmpty()) { return null; }

            for (final BlockId blockId : blockIds) {
                final BlockchainSegmentId blockBlockchainSegmentId = _blockchainSegmentIds.get(blockId);
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
            final Tuple<Long, Long> nestedSet = new Tuple<>();
            nestedSet.first = _blockchainSegmentNestedSetLeft.get(blockchainSegmentId);
            nestedSet.second = _blockchainSegmentNestedSetRight.get(blockchainSegmentId);

            final MutableList<BlockchainSegmentId> childrenLeaves = new MutableList<>();
            _blockchainSegmentMaxBlockHeight.visit(new Map.Visitor<BlockchainSegmentId, Long>() {
                @Override
                public boolean run(final Tuple<BlockchainSegmentId, Long> entry) {
                    final Tuple<Long, Long> nestedSet2 = new Tuple<>();
                    nestedSet2.first = _blockchainSegmentNestedSetLeft.get(entry.first);
                    nestedSet2.second = _blockchainSegmentNestedSetRight.get(entry.first);

                    final boolean isLeafNode = (nestedSet2.first == (nestedSet2.second - 1));
                    if (! isLeafNode) { return true; }

                    if (nestedSet2.first <= nestedSet.first) { return true; }
                    if (nestedSet2.second >= nestedSet.second) { return true; }

                    childrenLeaves.add(entry.first);

                    return true;
                }
            });

            BlockId headBlockId = null;
            ChainWork maxChainWork = null;
            for (final BlockchainSegmentId childBlockchainSegmentId : childrenLeaves) {
                final Long blockHeight = _blockchainSegmentMaxBlockHeight.get(childBlockchainSegmentId);
                if (blockHeight == null) { continue; }

                final List<BlockId> blockIds = _blocksByHeight.get(blockHeight);
                if (blockIds == null) { continue; }

                for (final BlockId blockId : blockIds) {
                    final BlockchainSegmentId blockBlockchainSegmentId = _blockchainSegmentIds.get(blockId);
                    if (! Util.areEqual(blockchainSegmentId, blockBlockchainSegmentId)) { continue; }

                    if (maxChainWork == null) {
                        maxChainWork = _chainWorks.get(blockId);
                        headBlockId = blockId;
                    }
                    else {
                        final ChainWork chainWork = _chainWorks.get(blockId);
                        if ( (chainWork != null) && (maxChainWork.compareTo(chainWork) <= 0) ) {
                            maxChainWork = chainWork;
                            headBlockId = blockId;
                        }
                    }
                }
            }

            if (headBlockId == null) { return null; }
            return _blockchainSegmentIds.get(headBlockId);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public List<BlockchainSegmentId> getLeafBlockchainSegmentIds() {
        _readLock.lock();
        try {
            final MutableList<BlockchainSegmentId> childrenLeaves = new MutableList<>();
            _blockchainSegmentMaxBlockHeight.visit(new Map.Visitor<BlockchainSegmentId, Long>() {
                @Override
                public boolean run(final Tuple<BlockchainSegmentId, Long> entry) {
                    final Tuple<Long, Long> nestedSet2 = new Tuple<>();
                    nestedSet2.first = _blockchainSegmentNestedSetLeft.get(entry.first);
                    nestedSet2.second = _blockchainSegmentNestedSetRight.get(entry.first);

                    final boolean isLeafNode = (nestedSet2.first == (nestedSet2.second - 1));
                    if (!isLeafNode) { return true; }

                    childrenLeaves.add(entry.first);
                    return true;
                }
            });
            return childrenLeaves;
        }
        finally {
            _readLock.unlock();
        }
    }
}
