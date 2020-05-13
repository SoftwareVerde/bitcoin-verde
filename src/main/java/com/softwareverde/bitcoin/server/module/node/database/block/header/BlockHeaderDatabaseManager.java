package com.softwareverde.bitcoin.server.module.node.database.block.header;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

import java.util.Map;

public interface BlockHeaderDatabaseManager {
    Object MUTEX = new Object();

    BlockId insertBlockHeader(BlockHeader blockHeader) throws DatabaseException;
    void updateBlockHeader(BlockId blockId, BlockHeader blockHeader) throws DatabaseException;
    BlockId storeBlockHeader(BlockHeader blockHeader) throws DatabaseException;
    List<BlockId> insertBlockHeaders(List<BlockHeader> blockHeaders) throws DatabaseException;
    List<BlockId> insertBlockHeaders(List<BlockHeader> blockHeaders, Integer maxBatchSize) throws DatabaseException;
    void setBlockByteCount(BlockId blockId, Integer byteCount) throws DatabaseException;
    Integer getBlockByteCount(BlockId blockId) throws DatabaseException;
    Sha256Hash getHeadBlockHeaderHash() throws DatabaseException;
    BlockId getHeadBlockHeaderId() throws DatabaseException;
    BlockId getBlockHeaderId(Sha256Hash blockHash) throws DatabaseException;
    BlockHeader getBlockHeader(BlockId blockId) throws DatabaseException;
    Boolean blockHeaderExists(Sha256Hash blockHash) throws DatabaseException;
    Integer getBlockDirectDescendantCount(BlockId blockId) throws DatabaseException;
    void setBlockchainSegmentId(BlockId blockId, BlockchainSegmentId blockchainSegmentId) throws DatabaseException;
    BlockchainSegmentId getBlockchainSegmentId(BlockId blockId) throws DatabaseException;
    Long getBlockHeight(BlockId blockId) throws DatabaseException;
    Map<BlockId, Long> getBlockHeights(List<BlockId> blockIds) throws DatabaseException;
    Long getBlockTimestamp(BlockId blockId) throws DatabaseException;
    BlockId getChildBlockId(BlockchainSegmentId blockchainSegmentId, BlockId previousBlockId) throws DatabaseException;
    Boolean hasChildBlock(BlockId blockId) throws DatabaseException;
    Boolean isBlockConnectedToChain(BlockId blockId, BlockchainSegmentId blockchainSegmentId, BlockRelationship blockRelationship) throws DatabaseException;
    Sha256Hash getBlockHash(BlockId blockId) throws DatabaseException;
    List<Sha256Hash> getBlockHashes(List<BlockId> blockIds) throws DatabaseException;
    BlockId getAncestorBlockId(BlockId blockId, Integer parentCount) throws DatabaseException;
    MutableMedianBlockTime initializeMedianBlockTime() throws DatabaseException;
    MutableMedianBlockTime initializeMedianBlockHeaderTime() throws DatabaseException;
    MedianBlockTime calculateMedianBlockTime(BlockId blockId) throws DatabaseException;
    MedianBlockTime calculateMedianBlockTimeStartingWithBlock(BlockId blockId) throws DatabaseException;
    ChainWork getChainWork(BlockId blockId) throws DatabaseException;
    BlockId getBlockIdAtHeight(BlockchainSegmentId blockchainSegmentId, Long blockHeight) throws DatabaseException;
}
