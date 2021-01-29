package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.test.fake.database.FakeBlockHeaderDatabaseManager;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

import java.util.HashMap;

public class FakeDatabaseManager implements com.softwareverde.bitcoin.test.fake.database.FakeDatabaseManager {
    protected static final BlockchainSegmentId BLOCKCHAIN_SEGMENT_ID = BlockchainSegmentId.wrap(1L);
    protected Long _nextBlockId = 1L;
    protected final HashMap<Sha256Hash, BlockId> _blockIds = new HashMap<Sha256Hash, BlockId>();
    protected final HashMap<BlockId, BlockHeader> _blockHeaders = new HashMap<BlockId, BlockHeader>();
    protected final HashMap<BlockId, Long> _blockHeights = new HashMap<BlockId, Long>();
    protected final HashMap<Long, BlockId> _blocksByBlockHeight = new HashMap<Long, BlockId>();
    protected final HashMap<BlockId, ChainWork> _chainWork = new HashMap<BlockId, ChainWork>();

    @Override
    public BlockHeaderDatabaseManager getBlockHeaderDatabaseManager() {
        return new FakeBlockHeaderDatabaseManager() {
            @Override
            public Sha256Hash getBlockHash(final BlockId blockId) throws DatabaseException {
                if (! _blockHeaders.containsKey(blockId)) {
                    Logger.debug("Requested unregistered BlockHeader. blockId=" + blockId);
                }

                final BlockHeader blockHeader = _blockHeaders.get(blockId);
                return blockHeader.getHash();
            }

            @Override
            public BlockchainSegmentId getBlockchainSegmentId(final BlockId blockId) {
                if (! _blockHeaders.containsKey(blockId)) {
                    return null;
                }

                return BLOCKCHAIN_SEGMENT_ID;
            }

            @Override
            public BlockId getBlockHeaderId(final Sha256Hash blockHash) {
                if (! _blockIds.containsKey(blockHash)) {
                    Logger.debug("Requested unregistered BlockId. blockHash=" + blockHash);
                }

                return _blockIds.get(blockHash);
            }

            @Override
            public Long getBlockHeight(final BlockId blockId) {
                if (! _blockHeights.containsKey(blockId)) {
                    Logger.debug("Requested unregistered BlockHeight. blockId=" + blockId);
                }

                return _blockHeights.get(blockId);
            }

            @Override
            public BlockId getAncestorBlockId(final BlockId blockId, final Integer parentCount) {
                final Long blockHeight = _blockHeights.get(blockId);
                if (! _blockHeights.containsKey(blockId)) {
                    Logger.debug("Requested unregistered BlockId. blockId=" + blockId);
                }

                final Long requestedBlockHeight = (blockHeight - parentCount);

                if (! _blocksByBlockHeight.containsKey(requestedBlockHeight)) {
                    Logger.debug("Requested unregistered BlockHeight. blockHeight=" + requestedBlockHeight);
                }

                return _blocksByBlockHeight.get(requestedBlockHeight);
            }

            @Override
            public BlockId getBlockIdAtHeight(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight) {
                if (! Util.areEqual(blockchainSegmentId, BLOCKCHAIN_SEGMENT_ID)) { return null; }

                if (! _blocksByBlockHeight.containsKey(blockHeight)) {
                    Logger.debug("Requested unregistered BlockId. blockHeight=" + blockHeight);
                }

                return _blocksByBlockHeight.get(blockHeight);
            }

            @Override
            public Boolean isBlockInvalid(final Sha256Hash blockHash, final Integer maxProcessedCount) throws DatabaseException { return false; }

            @Override
            public void markBlockAsInvalid(final Sha256Hash blockHash, final Integer processIncrement) throws DatabaseException { }

            @Override
            public void clearBlockAsInvalid(final Sha256Hash blockHash, final Integer processDecrement) throws DatabaseException { }

            @Override
            public BlockHeader getBlockHeader(final BlockId blockId) {
                if (! _blockHeaders.containsKey(blockId)) {
                    Logger.debug("Requested unregistered BlockHeader. blockId=" + blockId);
                }

                return _blockHeaders.get(blockId);
            }

            @Override
            public ChainWork getChainWork(final BlockId blockId) {
                if (! _chainWork.containsKey(blockId)) {
                    Logger.debug("Requested unregistered ChainWork. blockId=" + blockId);
                }

                return _chainWork.get(blockId);
            }

            @Override
            public MutableMedianBlockTime calculateMedianBlockTime(final BlockId blockId) throws DatabaseException {
                final Sha256Hash blockHash = this.getBlockHash(blockId);
                return FakeBlockHeaderDatabaseManager.newInitializedMedianBlockTime(this, blockHash);
            }

            @Override
            public MedianBlockTime getMedianBlockTime(final BlockId blockId) throws DatabaseException {
                return this.calculateMedianBlockTime(blockId);
            }

            @Override
            public MedianBlockTime getMedianTimePast(final BlockId blockId) throws DatabaseException {
                final Long blockHeight = _blockHeights.get(blockId);
                final BlockId previousBlockId = _blocksByBlockHeight.get(blockHeight - 1L);
                return this.calculateMedianBlockTime(previousBlockId);
            }
        };
    }

    @Override
    public Integer getMaxQueryBatchSize() {
        return 1024;
    }

    public void registerBlockHeader(final BlockHeader blockHeader, final Long blockHeight, final ChainWork chainWork) {
        final Sha256Hash blockHash = blockHeader.getHash();
        System.out.println("Registering " + blockHeader.getHash() + " -> " + blockHeight);

        final BlockId blockId = BlockId.wrap(_nextBlockId);
        _blockIds.put(blockHash, blockId);
        _blockHeights.put(blockId, blockHeight);
        _blockHeaders.put(blockId, blockHeader.asConst());
        _blocksByBlockHeight.put(blockHeight, blockId);
        _chainWork.put(blockId, chainWork);

        _nextBlockId += 1L;
    }
}