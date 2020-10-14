package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;

public class BlockInventoryMessageHandlerUtil {
    public interface BlockIdStore {
        BlockId getBlockId(Sha256Hash blockHash) throws Exception;
    }

    public static class NodeInventory {
        public final Long blockHeight;
        public final Sha256Hash blockHash;

        public NodeInventory(final Long blockHeight, final Sha256Hash blockHash) {
            this.blockHeight = blockHeight;
            this.blockHash = blockHash;
        }
    }

    public static NodeInventory getHeadBlockInventory(final Long blockHeightOfFirstBlockHash, final List<Sha256Hash> blockHashes, final BlockIdStore blockIdStore) throws Exception {
        final int blockHashCount = blockHashes.getCount();
        int lookupCount = 0;
        int i = (blockHashCount / 2);
        int maxPivotIndex = (blockHashCount - 1);
        int minPivotIndex = 0;

        final int indexOfFirstUnknown;
        while (true) {
            lookupCount += 1;

            final int index = i;
            final Sha256Hash blockHash = blockHashes.get(index);
            final BlockId blockId = blockIdStore.getBlockId(blockHash);
            boolean indexIsGreaterThanOrEqualToCorrectIndex = (blockId == null);
            Logger.trace(minPivotIndex + " <= " + index + " <= " + maxPivotIndex + " (" + indexIsGreaterThanOrEqualToCorrectIndex + ")");

            if (indexIsGreaterThanOrEqualToCorrectIndex) {
                maxPivotIndex = index;
            }
            else {
                minPivotIndex = index;
            }

            if ((maxPivotIndex - minPivotIndex) <= 1) {
                final Sha256Hash minIndexBlockHash = blockHashes.get(minPivotIndex);
                final BlockId minIndexBlockId = blockIdStore.getBlockId(minIndexBlockHash);
                final boolean minIndexIsCorrectIndex = (minIndexBlockId == null);
                if (minIndexIsCorrectIndex) {
                    indexOfFirstUnknown = minPivotIndex;
                }
                else {
                    indexOfFirstUnknown = (indexIsGreaterThanOrEqualToCorrectIndex ? index : maxPivotIndex);
                }
                break;
            }

            i = ((minPivotIndex + maxPivotIndex) / 2);
        }

        Logger.trace("Lookup Count: " + lookupCount);

        final Long blockHeight = (blockHeightOfFirstBlockHash + indexOfFirstUnknown);
        final Sha256Hash blockHash = blockHashes.get(indexOfFirstUnknown);
        return new NodeInventory(blockHeight, blockHash);
    }

    protected BlockInventoryMessageHandlerUtil() { }
}
