package com.softwareverde.bitcoin.test.fake.database;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.configuration.CheckpointConfiguration;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.MedianBlockTimeDatabaseManagerUtil;
import com.softwareverde.bitcoin.server.module.node.database.block.header.fullnode.BlockHeaderDatabaseManagerCore;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;

import java.util.Map;

public interface FakeBlockHeaderDatabaseManager extends BlockHeaderDatabaseManager {
    static MutableMedianBlockTime newInitializedMedianBlockTime(final BlockHeaderDatabaseManager blockDatabaseManager, final Sha256Hash headBlockHash) throws DatabaseException {
        return FakeBlockHeaderDatabaseManagerCore.newInitializedMedianBlockTime(blockDatabaseManager, headBlockHash);
    }

    @Override
    default BlockId insertBlockHeader(final BlockHeader blockHeader) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default void updateBlockHeader(final BlockId blockId, final BlockHeader blockHeader) throws DatabaseException { }

    @Override
    default BlockId storeBlockHeader(final BlockHeader blockHeader) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default List<BlockId> insertBlockHeaders(final List<BlockHeader> blockHeaders) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default void setBlockByteCount(final BlockId blockId, final Integer byteCount) throws DatabaseException { }

    @Override
    default Integer getBlockByteCount(final BlockId blockId) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default Sha256Hash getHeadBlockHeaderHash() throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default BlockId getHeadBlockHeaderId() throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default BlockId getBlockHeaderId(final Sha256Hash blockHash) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default BlockHeader getBlockHeader(final BlockId blockId) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default Boolean blockHeaderExists(final Sha256Hash blockHash) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default Integer getBlockDirectDescendantCount(final BlockId blockId) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default void setBlockchainSegmentId(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId) throws DatabaseException { }

    @Override
    default BlockchainSegmentId getBlockchainSegmentId(final BlockId blockId) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default Long getBlockHeight(final BlockId blockId) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default Long getBlockTimestamp(final BlockId blockId) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default BlockId getChildBlockId(final BlockchainSegmentId blockchainSegmentId, final BlockId previousBlockId) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default Boolean hasChildBlock(final BlockId blockId) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default Boolean isBlockConnectedToChain(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId, final BlockRelationship blockRelationship) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default Sha256Hash getBlockHash(final BlockId blockId) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default List<Sha256Hash> getBlockHashes(final List<BlockId> blockIds) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default BlockId getAncestorBlockId(final BlockId blockId, final Integer parentCount) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default MutableMedianBlockTime calculateMedianBlockHeaderTime() throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default MutableMedianBlockTime calculateMedianBlockTime(final BlockId blockId) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default ChainWork getChainWork(final BlockId blockId) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default BlockId getBlockIdAtHeight(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    default Map<BlockId, Long> getBlockHeights(List<BlockId> blockIds) throws DatabaseException {
        throw new UnsupportedOperationException();
    }
}

class FakeBlockHeaderDatabaseManagerCore extends BlockHeaderDatabaseManagerCore {
    public static MutableMedianBlockTime newInitializedMedianBlockTime(final BlockHeaderDatabaseManager blockDatabaseManager, final Sha256Hash headBlockHash) throws DatabaseException {
        return MedianBlockTimeDatabaseManagerUtil.calculateMedianBlockTime(blockDatabaseManager, headBlockHash);
    }

    public FakeBlockHeaderDatabaseManagerCore(final DatabaseManager databaseManager, final CheckpointConfiguration checkpointConfiguration) {
        super(databaseManager, checkpointConfiguration);
    }
}