package com.softwareverde.bitcoin.server.module.node.database.block;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MutableBlockchainCache implements BlockchainCache {
    protected final ReentrantReadWriteLock.ReadLock _readLock;
    protected final ReentrantReadWriteLock.WriteLock _writeLock;

    protected final HashMap<BlockId, BlockHeader> _blockHeaders = new HashMap<>();
    protected final HashMap<BlockId, Long> _blockHeights = new HashMap<>();
    protected final HashMap<Sha256Hash, BlockId> _blockIds = new HashMap<>();
    protected final HashMap<Long, MutableList<BlockId>> _blocksByHeight = new HashMap<>();

    protected BlockchainSegmentId _headBlockchainSegmentId;
    protected final HashMap<BlockId, BlockchainSegmentId> _blockchainSegmentIds = new HashMap<>();
    protected final HashMap<BlockchainSegmentId, BlockchainSegmentId> _blockchainSegmentParents = new HashMap<>();
    protected final HashMap<BlockchainSegmentId, Long> _blockchainSegmentNestedSetLeft = new HashMap<>();
    protected final HashMap<BlockchainSegmentId, Long> _blockchainSegmentNestedSetRight = new HashMap<>();

    public MutableBlockchainCache() {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _readLock = readWriteLock.readLock();
        _writeLock = readWriteLock.writeLock();
    }

    public void updateBlockchainSegmentBlocks(final BlockchainSegmentId newBlockchainSegmentId, final BlockchainSegmentId blockchainSegmentId, final Long minBlockHeight) {
        _writeLock.lock();
        try {
            for (final Map.Entry<BlockId, BlockchainSegmentId> entry : _blockchainSegmentIds.entrySet()) {
                if (Util.areEqual(blockchainSegmentId, entry.getValue())) {
                    final BlockId blockId = entry.getKey();
                    final Long blockBlockHeight = _blockHeights.get(blockId);
                    if (blockBlockHeight >= minBlockHeight) {
                        entry.setValue(newBlockchainSegmentId);
                    }
                }
            }
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void replaceBlockchainSegmentsParent(final BlockchainSegmentId parentBlockchainSegmentId, final BlockchainSegmentId newParentBlockchainSegmentId) {
        _writeLock.lock();
        try {
            for (final Map.Entry<BlockchainSegmentId, BlockchainSegmentId> entry : _blockchainSegmentParents.entrySet()) {
                final BlockchainSegmentId blockchainSegmentId = entry.getKey();
                final BlockchainSegmentId currentParentBlockchainSegmentId = entry.getValue();
                if (Util.areEqual(parentBlockchainSegmentId, currentParentBlockchainSegmentId)) {
                    if (!Util.areEqual(blockchainSegmentId, newParentBlockchainSegmentId)) { // A BlockchainSegment cannot be its own parent.
                        entry.setValue(newParentBlockchainSegmentId);
                    }
                }
            }
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

    public void invalidateBlockId(final BlockId blockId) {
        _writeLock.lock();
        try {
            final BlockHeader blockHeader = _blockHeaders.get(blockId);
            if (blockHeader != null) {
                final Sha256Hash blockHash = blockHeader.getHash();
                _blockIds.remove(blockHash);
            }
            else {
                for (final Map.Entry<Sha256Hash, BlockId> entry : _blockIds.entrySet()) {
                    if (Util.areEqual(blockId, entry.getValue())) {
                        _blockIds.remove(entry.getKey());
                        break;
                    }
                }
            }

            final Long blockHeight = _blockHeights.get(blockId);
            if (blockHeight != null) {
                final MutableList<BlockId> blockIds = _blocksByHeight.get(blockHeight);
                final int index = blockIds.indexOf(blockId);
                if (index >= 0) {
                    blockIds.remove(index);
                }
            }
            else {
                for (final MutableList<BlockId> blockIds : _blocksByHeight.values()) {
                    final int index = blockIds.indexOf(blockId);
                    if (index >= 0) {
                        blockIds.remove(index);
                        break;
                    }
                }
            }

            _blockHeaders.remove(blockId);
            _blockHeights.remove(blockId);
            _blockchainSegmentIds.remove(blockId);
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
            _blockIds.clear();
            _blocksByHeight.clear();
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void setHeadBlockchainSegmentId(final BlockchainSegmentId blockchainSegmentId) {
        _writeLock.lock();
        try {
            _headBlockchainSegmentId = blockchainSegmentId;
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

    public void addBlockBlockchainSegment(final BlockchainSegmentId blockchainSegmentId, final BlockId blockId) {
        _writeLock.lock();
        try {
            _blockchainSegmentIds.put(blockId, blockchainSegmentId);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void addBlock(final BlockId blockId, final BlockHeader blockHeader, final Long blockHeight) {
        _writeLock.lock();
        try {
            final Sha256Hash blockHash = blockHeader.getHash();

            _blockHeaders.put(blockId, blockHeader);
            _blockHeights.put(blockId, blockHeight);
            _blockIds.put(blockHash, blockId);

            MutableList<BlockId> blockIds = _blocksByHeight.get(blockHeight);
            if (blockIds == null) {
                blockIds = new MutableList<>(1);
                _blocksByHeight.put(blockHeight, blockIds);
            }
            blockIds.add(blockId);
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
    public Long getBlockHeight(final Sha256Hash blockHash) {
        _readLock.lock();
        try {
            final BlockId blockId = _blockIds.get(blockHash);
            if (blockId == null) { return null; }
            return _blockHeights.get(blockId);
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
                for (final Map.Entry<BlockchainSegmentId, Long> nestedSetLeft : _blockchainSegmentNestedSetLeft.entrySet()) {
                    if (nestedSetLeft.getValue() <= maxNestedSetLeft) {
                        parentBlockchainSegmentIds.add(nestedSetLeft.getKey());
                    }
                }

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
    public BlockchainSegmentId getHeadBlockchainSegmentId() {
        _readLock.lock();
        try {
            return _headBlockchainSegmentId;
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public BlockchainSegmentId getRootBlockchainSegmentId() {
        _readLock.lock();
        try {
            for (final Map.Entry<BlockchainSegmentId, BlockchainSegmentId> entry : _blockchainSegmentParents.entrySet()) {
                if (entry.getValue() == null) {
                    return entry.getKey();
                }
            }
            return null;
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
    public List<BlockchainSegmentId> getChildSegmentIds(final BlockchainSegmentId parentBlockchainSegmentId) {
        _readLock.lock();
        try {
            final MutableList<BlockchainSegmentId> blockchainSegmentIds = new MutableList<>();
            for (final Map.Entry<BlockchainSegmentId, BlockchainSegmentId> entry : _blockchainSegmentParents.entrySet()) {
                final BlockchainSegmentId blockchainSegmentId = entry.getKey();
                final BlockchainSegmentId entryParentBlockchainSegmentId = entry.getValue();
                if (Util.areEqual(parentBlockchainSegmentId, entryParentBlockchainSegmentId)) {
                    blockchainSegmentIds.add(blockchainSegmentId);
                }
            }
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
    public Map<BlockchainSegmentId, Boolean> areBlockchainSegmentsConnected(final BlockchainSegmentId blockchainSegmentId0, final List<BlockchainSegmentId> blockchainSegmentIds, final BlockRelationship blockRelationship) {
        _readLock.lock();
        try {
            final Long nestedSetLeft0 = _blockchainSegmentNestedSetLeft.get(blockchainSegmentId0);
            final Long nestedSetRight0 = _blockchainSegmentNestedSetRight.get(blockchainSegmentId0);

            final HashMap<BlockchainSegmentId, Boolean> areConnected = new HashMap<>();
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
        return null;
    }
}
