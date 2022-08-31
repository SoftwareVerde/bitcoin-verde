package com.softwareverde.bitcoin.server.module.node.database.block;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

import java.util.Map;

public interface BlockchainCache {
    BlockId getBlockId(Sha256Hash blockHash);

    BlockHeader getBlockHeader(BlockId blockId);
    BlockHeader getBlockHeader(Sha256Hash blockHash);

    Long getBlockHeight(BlockId blockId);
    ChainWork getChainWork(BlockId blockId);
    MedianBlockTime getMedianBlockTime(BlockId blockId);
    Boolean hasTransactions(BlockId blockId);

    BlockId getBlockHeader(BlockchainSegmentId blockchainSegmentId, Long blockHeight);

    BlockId getHeadBlockHeaderId();
    BlockId getHeadBlockId();

    BlockchainSegmentId getHeadBlockchainSegmentId();
    BlockchainSegmentId getRootBlockchainSegmentId();
    BlockchainSegmentId getBlockchainSegmentId(BlockId blockId);
    List<BlockId> getChildBlockIds(BlockId blockId);
    List<BlockchainSegmentId> getChildSegmentIds(BlockchainSegmentId parentBlockchainSegmentId);
    BlockchainSegment getBlockchainSegment(BlockchainSegmentId blockchainSegmentId);
    Boolean areBlockchainSegmentsConnected(BlockchainSegmentId blockchainSegmentId0, BlockchainSegmentId blockchainSegmentId1, BlockRelationship blockRelationship);
    Map<BlockchainSegmentId, Boolean> areBlockchainSegmentsConnected(BlockchainSegmentId blockchainSegmentId0, List<BlockchainSegmentId> blockchainSegmentIds, final BlockRelationship blockRelationship);
    BlockId getHeadBlockIdOfBlockchainSegment(BlockchainSegmentId blockchainSegmentId);
    BlockId getFirstBlockIdOfBlockchainSegment(BlockchainSegmentId blockchainSegmentId);
    BlockchainSegmentId getHeadBlockchainSegmentIdOfBlockchainSegment(BlockchainSegmentId blockchainSegmentId);
    List<BlockchainSegmentId> getLeafBlockchainSegmentIds();

    MutableBlockchainCache copy();
}
