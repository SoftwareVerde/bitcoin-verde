package com.softwareverde.bitcoin.server.module.node.database.block;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MutableBlockchainCache extends BlockchainCacheCore {
    protected <Key, Value> MutableHashMap<Key, Value> _castMap(final Map<Key, Value> map) {
        return (MutableHashMap<Key, Value>) map;
    }

    protected MutableBlockchainCache(final ReentrantReadWriteLock.ReadLock readLock, final ReentrantReadWriteLock.WriteLock writeLock, final Map<BlockId, BlockHeader> blockHeaders, final Map<BlockId, Long> blockHeights, final Map<Sha256Hash, BlockId> blockIds, final Map<Long, MutableList<BlockId>> blocksByHeight, final Map<BlockId, ChainWork> chainWorks, final Map<BlockId, MedianBlockTime> medianBlockTimes, final Map<BlockId, Boolean> blockTransactionMap, final Map<BlockId, Integer> blockProcessCount, final Map<BlockId, BlockchainSegmentId> blockchainSegmentIds, final BlockId headBlockId, final BlockId headBlockHeaderId, final Map<BlockchainSegmentId, BlockchainSegmentId> blockchainSegmentParents, final Map<BlockchainSegmentId, Long> blockchainSegmentNestedSetLeft, final Map<BlockchainSegmentId, Long> blockchainSegmentNestedSetRight, final Map<BlockchainSegmentId, Long> blockchainSegmentMaxBlockHeight) {
        super(readLock, writeLock, blockHeaders, blockHeights, blockIds, blocksByHeight, chainWorks, medianBlockTimes, blockTransactionMap, blockProcessCount, blockchainSegmentIds, headBlockId, headBlockHeaderId, blockchainSegmentParents, blockchainSegmentNestedSetLeft, blockchainSegmentNestedSetRight, blockchainSegmentMaxBlockHeight);
    }

    public MutableBlockchainCache(final Integer estimatedBlockCount) {
        super(estimatedBlockCount, new MapFactory() {
            @Override
            public <Key, Value> VersionedMap<Key, Value> newMap() {
                return new MutableHashMap<>();
            }

            @Override
            public <Key, Value> VersionedMap<Key, Value> newMap(final Integer itemCount) {
                return new MutableHashMap<>(itemCount);
            }
        });
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
            _castMap(_blockchainSegmentNestedSetLeft).put(blockchainSegmentId, value);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void updateBlockchainSegmentNestedSetRight(final BlockchainSegmentId blockchainSegmentId, final Long value) {
        _writeLock.lock();
        try {
            _castMap(_blockchainSegmentNestedSetRight).put(blockchainSegmentId, value);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void addBlockchainSegment(final BlockchainSegmentId blockchainSegmentId, final BlockchainSegmentId parentBlockchainSegmentId, final Long nestedSetLeft, final Long nestedSetRight) {
        _writeLock.lock();
        try {
            _castMap(_blockchainSegmentParents).put(blockchainSegmentId, parentBlockchainSegmentId);
            _castMap(_blockchainSegmentNestedSetLeft).put(blockchainSegmentId, nestedSetLeft);
            _castMap(_blockchainSegmentNestedSetRight).put(blockchainSegmentId, nestedSetRight);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void setBlockBlockchainSegment(final BlockchainSegmentId blockchainSegmentId, final BlockId blockId) {
        _writeLock.lock();
        try {
            _castMap(_blockchainSegmentIds).put(blockId, blockchainSegmentId);

            final Long blockHeight = _blockHeights.get(blockId);
            if (blockHeight != null) {
                final Long maxBlockHeight = Util.coalesce(_blockchainSegmentMaxBlockHeight.get(blockchainSegmentId));
                if (maxBlockHeight < blockHeight) {
                    _castMap(_blockchainSegmentMaxBlockHeight).put(blockchainSegmentId, blockHeight);
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

            _castMap(_blockHeaders).put(blockId, constBlockHeader);
            _castMap(_blockHeights).put(blockId, blockHeight);
            _castMap(_chainWorks).put(blockId, chainWork.asConst());
            _castMap(_medianBlockTimes).put(blockId, medianBlockTime.asConst());
            _castMap(_blockIds).put(blockHash, blockId);
            _castMap(_blockTransactionMap).put(blockId, hasTransactions);
            _castMap(_blockProcessCount).put(blockId, null);

            MutableList<BlockId> blockIds = _blocksByHeight.get(blockHeight);
            if (blockIds == null) {
                blockIds = new MutableList<>(1);
                _castMap(_blocksByHeight).put(blockHeight, blockIds);
            }
            blockIds.add(blockId);

            final BlockchainSegmentId blockchainSegmentId = _blockchainSegmentIds.get(blockId);
            if (blockchainSegmentId != null) {
                final Long maxBlockHeight = Util.coalesce(_blockchainSegmentMaxBlockHeight.get(blockchainSegmentId));
                if (maxBlockHeight < blockHeight) {
                    _castMap(_blockchainSegmentMaxBlockHeight).put(blockchainSegmentId, blockHeight);
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

    public void setHasTransactions(final BlockId blockId) {
        _writeLock.lock();
        try {
            _castMap(_blockTransactionMap).put(blockId, true);

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
                            _castMap(_blockTransactionMap).put(blockId, true);
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

    public void pushVersion() {
        _writeLock.lock();
        try {
            _castMap(_blockHeaders).pushVersion();
            _castMap(_blockHeights).pushVersion();
            _castMap(_blockIds).pushVersion();
            _castMap(_chainWorks).pushVersion();
            _castMap(_medianBlockTimes).pushVersion();
            _castMap(_blocksByHeight).pushVersion();
            _castMap(_blockTransactionMap).pushVersion();
            _castMap(_blockProcessCount).pushVersion();
            _castMap(_blockchainSegmentIds).pushVersion();
            _castMap(_blockchainSegmentParents).pushVersion();
            _castMap(_blockchainSegmentNestedSetLeft).pushVersion();
            _castMap(_blockchainSegmentNestedSetRight).pushVersion();
            _castMap(_blockchainSegmentMaxBlockHeight).pushVersion();
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void popVersion() {
        _writeLock.lock();
        try {
            _castMap(_blockHeaders).popVersion();
            _castMap(_blockHeights).popVersion();
            _castMap(_blockIds).popVersion();
            _castMap(_chainWorks).popVersion();
            _castMap(_medianBlockTimes).popVersion();
            _castMap(_blocksByHeight).popVersion();
            _castMap(_blockTransactionMap).popVersion();
            _castMap(_blockProcessCount).popVersion();
            _castMap(_blockchainSegmentIds).popVersion();
            _castMap(_blockchainSegmentParents).popVersion();
            _castMap(_blockchainSegmentNestedSetLeft).popVersion();
            _castMap(_blockchainSegmentNestedSetRight).popVersion();
            _castMap(_blockchainSegmentMaxBlockHeight).popVersion();
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void applyVersion() {
        _writeLock.lock();
        try {
            _castMap(_blockHeaders).applyVersion();
            _castMap(_blockHeights).applyVersion();
            _castMap(_blockIds).applyVersion();
            _castMap(_chainWorks).applyVersion();
            _castMap(_medianBlockTimes).applyVersion();
            _castMap(_blocksByHeight).applyVersion();
            _castMap(_blockTransactionMap).applyVersion();
            _castMap(_blockProcessCount).applyVersion();
            _castMap(_blockchainSegmentIds).applyVersion();
            _castMap(_blockchainSegmentParents).applyVersion();
            _castMap(_blockchainSegmentNestedSetLeft).applyVersion();
            _castMap(_blockchainSegmentNestedSetRight).applyVersion();
            _castMap(_blockchainSegmentMaxBlockHeight).applyVersion();
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void applyCache(final MutableBlockchainCache blockchainCache) {
        _writeLock.lock();
        try {
            _castMap(_blockHeaders).applyVersion(_castMap(blockchainCache._blockHeaders));
            _castMap(_blockHeights).applyVersion(_castMap(blockchainCache._blockHeights));
            _castMap(_blockIds).applyVersion(_castMap(blockchainCache._blockIds));
            _castMap(_chainWorks).applyVersion(_castMap(blockchainCache._chainWorks));
            _castMap(_medianBlockTimes).applyVersion(_castMap(blockchainCache._medianBlockTimes));
            _castMap(_blocksByHeight).applyVersion(_castMap(blockchainCache._blocksByHeight));
            _castMap(_blockTransactionMap).applyVersion(_castMap(blockchainCache._blockTransactionMap));
            _castMap(_blockProcessCount).applyVersion(_castMap(blockchainCache._blockProcessCount));
            _castMap(_blockchainSegmentIds).applyVersion(_castMap(blockchainCache._blockchainSegmentIds));
            _castMap(_blockchainSegmentParents).applyVersion(_castMap(blockchainCache._blockchainSegmentParents));
            _castMap(_blockchainSegmentNestedSetLeft).applyVersion(_castMap(blockchainCache._blockchainSegmentNestedSetLeft));
            _castMap(_blockchainSegmentNestedSetRight).applyVersion(_castMap(blockchainCache._blockchainSegmentNestedSetRight));
            _castMap(_blockchainSegmentMaxBlockHeight).applyVersion(_castMap(blockchainCache._blockchainSegmentMaxBlockHeight));
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
            _castMap(_blockProcessCount).put(blockId, newProcessCount);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public MutableBlockchainCache newCopyOnWriteCache() {
        final MutableHashMap<BlockId, BlockHeader> blockHeaders = MutableHashMap.wrap(_castMap(_blockHeaders));
        blockHeaders.pushVersion();

        final MutableHashMap<BlockId, Long> blockHeights = MutableHashMap.wrap(_castMap(_blockHeights));
        blockHeights.pushVersion();

        final MutableHashMap<Sha256Hash, BlockId> blockIds = MutableHashMap.wrap(_castMap(_blockIds));
        blockIds.pushVersion();

        final MutableHashMap<Long, MutableList<BlockId>> blocksByHeight = MutableHashMap.wrap(_castMap(_blocksByHeight));
        blocksByHeight.pushVersion();

        final MutableHashMap<BlockId, ChainWork> chainWorks = MutableHashMap.wrap(_castMap(_chainWorks));
        chainWorks.pushVersion();

        final MutableHashMap<BlockId, MedianBlockTime> medianBlockTimes = MutableHashMap.wrap(_castMap(_medianBlockTimes));
        medianBlockTimes.pushVersion();

        final MutableHashMap<BlockId, Boolean> blockTransactionMap = MutableHashMap.wrap(_castMap(_blockTransactionMap));
        blockTransactionMap.pushVersion();

        final MutableHashMap<BlockId, Integer> blockProcessCount = MutableHashMap.wrap(_castMap(_blockProcessCount));
        blockProcessCount.pushVersion();

        final MutableHashMap<BlockId, BlockchainSegmentId> blockchainSegmentIds = MutableHashMap.wrap(_castMap(_blockchainSegmentIds));
        blockchainSegmentIds.pushVersion();

        final MutableHashMap<BlockchainSegmentId, BlockchainSegmentId> blockchainSegmentParents = MutableHashMap.wrap(_castMap(_blockchainSegmentParents));
        blockchainSegmentParents.pushVersion();

        final MutableHashMap<BlockchainSegmentId, Long> blockchainSegmentNestedSetLeft = MutableHashMap.wrap(_castMap(_blockchainSegmentNestedSetLeft));
        blockchainSegmentNestedSetLeft.pushVersion();

        final MutableHashMap<BlockchainSegmentId, Long> blockchainSegmentNestedSetRight = MutableHashMap.wrap(_castMap(_blockchainSegmentNestedSetRight));
        blockchainSegmentNestedSetRight.pushVersion();

        final MutableHashMap<BlockchainSegmentId, Long> blockchainSegmentMaxBlockHeight = MutableHashMap.wrap(_castMap(_blockchainSegmentMaxBlockHeight));
        blockchainSegmentMaxBlockHeight.pushVersion();

        return new MutableBlockchainCache(_readLock, _writeLock, blockHeaders, blockHeights, blockIds, blocksByHeight, chainWorks, medianBlockTimes, blockTransactionMap, blockProcessCount, blockchainSegmentIds, _headBlockId, _headBlockHeaderId, blockchainSegmentParents, blockchainSegmentNestedSetLeft, blockchainSegmentNestedSetRight, blockchainSegmentMaxBlockHeight);
    }
}
