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

public class MutableBlockchainCache implements BlockchainCache {
    protected final ReentrantReadWriteLock.ReadLock _readLock;
    protected final ReentrantReadWriteLock.WriteLock _writeLock;

    protected final Integer _initializationSize;
    protected final MutableHashMap<BlockId, BlockHeader> _blockHeaders;
    protected final MutableHashMap<BlockId, Long> _blockHeights;
    protected final MutableHashMap<Sha256Hash, BlockId> _blockIds;
    protected final MutableHashMap<Long, MutableList<BlockId>> _blocksByHeight;
    protected final MutableHashMap<BlockId, ChainWork> _chainWorks;
    protected final MutableHashMap<BlockId, MedianBlockTime> _medianBlockTimes;
    protected final MutableHashMap<BlockId, Boolean> _blockTransactionMap;
    protected final MutableHashMap<BlockId, Integer> _blockProcessCount;

    protected final MutableHashMap<BlockId, BlockchainSegmentId> _blockchainSegmentIds;

    protected BlockId _headBlockId = null;
    protected BlockId _headBlockHeaderId = null;

    protected final MutableHashMap<BlockchainSegmentId, BlockchainSegmentId> _blockchainSegmentParents = new MutableHashMap<>(); // key=child, value=parent
    protected final MutableHashMap<BlockchainSegmentId, Long> _blockchainSegmentNestedSetLeft = new MutableHashMap<>();
    protected final MutableHashMap<BlockchainSegmentId, Long> _blockchainSegmentNestedSetRight = new MutableHashMap<>();
    protected final MutableHashMap<BlockchainSegmentId, Long> _blockchainSegmentMaxBlockHeight = new MutableHashMap<>();

    public MutableBlockchainCache(final Integer estimatedBlockCount) {
        _initializationSize = estimatedBlockCount;

        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _readLock = readWriteLock.readLock();
        _writeLock = readWriteLock.writeLock();

        _blockHeaders = new MutableHashMap<>(estimatedBlockCount);
        _blockHeights = new MutableHashMap<>(estimatedBlockCount);
        _blockIds = new MutableHashMap<>(estimatedBlockCount);
        _blocksByHeight = new MutableHashMap<>(estimatedBlockCount);
        _chainWorks = new MutableHashMap<>(estimatedBlockCount);
        _medianBlockTimes = new MutableHashMap<>(estimatedBlockCount);
        _blockTransactionMap = new MutableHashMap<>(estimatedBlockCount);
        _blockchainSegmentIds = new MutableHashMap<>(estimatedBlockCount);
        _blockProcessCount = new MutableHashMap<>(estimatedBlockCount);
    }

    /**
     * Iterates through all blocks, finding any block matching both blockchainSegmentId and a height GTE minBlockHeight, reassigning it to belong to newBlockchainSegmentId.
     */
    public void updateBlockchainSegmentBlocks(final BlockchainSegmentId newBlockchainSegmentId, final BlockchainSegmentId blockchainSegmentId, final Long minBlockHeight) {
        _writeLock.lock();
        try {
            _blockchainSegmentIds.visit(new Map.Visitor<BlockId, BlockchainSegmentId>() {
                @Override
                public boolean run(final Tuple<BlockId, BlockchainSegmentId> entry) {
                    if (Util.areEqual(blockchainSegmentId, entry.second)) {
                        final BlockId blockId = entry.first;
                        final Long blockBlockHeight = _blockHeights.get(blockId);
                        if (blockBlockHeight >= minBlockHeight) {
                            entry.second = newBlockchainSegmentId;
                        }
                    }
                    return true;
                }
            });
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void replaceBlockchainSegmentsParent(final BlockchainSegmentId parentBlockchainSegmentId, final BlockchainSegmentId newParentBlockchainSegmentId) {
        _writeLock.lock();
        try {
            _blockchainSegmentParents.visit(new Map.Visitor<BlockchainSegmentId, BlockchainSegmentId>() {
                @Override
                public boolean run(final Tuple<BlockchainSegmentId, BlockchainSegmentId> entry) {
                    final BlockchainSegmentId blockchainSegmentId = entry.first;
                    final BlockchainSegmentId currentParentBlockchainSegmentId = entry.second;
                    if (Util.areEqual(parentBlockchainSegmentId, currentParentBlockchainSegmentId)) {
                        if (!Util.areEqual(blockchainSegmentId, newParentBlockchainSegmentId)) { // A BlockchainSegment cannot be its own parent.
                            entry.second = newParentBlockchainSegmentId;
                        }
                    }
                    return true;
                }
            });
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void updateBlockchainSegmentNestedSetLeft(final BlockchainSegmentId blockchainSegmentId, final Long value) {
        _writeLock.lock();
        try {
            _blockchainSegmentNestedSetLeft.put(blockchainSegmentId, value);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void updateBlockchainSegmentNestedSetRight(final BlockchainSegmentId blockchainSegmentId, final Long value) {
        _writeLock.lock();
        try {
            _blockchainSegmentNestedSetRight.put(blockchainSegmentId, value);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void clearBlockchainSegments() {
        _writeLock.lock();
        try {
            _blockchainSegmentParents.clear();
            _blockchainSegmentNestedSetLeft.clear();
            _blockchainSegmentNestedSetRight.clear();
            _blockchainSegmentIds.clear();
            _blockchainSegmentMaxBlockHeight.clear();
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void clearBlocks() {
        _writeLock.lock();
        try {
            _blockHeaders.clear();
            _blockHeights.clear();
            _chainWorks.clear();
            _blockIds.clear();
            _medianBlockTimes.clear();
            _blocksByHeight.clear();
            _blockTransactionMap.clear();
            _blockProcessCount.clear();

            _headBlockHeaderId = null;
            _headBlockId = null;
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void addBlockchainSegment(final BlockchainSegmentId blockchainSegmentId, final BlockchainSegmentId parentBlockchainSegmentId, final Long nestedSetLeft, final Long nestedSetRight) {
        _writeLock.lock();
        try {
            _blockchainSegmentParents.put(blockchainSegmentId, parentBlockchainSegmentId);
            _blockchainSegmentNestedSetLeft.put(blockchainSegmentId, nestedSetLeft);
            _blockchainSegmentNestedSetRight.put(blockchainSegmentId, nestedSetRight);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void setBlockBlockchainSegment(final BlockchainSegmentId blockchainSegmentId, final BlockId blockId) {
        _writeLock.lock();
        try {
            _blockchainSegmentIds.put(blockId, blockchainSegmentId);

            final Long blockHeight = _blockHeights.get(blockId);
            if (blockHeight != null) {
                final Long maxBlockHeight = Util.coalesce(_blockchainSegmentMaxBlockHeight.get(blockchainSegmentId));
                if (maxBlockHeight < blockHeight) {
                    _blockchainSegmentMaxBlockHeight.put(blockchainSegmentId, blockHeight);
                }
            }
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void addBlock(final BlockId blockId, final BlockHeader blockHeader, final Long blockHeight, final ChainWork chainWork, final MedianBlockTime medianBlockTime, final Boolean hasTransactions) {
        _writeLock.lock();
        try {
            final BlockHeader constBlockHeader = blockHeader.asConst();
            final Sha256Hash blockHash = constBlockHeader.getHash(); // NOTE: Also forces internal BlockHeader hash caching...

            _blockHeaders.put(blockId, constBlockHeader);
            _blockHeights.put(blockId, blockHeight);
            _chainWorks.put(blockId, chainWork.asConst());
            _medianBlockTimes.put(blockId, medianBlockTime.asConst());
            _blockIds.put(blockHash, blockId);
            _blockTransactionMap.put(blockId, hasTransactions);
            _blockProcessCount.put(blockId, null);

            MutableList<BlockId> blockIds = _blocksByHeight.get(blockHeight);
            if (blockIds == null) {
                blockIds = new MutableList<>(1);
                _blocksByHeight.put(blockHeight, blockIds);
            }
            blockIds.add(blockId);

            final BlockchainSegmentId blockchainSegmentId = _blockchainSegmentIds.get(blockId);
            if (blockchainSegmentId != null) {
                final Long maxBlockHeight = Util.coalesce(_blockchainSegmentMaxBlockHeight.get(blockchainSegmentId));
                if (maxBlockHeight < blockHeight) {
                    _blockchainSegmentMaxBlockHeight.put(blockchainSegmentId, blockHeight);
                }
            }

            if (_headBlockHeaderId == null) {
                _headBlockHeaderId = blockId;
            }
            else {
                final ChainWork headChainWork = _chainWorks.get(_headBlockHeaderId);
                if (chainWork.compareTo(headChainWork) >= 0) {
                    _headBlockHeaderId = blockId;
                }
            }

            if (hasTransactions) {
                if (_headBlockId == null) {
                    _headBlockId = blockId;
                }
                else {
                    final ChainWork headChainWork = _chainWorks.get(_headBlockId);
                    if (chainWork.compareTo(headChainWork) >= 0) {
                        _headBlockId = blockId;
                    }
                }
            }
        }
        finally {
            _writeLock.unlock();
        }
    }

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

    public void setHasTransactions(final BlockId blockId) {
        _writeLock.lock();
        try {
            _blockTransactionMap.put(blockId, true);

            if (_headBlockId == null) {
                _headBlockId = blockId;
            }
            else {
                final ChainWork headChainWork = _chainWorks.get(_headBlockId);
                final ChainWork blockChainWork = _chainWorks.get(blockId);
                if (blockChainWork.compareTo(headChainWork) >= 0) {
                    _headBlockId = blockId;
                }
            }
        }
        finally {
            _writeLock.unlock();
        }
    }

    /**
     * Sets all blocks below the provided blockHeight as processed.
     *  This function is only intended to be used only for post-Utxo Commitment import.
     */
    public void setHasTransactionsForBlocksUpToHeight(final Long blockHeight) {
        _writeLock.lock();
        try {
            _blocksByHeight.visit(new Map.Visitor<Long, MutableList<BlockId>>() {
                @Override
                public boolean run(final Tuple<Long, MutableList<BlockId>> entry) {
                    if (entry.first <= blockHeight) {
                        for (final BlockId blockId : entry.second) {
                            _blockTransactionMap.put(blockId, true);
                        }
                    }
                    return true;
                }
            });
        }
        finally {
            _writeLock.unlock();
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
            final BlockId blockId = _headBlockHeaderId;
            if (blockId == null) { return null; }
            return _blockchainSegmentIds.get(blockId);

            // final MutableList<BlockchainSegmentId> childrenLeaves = new MutableList<>();
            // for (final BlockchainSegmentId tmpBlockchainSegmentId : _blockchainSegmentMaxBlockHeight.keySet()) {
            //     final Tuple<Long, Long> nestedSet2 = new Tuple<>();
            //     nestedSet2.first = _blockchainSegmentNestedSetLeft.get(tmpBlockchainSegmentId);
            //     nestedSet2.second = _blockchainSegmentNestedSetRight.get(tmpBlockchainSegmentId);

            //     final boolean isLeafNode = (nestedSet2.first == (nestedSet2.second - 1));
            //     if (! isLeafNode) { continue; }

            //     childrenLeaves.add(tmpBlockchainSegmentId);
            // }

            // BlockId headBlockId = null;
            // ChainWork maxChainWork = null;
            // for (final BlockchainSegmentId childBlockchainSegmentId : childrenLeaves) {
            //     final Long blockHeight = _blockchainSegmentMaxBlockHeight.get(childBlockchainSegmentId);
            //     if (blockHeight == null) { continue; }

            //     final List<BlockId> blockIds = _blocksByHeight.get(blockHeight);
            //     if (blockIds == null) { continue; }

            //     for (final BlockId blockId : blockIds) {
            //         if (maxChainWork == null) {
            //             maxChainWork = _chainWorks.get(blockId);
            //             headBlockId = blockId;
            //         }
            //         else {
            //             final ChainWork chainWork = _chainWorks.get(blockId);
            //             if ( (chainWork != null) && (maxChainWork.compareTo(chainWork) <= 0) ) {
            //                 maxChainWork = chainWork;
            //                 headBlockId = blockId;
            //             }
            //         }
            //     }
            // }

            // if (headBlockId == null) { return null; }
            // return _blockchainSegmentIds.get(headBlockId);
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

            final List<BlockId> blocksAtChildHeight = _blocksByHeight.get(blockHeight + 1L);
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
            final Long nestedSetLeft0 = _blockchainSegmentNestedSetLeft.get(blockchainSegmentId0);
            final Long nestedSetRight0 = _blockchainSegmentNestedSetRight.get(blockchainSegmentId0);

            final java.util.Map<BlockchainSegmentId, Boolean> areConnected = new java.util.HashMap<>();
            for (final BlockchainSegmentId blockchainSegmentId : blockchainSegmentIds) {
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
    public BlockId getHeadBlockIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) {
        _readLock.lock();
        try {
            final Long blockHeight = _blockchainSegmentMaxBlockHeight.get(blockchainSegmentId);
            if (blockHeight == null) { return null; }

            final List<BlockId> blockIds = _blocksByHeight.get(blockHeight);
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

    public void pushVersion() {
        _readLock.lock();
        try {
            _blockHeaders.pushVersion();
            _blockHeights.pushVersion();
            _blockIds.pushVersion();
            _chainWorks.pushVersion();
            _medianBlockTimes.pushVersion();
            _blocksByHeight.pushVersion();
            _blockTransactionMap.pushVersion();
            _blockProcessCount.pushVersion();
            _blockchainSegmentIds.pushVersion();
            _blockchainSegmentParents.pushVersion();
            _blockchainSegmentNestedSetLeft.pushVersion();
            _blockchainSegmentNestedSetRight.pushVersion();
            _blockchainSegmentMaxBlockHeight.pushVersion();
        }
        finally {
            _readLock.unlock();
        }
    }

    public void popVersion() {
        _readLock.lock();
        try {
            _blockHeaders.popVersion();
            _blockHeights.popVersion();
            _blockIds.popVersion();
            _chainWorks.popVersion();
            _medianBlockTimes.popVersion();
            _blocksByHeight.popVersion();
            _blockTransactionMap.popVersion();
            _blockProcessCount.popVersion();
            _blockchainSegmentIds.popVersion();
            _blockchainSegmentParents.popVersion();
            _blockchainSegmentNestedSetLeft.popVersion();
            _blockchainSegmentNestedSetRight.popVersion();
            _blockchainSegmentMaxBlockHeight.popVersion();
        }
        finally {
            _readLock.unlock();
        }
    }

    public void applyVersion() {
        _writeLock.lock();
        try {
            _blockHeaders.applyVersion();
            _blockHeights.applyVersion();
            _blockIds.applyVersion();
            _chainWorks.applyVersion();
            _medianBlockTimes.applyVersion();
            _blocksByHeight.applyVersion();
            _blockTransactionMap.applyVersion();
            _blockProcessCount.applyVersion();
            _blockchainSegmentIds.applyVersion();
            _blockchainSegmentParents.applyVersion();
            _blockchainSegmentNestedSetLeft.applyVersion();
            _blockchainSegmentNestedSetRight.applyVersion();
            _blockchainSegmentMaxBlockHeight.applyVersion();
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void incrementProcessCount(final BlockId blockId, final Integer i) {
        _writeLock.lock();
        try {
            final Integer processCount = Util.coalesce(_blockProcessCount.get(blockId));
            final int newProcessCount = Math.max(0, (processCount + i));
            _blockProcessCount.put(blockId, newProcessCount);
        }
        finally {
            _writeLock.unlock();
        }
    }
}
