package com.softwareverde.bitcoin.server.module.node.database.block.header;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;

public class MedianBlockTimeDatabaseManagerUtil {
    public static MutableMedianBlockTime calculateMedianBlockTime(final BlockHeaderDatabaseManager blockHeaderDatabaseManager, final BlockId headBlockId) throws DatabaseException {
        return MedianBlockTimeDatabaseManagerUtil.calculateMedianBlockTime(blockHeaderDatabaseManager, headBlockId, null);
    }

    public static MutableMedianBlockTime calculateMedianBlockTime(final BlockHeaderDatabaseManager blockHeaderDatabaseManager, final Sha256Hash headBlockHash) throws DatabaseException {
        return MedianBlockTimeDatabaseManagerUtil.calculateMedianBlockTime(blockHeaderDatabaseManager, null, headBlockHash);
    }

    /**
     * Initializes a MedianBlockTime from the database.
     *  NOTE: The blockId/blockHash is included within the MedianBlockTime.
     *  To create the MedianTimePast for a block, provide the previousBlockId/previousBlockHash.
     */
    public static MutableMedianBlockTime calculateMedianBlockTime(final BlockHeaderDatabaseManager blockHeaderDatabaseManager, final BlockId nullableBlockId, final Sha256Hash nullableBlockHash) throws DatabaseException {
        final BlockId firstBlockIdInclusive;
        final Sha256Hash firstBlockHashInclusive;
        {
            if ( (nullableBlockId == null) && (nullableBlockHash == null) ) { return null; }

            if (nullableBlockId == null) {
                firstBlockHashInclusive = nullableBlockHash;
                firstBlockIdInclusive = blockHeaderDatabaseManager.getBlockHeaderId(firstBlockHashInclusive);
            }
            else {
                firstBlockIdInclusive = nullableBlockId;
                if (nullableBlockHash != null) {
                    firstBlockHashInclusive = nullableBlockHash;
                }
                else {
                    firstBlockHashInclusive = blockHeaderDatabaseManager.getBlockHash(firstBlockIdInclusive);
                }
            }
        }

        final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();
        if (firstBlockIdInclusive == null) { return medianBlockTime; } // Special case for the Genesis Block...

        final MutableList<BlockHeader> blockHeadersInDescendingOrder = new MutableArrayList<>(MedianBlockTime.BLOCK_COUNT);

        Sha256Hash blockHash = firstBlockHashInclusive;
        for (int i = 0; i < MedianBlockTime.BLOCK_COUNT; ++i) {
            final BlockId blockId = (i == 0 ? firstBlockIdInclusive : blockHeaderDatabaseManager.getBlockHeaderId(blockHash));
            if (blockId == null) { break; }

            final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);
            if (blockHeader == null) { break; } // Should never happen, but facilitates tests.

            blockHeadersInDescendingOrder.add(blockHeader);
            blockHash = blockHeader.getPreviousBlockHash();
        }

        // Add the blocks to the MedianBlockTime in ascending order (lowest block-height is added first)...
        final int blockHeaderCount = blockHeadersInDescendingOrder.getCount();
        for (int i = 0; i < blockHeaderCount; ++i) {
            final BlockHeader blockHeader = blockHeadersInDescendingOrder.get(blockHeaderCount - i - 1);
            medianBlockTime.addBlock(blockHeader);
        }

        return medianBlockTime;
    }

    protected MedianBlockTimeDatabaseManagerUtil() { }
}
