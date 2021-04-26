package com.softwareverde.bitcoin.test.fake.database;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

import java.util.Map;

interface FakeBlockchainDatabaseManager extends BlockchainDatabaseManager {
    @Override
    default BlockchainSegment getBlockchainSegment(BlockchainSegmentId blockchainSegmentId) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default BlockchainSegmentId updateBlockchainsForNewBlock(BlockId blockId) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default BlockchainSegmentId getHeadBlockchainSegmentId() throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default BlockId getHeadBlockIdOfBlockchainSegment(BlockchainSegmentId blockchainSegmentId) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default BlockId getFirstBlockIdOfBlockchainSegment(BlockchainSegmentId blockchainSegmentId) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default BlockchainSegmentId getHeadBlockchainSegmentIdOfBlockchainSegment(BlockchainSegmentId blockchainSegmentId) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default BlockchainSegmentId getPreviousBlockchainSegmentId(BlockchainSegmentId blockchainSegmentId) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default Boolean areBlockchainSegmentsConnected(BlockchainSegmentId blockchainSegmentId0, BlockchainSegmentId blockchainSegmentId1, BlockRelationship blockRelationship) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default Map<BlockchainSegmentId, Boolean> areBlockchainSegmentsConnected(BlockchainSegmentId blockchainSegmentId, List<BlockchainSegmentId> blockchainSegmentIds, BlockRelationship blockRelationship) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default List<BlockchainSegmentId> getLeafBlockchainSegmentIds() throws DatabaseException { throw new UnsupportedOperationException(); }
}
