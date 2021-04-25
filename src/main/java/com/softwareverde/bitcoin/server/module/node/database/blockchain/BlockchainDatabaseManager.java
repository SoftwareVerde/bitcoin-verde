package com.softwareverde.bitcoin.server.module.node.database.blockchain;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

import java.util.Map;

public interface BlockchainDatabaseManager {
    BlockchainSegment getBlockchainSegment(BlockchainSegmentId blockchainSegmentId) throws DatabaseException;
    BlockchainSegmentId updateBlockchainsForNewBlock(BlockId blockId) throws DatabaseException;
    BlockchainSegmentId getHeadBlockchainSegmentId() throws DatabaseException;
    BlockId getHeadBlockIdOfBlockchainSegment(BlockchainSegmentId blockchainSegmentId) throws DatabaseException;
    BlockId getFirstBlockIdOfBlockchainSegment(BlockchainSegmentId blockchainSegmentId) throws DatabaseException;
    BlockchainSegmentId getHeadBlockchainSegmentIdOfBlockchainSegment(BlockchainSegmentId blockchainSegmentId) throws DatabaseException;
    BlockchainSegmentId getPreviousBlockchainSegmentId(BlockchainSegmentId blockchainSegmentId) throws DatabaseException;
    Boolean areBlockchainSegmentsConnected(BlockchainSegmentId blockchainSegmentId0, BlockchainSegmentId blockchainSegmentId1, BlockRelationship blockRelationship) throws DatabaseException;
    Map<BlockchainSegmentId, Boolean> areBlockchainSegmentsConnected(BlockchainSegmentId blockchainSegmentId, List<BlockchainSegmentId> blockchainSegmentIds, BlockRelationship blockRelationship) throws DatabaseException;
    List<BlockchainSegmentId> getLeafBlockchainSegmentIds() throws DatabaseException;
}
