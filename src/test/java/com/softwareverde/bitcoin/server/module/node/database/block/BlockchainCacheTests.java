package com.softwareverde.bitcoin.server.module.node.database.block;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.BlockWork;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.header.difficulty.work.MutableChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;

public class BlockchainCacheTests extends UnitTest {
    @Before
    public void before() throws Exception {
        super.before();
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_return_head_block_after_loading() throws Exception {
        // Setup
        final int blockCount = 64;
        final int blockHeaderCount = 128;
        final BlockchainSegmentId blockchainSegmentId = BlockchainSegmentId.wrap(1L);
        final MutableBlockchainCache blockchainCache = new MutableBlockchainCache(blockHeaderCount);
        final MutableBlockchainCache baseBlockchainCache = new MutableBlockchainCache(blockHeaderCount);

        blockchainCache.pushVersion();
        blockchainCache.addBlockchainSegment(blockchainSegmentId, null, 1L, 2L);

        final MutableList<BlockId> blockIdsWithTransactions = new MutableArrayList<>();

        BlockId lastBlockId = null;
        BlockId lastBlockHeaderId = null;
        try (final InputStream inputStream = BlockchainCacheTests.class.getResourceAsStream("/bootstrap/headers.dat")) {
            final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
            final MutableByteArray buffer = new MutableByteArray(BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT);

            final MedianBlockTime medianBlockTime = MedianBlockTime.fromSeconds(0L);
            ChainWork previousChainWork = new MutableChainWork();
            long blockId = 1L;

            int processCount = 0;
            while (processCount < blockHeaderCount) {
                int readByteCount = inputStream.read(buffer.unwrap());
                while ( (readByteCount >= 0) && (readByteCount < BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT) ) {
                    final int nextByte = inputStream.read();
                    if (nextByte < 0) { break; }

                    buffer.setByte(readByteCount, (byte) nextByte);
                    readByteCount += 1;
                }
                if (readByteCount != BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT) { break; }

                final BlockHeader blockHeader = blockHeaderInflater.fromBytes(buffer);
                if (blockHeader == null) { break; }

                final Difficulty difficulty = blockHeader.getDifficulty();
                final BlockWork blockWork = difficulty.calculateWork();
                final ChainWork chainWork = ChainWork.add(previousChainWork, blockWork);

                final boolean hasTransactions = (processCount < blockCount);

                lastBlockHeaderId = BlockId.wrap(blockId);
                if (hasTransactions) {
                    lastBlockId = lastBlockHeaderId;
                    blockIdsWithTransactions.add(lastBlockHeaderId);
                }

                blockchainCache.addBlock(lastBlockHeaderId, blockHeader, (blockId - 1L), chainWork, medianBlockTime, false);
                blockchainCache.setBlockchainSegmentId(lastBlockHeaderId, blockchainSegmentId);

                previousChainWork = chainWork;
                processCount += 1;
                blockId += 1L;
            }
        }

        for (final BlockId blockId : blockIdsWithTransactions) {
            blockchainCache.setHasTransactions(blockId);
        }

        baseBlockchainCache.applyCache(blockchainCache);

        // Action
        final BlockId headBlockId = baseBlockchainCache.getHeadBlockIdOfBlockchainSegment(blockchainSegmentId, true);
        final BlockId headBlockHeaderId = baseBlockchainCache.getHeadBlockIdOfBlockchainSegment(blockchainSegmentId, false);
        final BlockId childBlockId;
        {
            final Long blockHeight = (baseBlockchainCache.getBlockHeight(headBlockId) + 1L);
            childBlockId = baseBlockchainCache.getBlockHeader(blockchainSegmentId, blockHeight);
        }

        // Assert
        Assert.assertEquals(lastBlockHeaderId, headBlockHeaderId);
        Assert.assertEquals(lastBlockId, headBlockId);

        Assert.assertEquals(BlockId.wrap(lastBlockId.longValue() + 1L), childBlockId);
    }
}
