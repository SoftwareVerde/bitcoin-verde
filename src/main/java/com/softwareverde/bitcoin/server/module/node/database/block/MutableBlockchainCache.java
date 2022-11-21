package com.softwareverde.bitcoin.server.module.node.database.block;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.map.Map;
import com.softwareverde.util.map.MutableVersionedHashMap;
import com.softwareverde.util.map.VersionedMap;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MutableBlockchainCache extends BlockchainCacheCore {
    public static final MapFactory MAP_FACTORY = new MapFactory() {
        @Override
        public <Key, Value> VersionedMap<Key, Value> newMap() {
            return new MutableVersionedHashMap<>();
        }

        @Override
        public <Key, Value> VersionedMap<Key, Value> newMap(final Integer itemCount) {
            return new MutableVersionedHashMap<>(itemCount);
        }

        @Override
        public <Value> List<Value> newList() {
            return new MutableList<>(0);
        }

        @Override
        public <Value> List<Value> newList(final Value... values) {
            final MutableList<Value> list = new MutableList<>(values.length);
            for (final Value value : values) {
                list.add(value);
            }
            return list;
        }
    };

    protected <Key, Value> MutableVersionedHashMap<Key, Value> _castMap(final Map<Key, Value> map) {
        return (MutableVersionedHashMap<Key, Value>) map;
    }

    protected <Value> void _setListValue(final Value value, final List<Value> list) {
        int itemCount = list.getCount();
        final MutableList<Value> mutableList = ((MutableList<Value>) list);

        while (itemCount > _version.get()) {
            mutableList.remove(itemCount - 1);
            itemCount -= 1;
        }
        mutableList.add(value);
    }

    protected MutableBlockchainCache(final ReentrantReadWriteLock.ReadLock readLock, final ReentrantReadWriteLock.WriteLock writeLock, final Map<BlockId, BlockHeader> blockHeaders, final Map<BlockId, Long> blockHeights, final Map<Sha256Hash, BlockId> blockIds, final Map<Long, MutableList<BlockId>> blocksByHeight, final Map<BlockId, ChainWork> chainWorks, final Map<BlockId, MedianBlockTime> medianBlockTimes, final Map<BlockId, Boolean> blockTransactionMap, final Map<BlockId, Integer> blockProcessCount, final Map<BlockId, BlockchainSegmentId> blockchainSegmentIds, final BlockId headBlockId, final BlockId headBlockHeaderId, final Map<BlockchainSegmentId, BlockchainSegmentId> blockchainSegmentParents, final Map<BlockchainSegmentId, Long> blockchainSegmentNestedSetLeft, final Map<BlockchainSegmentId, Long> blockchainSegmentNestedSetRight, final Map<BlockchainSegmentId, Long> blockchainSegmentMaxBlockHeight) {
        super(readLock, writeLock, blockHeaders, blockHeights, blockIds, blocksByHeight, chainWorks, medianBlockTimes, blockTransactionMap, blockProcessCount, blockchainSegmentIds, headBlockId, headBlockHeaderId, blockchainSegmentParents, blockchainSegmentNestedSetLeft, blockchainSegmentNestedSetRight, blockchainSegmentMaxBlockHeight, MutableBlockchainCache.MAP_FACTORY);
    }

    public MutableBlockchainCache(final Integer estimatedBlockCount) {
        super(estimatedBlockCount, MutableBlockchainCache.MAP_FACTORY);
    }

    public Integer getVersion() {
        return _version.get();
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

    public void setBlockchainSegmentId(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId) {
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

            final BlockId headBlockHeaderId = _getListValue(_headBlockHeaderId);
            if (headBlockHeaderId == null) {
                _setListValue(blockId, _headBlockHeaderId);
            }
            else {
                final ChainWork headChainWork = _chainWorks.get(headBlockHeaderId);
                if (chainWork.compareTo(headChainWork) >= 0) {
                    _setListValue(blockId, _headBlockHeaderId);
                }
            }

            if (hasTransactions) {
                final BlockId headBlockId = _getListValue(_headBlockId);
                if (headBlockId == null) {
                    _setListValue(blockId, _headBlockId);
                }
                else {
                    final ChainWork headChainWork = _chainWorks.get(headBlockId);
                    if (chainWork.compareTo(headChainWork) >= 0) {
                        _setListValue(blockId, _headBlockId);
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

            final BlockId headBlockId = _getListValue(_headBlockId);
            if (headBlockId == null) {
                _setListValue(blockId, _headBlockId);
            }
            else {
                final ChainWork headChainWork = _chainWorks.get(headBlockId);
                final ChainWork blockChainWork = _chainWorks.get(blockId);
                if (blockChainWork.compareTo(headChainWork) >= 0) {
                    _setListValue(blockId, _headBlockId);
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

            _version.incrementAndGet();
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

            _version.decrementAndGet();
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

            // Must occur before _version is decremented.
            final BlockId headBlockId = _getListValue(_headBlockId);
            final BlockId headBlockHeaderId = _getListValue(_headBlockHeaderId);

            _version.decrementAndGet();

            // Must occur after _version is decremented.
            _setListValue(headBlockId, _headBlockId);
            _setListValue(headBlockHeaderId, _headBlockHeaderId);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void applyCache(final MutableBlockchainCache blockchainCache) {
        if (blockchainCache == this) {
            while (blockchainCache._version.get() > 0) {
                this.applyVersion();
            }
            return;
        }

        _writeLock.lock();
        try {
            blockchainCache._readLock.lock();
            try {
                _castMap(_blockHeaders).applyStagedChanges(_castMap(blockchainCache._blockHeaders));
                _castMap(_blockHeights).applyStagedChanges(_castMap(blockchainCache._blockHeights));
                _castMap(_blockIds).applyStagedChanges(_castMap(blockchainCache._blockIds));
                _castMap(_chainWorks).applyStagedChanges(_castMap(blockchainCache._chainWorks));
                _castMap(_medianBlockTimes).applyStagedChanges(_castMap(blockchainCache._medianBlockTimes));
                _castMap(_blocksByHeight).applyStagedChanges(_castMap(blockchainCache._blocksByHeight));
                _castMap(_blockTransactionMap).applyStagedChanges(_castMap(blockchainCache._blockTransactionMap));
                _castMap(_blockProcessCount).applyStagedChanges(_castMap(blockchainCache._blockProcessCount));
                _castMap(_blockchainSegmentIds).applyStagedChanges(_castMap(blockchainCache._blockchainSegmentIds));
                _castMap(_blockchainSegmentParents).applyStagedChanges(_castMap(blockchainCache._blockchainSegmentParents));
                _castMap(_blockchainSegmentNestedSetLeft).applyStagedChanges(_castMap(blockchainCache._blockchainSegmentNestedSetLeft));
                _castMap(_blockchainSegmentNestedSetRight).applyStagedChanges(_castMap(blockchainCache._blockchainSegmentNestedSetRight));
                _castMap(_blockchainSegmentMaxBlockHeight).applyStagedChanges(_castMap(blockchainCache._blockchainSegmentMaxBlockHeight));

                _version.set(0);

                ((MutableList<BlockId>) _headBlockId).clear();
                ((MutableList<BlockId>) _headBlockHeaderId).clear();

                // Must occur after _version is set...
                _setListValue(blockchainCache.getHeadBlockId(), _headBlockId);
                _setListValue(blockchainCache.getHeadBlockHeaderId(), _headBlockHeaderId);
            }
            finally {
                blockchainCache._readLock.unlock();
            }
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

    // NOTE: Method is synchronized to ensure member values are up-to-date across threads.
    public synchronized MutableBlockchainCache newCopyOnWriteCache() {
        _readLock.lock();
        try {
            final MutableVersionedHashMap<BlockId, BlockHeader> blockHeaders = MutableVersionedHashMap.wrap(_castMap(_blockHeaders));
            blockHeaders.pushVersion();

            final MutableVersionedHashMap<BlockId, Long> blockHeights = MutableVersionedHashMap.wrap(_castMap(_blockHeights));
            blockHeights.pushVersion();

            final MutableVersionedHashMap<Sha256Hash, BlockId> blockIds = MutableVersionedHashMap.wrap(_castMap(_blockIds));
            blockIds.pushVersion();

            final MutableVersionedHashMap<Long, MutableList<BlockId>> blocksByHeight = MutableVersionedHashMap.wrap(_castMap(_blocksByHeight));
            blocksByHeight.pushVersion();

            final MutableVersionedHashMap<BlockId, ChainWork> chainWorks = MutableVersionedHashMap.wrap(_castMap(_chainWorks));
            chainWorks.pushVersion();

            final MutableVersionedHashMap<BlockId, MedianBlockTime> medianBlockTimes = MutableVersionedHashMap.wrap(_castMap(_medianBlockTimes));
            medianBlockTimes.pushVersion();

            final MutableVersionedHashMap<BlockId, Boolean> blockTransactionMap = MutableVersionedHashMap.wrap(_castMap(_blockTransactionMap));
            blockTransactionMap.pushVersion();

            final MutableVersionedHashMap<BlockId, Integer> blockProcessCount = MutableVersionedHashMap.wrap(_castMap(_blockProcessCount));
            blockProcessCount.pushVersion();

            final MutableVersionedHashMap<BlockId, BlockchainSegmentId> blockchainSegmentIds = MutableVersionedHashMap.wrap(_castMap(_blockchainSegmentIds));
            blockchainSegmentIds.pushVersion();

            final MutableVersionedHashMap<BlockchainSegmentId, BlockchainSegmentId> blockchainSegmentParents = MutableVersionedHashMap.wrap(_castMap(_blockchainSegmentParents));
            blockchainSegmentParents.pushVersion();

            final MutableVersionedHashMap<BlockchainSegmentId, Long> blockchainSegmentNestedSetLeft = MutableVersionedHashMap.wrap(_castMap(_blockchainSegmentNestedSetLeft));
            blockchainSegmentNestedSetLeft.pushVersion();

            final MutableVersionedHashMap<BlockchainSegmentId, Long> blockchainSegmentNestedSetRight = MutableVersionedHashMap.wrap(_castMap(_blockchainSegmentNestedSetRight));
            blockchainSegmentNestedSetRight.pushVersion();

            final MutableVersionedHashMap<BlockchainSegmentId, Long> blockchainSegmentMaxBlockHeight = MutableVersionedHashMap.wrap(_castMap(_blockchainSegmentMaxBlockHeight));
            blockchainSegmentMaxBlockHeight.pushVersion();

            return new MutableBlockchainCache(_readLock, _writeLock, blockHeaders, blockHeights, blockIds, blocksByHeight, chainWorks, medianBlockTimes, blockTransactionMap, blockProcessCount, blockchainSegmentIds, _getListValue(_headBlockId), _getListValue(_headBlockHeaderId), blockchainSegmentParents, blockchainSegmentNestedSetLeft, blockchainSegmentNestedSetRight, blockchainSegmentMaxBlockHeight);
        }
        finally {
            _readLock.unlock();
        }
    }
}
