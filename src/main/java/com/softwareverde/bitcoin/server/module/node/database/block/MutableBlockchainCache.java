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

public class MutableBlockchainCache extends AbstractBlockchainCache {
    protected Integer _version = 0;

    @Override
    protected Integer _getVersion() {
        return _version;
    }

    protected <Key, Value> MutableHashMap<Key, Value> _castMap(final Map<Key, Value> map) {
        return (MutableHashMap<Key, Value>) map;
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
            final Integer version = _getVersion();
            _blockchainSegmentIds.visit(new Map.Visitor<BlockId, BlockchainSegmentId>() {
                @Override
                public boolean run(final Tuple<BlockId, BlockchainSegmentId> entry) {
                    if (Util.areEqual(blockchainSegmentId, entry.second)) {
                        final BlockId blockId = entry.first;
                        final Long blockBlockHeight = _blockHeights.get(blockId, version);
                        if (blockBlockHeight >= minBlockHeight) {
                            entry.second = newBlockchainSegmentId;
                        }
                    }
                    return true;
                }
            }, version);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void replaceBlockchainSegmentsParent(final BlockchainSegmentId parentBlockchainSegmentId, final BlockchainSegmentId newParentBlockchainSegmentId) {
        _writeLock.lock();
        try {
            final Integer version = _getVersion();
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
            }, version);
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
            final Integer version = _getVersion();
            _castMap(_blockchainSegmentIds).put(blockId, blockchainSegmentId);

            final Long blockHeight = _blockHeights.get(blockId, version);
            if (blockHeight != null) {
                final Long maxBlockHeight = Util.coalesce(_blockchainSegmentMaxBlockHeight.get(blockchainSegmentId, version));
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
            final Integer version = _getVersion();
            final BlockHeader constBlockHeader = blockHeader.asConst();
            final Sha256Hash blockHash = constBlockHeader.getHash(); // NOTE: Also forces internal BlockHeader hash caching...

            _castMap(_blockHeaders).put(blockId, constBlockHeader);
            _castMap(_blockHeights).put(blockId, blockHeight);
            _castMap(_chainWorks).put(blockId, chainWork.asConst());
            _castMap(_medianBlockTimes).put(blockId, medianBlockTime.asConst());
            _castMap(_blockIds).put(blockHash, blockId);
            _castMap(_blockTransactionMap).put(blockId, hasTransactions);
            _castMap(_blockProcessCount).put(blockId, null);

            MutableList<BlockId> blockIds = _blocksByHeight.get(blockHeight, version);
            if (blockIds == null) {
                blockIds = new MutableList<>(1);
                _castMap(_blocksByHeight).put(blockHeight, blockIds);
            }
            blockIds.add(blockId);

            final BlockchainSegmentId blockchainSegmentId = _blockchainSegmentIds.get(blockId, version);
            if (blockchainSegmentId != null) {
                final Long maxBlockHeight = Util.coalesce(_blockchainSegmentMaxBlockHeight.get(blockchainSegmentId, version));
                if (maxBlockHeight < blockHeight) {
                    _castMap(_blockchainSegmentMaxBlockHeight).put(blockchainSegmentId, blockHeight);
                }
            }

            if (_headBlockHeaderId == null) {
                _headBlockHeaderId = blockId;
            }
            else {
                final ChainWork headChainWork = _chainWorks.get(_headBlockHeaderId, version);
                if (chainWork.compareTo(headChainWork) >= 0) {
                    _headBlockHeaderId = blockId;
                }
            }

            if (hasTransactions) {
                if (_headBlockId == null) {
                    _headBlockId = blockId;
                }
                else {
                    final ChainWork headChainWork = _chainWorks.get(_headBlockId, version);
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
            final Integer version = _getVersion();
            _castMap(_blockTransactionMap).put(blockId, true);

            if (_headBlockId == null) {
                _headBlockId = blockId;
            }
            else {
                final ChainWork headChainWork = _chainWorks.get(_headBlockId, version);
                final ChainWork blockChainWork = _chainWorks.get(blockId, version);
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
            final Integer version = _getVersion();
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
            }, version);
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

            _version += 1;
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

            _version -= 1;
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

            _version = 0;
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void incrementProcessCount(final BlockId blockId, final Integer i) {
        _writeLock.lock();
        try {
            final Integer version = _getVersion();
            final Integer processCount = Util.coalesce(_blockProcessCount.get(blockId, version));
            final int newProcessCount = Math.max(0, (processCount + i));
            _castMap(_blockProcessCount).put(blockId, newProcessCount);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public ReadOnlyBlockchainCache getReadOnlyCache(final Integer version) {
        return new ReadOnlyBlockchainCache(version, _readLock, _writeLock, _blockHeaders, _blockHeights, _blockIds, _blocksByHeight, _chainWorks, _medianBlockTimes, _blockTransactionMap, _blockProcessCount, _blockchainSegmentIds, _headBlockId, _headBlockHeaderId, _blockchainSegmentParents, _blockchainSegmentNestedSetLeft, _blockchainSegmentNestedSetRight, _blockchainSegmentMaxBlockHeight);
    }
}
