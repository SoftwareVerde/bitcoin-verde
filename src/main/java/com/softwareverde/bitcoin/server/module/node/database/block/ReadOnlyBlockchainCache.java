package com.softwareverde.bitcoin.server.module.node.database.block;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ReadOnlyBlockchainCache extends AbstractBlockchainCache {
    protected final Integer _version;

    @Override
    protected Integer _getVersion() {
        return _version;
    }

    protected ReadOnlyBlockchainCache(final Integer version, final ReentrantReadWriteLock.ReadLock readLock, final ReentrantReadWriteLock.WriteLock writeLock, final VersionedMap<BlockId, BlockHeader> blockHeaders, final VersionedMap<BlockId, Long> blockHeights, final VersionedMap<Sha256Hash, BlockId> blockIds, final VersionedMap<Long, MutableList<BlockId>> blocksByHeight, final VersionedMap<BlockId, ChainWork> chainWorks, final VersionedMap<BlockId, MedianBlockTime> medianBlockTimes, final VersionedMap<BlockId, Boolean> blockTransactionMap, final VersionedMap<BlockId, Integer> blockProcessCount, final VersionedMap<BlockId, BlockchainSegmentId> blockchainSegmentIds, final BlockId headBlockId, final BlockId headBlockHeaderId, final VersionedMap<BlockchainSegmentId, BlockchainSegmentId> blockchainSegmentParents, final VersionedMap<BlockchainSegmentId, Long> blockchainSegmentNestedSetLeft, final VersionedMap<BlockchainSegmentId, Long> blockchainSegmentNestedSetRight, final VersionedMap<BlockchainSegmentId, Long> blockchainSegmentMaxBlockHeight) {
        super(readLock, writeLock, blockHeaders, blockHeights, blockIds, blocksByHeight, chainWorks, medianBlockTimes, blockTransactionMap, blockProcessCount, blockchainSegmentIds, headBlockId, headBlockHeaderId, blockchainSegmentParents, blockchainSegmentNestedSetLeft, blockchainSegmentNestedSetRight, blockchainSegmentMaxBlockHeight);
        _version = version;
    }
}
