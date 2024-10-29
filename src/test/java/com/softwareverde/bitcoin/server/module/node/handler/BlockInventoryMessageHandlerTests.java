package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.util.HashUtil;
import org.junit.Assert;
import org.junit.Test;

public class BlockInventoryMessageHandlerTests extends UnitTest {

    public class FakeBlockIdStore implements BlockInventoryMessageHandlerUtil.BlockIdStore {
        final MutableMap<Sha256Hash, BlockId> _blockIds = new MutableHashMap<>();

        @Override
        public BlockId getBlockId(final Sha256Hash blockHash) throws Exception {
            return _blockIds.get(blockHash);
        }

        public void storeBlockId(final Sha256Hash blockHash, final BlockId blockId) {
            _blockIds.put(blockHash, blockId);
        }
    }

    protected static Sha256Hash generateBlockHash(final Long index) {
        return Sha256Hash.copyOf(HashUtil.doubleSha256(ByteUtil.longToBytes(index)));
    }

    protected static List<Sha256Hash> generateBlockHashes(final Integer blockHashCount, final Long firstUnknownIndex, final FakeBlockIdStore blockIdStore) {
        final MutableList<Sha256Hash> blockHashes = new MutableArrayList<>(blockHashCount);
        for (long i = 0L; i < blockHashCount; ++i) {
            final Sha256Hash blockHash = BlockInventoryMessageHandlerTests.generateBlockHash(i);
            final BlockId blockId = BlockId.wrap(i);
            blockHashes.add(blockHash);
            if (i < firstUnknownIndex) {
                blockIdStore.storeBlockId(blockHash, blockId);
            }
        }
        return blockHashes;
    }

    @Test
    public void should_return_first_unknown_block_ix0() throws Exception {
        final FakeBlockIdStore blockIdStore = new FakeBlockIdStore();

        final long blockHeightOffset = 1000L;
        final long firstUnknownIndex = 0L;
        final Sha256Hash firstUnknownHash = BlockInventoryMessageHandlerTests.generateBlockHash(firstUnknownIndex);

        final int blockHashCount = 1024;
        final List<Sha256Hash> blockHashes = BlockInventoryMessageHandlerTests.generateBlockHashes(blockHashCount, firstUnknownIndex, blockIdStore);

        final BlockInventoryMessageHandlerUtil.NodeInventory nodeInventory = BlockInventoryMessageHandlerUtil.getHeadBlockInventory(blockHeightOffset, blockHashes, blockIdStore);

        Assert.assertEquals(Long.valueOf(blockHeightOffset + firstUnknownIndex), nodeInventory.blockHeight);
        Assert.assertEquals(firstUnknownHash, nodeInventory.blockHash);
    }


    @Test
    public void should_return_first_unknown_block_ix768() throws Exception {
        final FakeBlockIdStore blockIdStore = new FakeBlockIdStore();

        final long blockHeightOffset = 1000L;
        final long firstUnknownIndex = 768L;
        final Sha256Hash firstUnknownHash = BlockInventoryMessageHandlerTests.generateBlockHash(firstUnknownIndex);

        final int blockHashCount = 1024;
        final List<Sha256Hash> blockHashes = BlockInventoryMessageHandlerTests.generateBlockHashes(blockHashCount, firstUnknownIndex, blockIdStore);

        final BlockInventoryMessageHandlerUtil.NodeInventory nodeInventory = BlockInventoryMessageHandlerUtil.getHeadBlockInventory(blockHeightOffset, blockHashes, blockIdStore);

        Assert.assertEquals(Long.valueOf(blockHeightOffset + firstUnknownIndex), nodeInventory.blockHeight);
        Assert.assertEquals(firstUnknownHash, nodeInventory.blockHash);
    }

    @Test
    public void should_return_first_unknown_block_ix1023() throws Exception {
        final FakeBlockIdStore blockIdStore = new FakeBlockIdStore();

        final long blockHeightOffset = 1000L;
        final long firstUnknownIndex = 1023L;
        final Sha256Hash firstUnknownHash = BlockInventoryMessageHandlerTests.generateBlockHash(firstUnknownIndex);

        final int blockHashCount = 1024;
        final List<Sha256Hash> blockHashes = BlockInventoryMessageHandlerTests.generateBlockHashes(blockHashCount, firstUnknownIndex, blockIdStore);

        final BlockInventoryMessageHandlerUtil.NodeInventory nodeInventory = BlockInventoryMessageHandlerUtil.getHeadBlockInventory(blockHeightOffset, blockHashes, blockIdStore);

        Assert.assertEquals(Long.valueOf(blockHeightOffset + firstUnknownIndex), nodeInventory.blockHeight);
        Assert.assertEquals(firstUnknownHash, nodeInventory.blockHash);
    }

    @Test
    public void should_return_first_unknown_block_ix512() throws Exception {
        final FakeBlockIdStore blockIdStore = new FakeBlockIdStore();

        final long blockHeightOffset = 1000L;
        final long firstUnknownIndex = 512L;
        final Sha256Hash firstUnknownHash = BlockInventoryMessageHandlerTests.generateBlockHash(firstUnknownIndex);

        final int blockHashCount = 1024;
        final List<Sha256Hash> blockHashes = BlockInventoryMessageHandlerTests.generateBlockHashes(blockHashCount, firstUnknownIndex, blockIdStore);

        final BlockInventoryMessageHandlerUtil.NodeInventory nodeInventory = BlockInventoryMessageHandlerUtil.getHeadBlockInventory(blockHeightOffset, blockHashes, blockIdStore);

        Assert.assertEquals(Long.valueOf(blockHeightOffset + firstUnknownIndex), nodeInventory.blockHeight);
        Assert.assertEquals(firstUnknownHash, nodeInventory.blockHash);
    }

    @Test
    public void should_return_first_unknown_block_ix256() throws Exception {
        final FakeBlockIdStore blockIdStore = new FakeBlockIdStore();

        final long blockHeightOffset = 1000L;
        final long firstUnknownIndex = 256L;
        final Sha256Hash firstUnknownHash = BlockInventoryMessageHandlerTests.generateBlockHash(firstUnknownIndex);

        final int blockHashCount = 1024;
        final List<Sha256Hash> blockHashes = BlockInventoryMessageHandlerTests.generateBlockHashes(blockHashCount, firstUnknownIndex, blockIdStore);

        final BlockInventoryMessageHandlerUtil.NodeInventory nodeInventory = BlockInventoryMessageHandlerUtil.getHeadBlockInventory(blockHeightOffset, blockHashes, blockIdStore);

        Assert.assertEquals(Long.valueOf(blockHeightOffset + firstUnknownIndex), nodeInventory.blockHeight);
        Assert.assertEquals(firstUnknownHash, nodeInventory.blockHash);
    }

    @Test
    public void should_return_first_unknown_block_ix255() throws Exception {
        final FakeBlockIdStore blockIdStore = new FakeBlockIdStore();

        final long blockHeightOffset = 1000L;
        final long firstUnknownIndex = 255L;
        final Sha256Hash firstUnknownHash = BlockInventoryMessageHandlerTests.generateBlockHash(firstUnknownIndex);

        final int blockHashCount = 1024;
        final List<Sha256Hash> blockHashes = BlockInventoryMessageHandlerTests.generateBlockHashes(blockHashCount, firstUnknownIndex, blockIdStore);

        final BlockInventoryMessageHandlerUtil.NodeInventory nodeInventory = BlockInventoryMessageHandlerUtil.getHeadBlockInventory(blockHeightOffset, blockHashes, blockIdStore);

        Assert.assertEquals(Long.valueOf(blockHeightOffset + firstUnknownIndex), nodeInventory.blockHeight);
        Assert.assertEquals(firstUnknownHash, nodeInventory.blockHash);
    }

    @Test
    public void should_return_first_unknown_block_ix1() throws Exception {
        final FakeBlockIdStore blockIdStore = new FakeBlockIdStore();

        final long blockHeightOffset = 1000L;
        final long firstUnknownIndex = 1L;
        final Sha256Hash firstUnknownHash = BlockInventoryMessageHandlerTests.generateBlockHash(firstUnknownIndex);

        final int blockHashCount = 1024;
        final List<Sha256Hash> blockHashes = BlockInventoryMessageHandlerTests.generateBlockHashes(blockHashCount, firstUnknownIndex, blockIdStore);

        final BlockInventoryMessageHandlerUtil.NodeInventory nodeInventory = BlockInventoryMessageHandlerUtil.getHeadBlockInventory(blockHeightOffset, blockHashes, blockIdStore);

        Assert.assertEquals(Long.valueOf(blockHeightOffset + firstUnknownIndex), nodeInventory.blockHeight);
        Assert.assertEquals(firstUnknownHash, nodeInventory.blockHash);
    }

    @Test
    public void should_return_first_unknown_block_ix1022() throws Exception {
        final FakeBlockIdStore blockIdStore = new FakeBlockIdStore();

        final long blockHeightOffset = 1000L;
        final long firstUnknownIndex = 512L;
        final Sha256Hash firstUnknownHash = BlockInventoryMessageHandlerTests.generateBlockHash(firstUnknownIndex);

        final int blockHashCount = 1024;
        final List<Sha256Hash> blockHashes = BlockInventoryMessageHandlerTests.generateBlockHashes(blockHashCount, firstUnknownIndex, blockIdStore);

        final BlockInventoryMessageHandlerUtil.NodeInventory nodeInventory = BlockInventoryMessageHandlerUtil.getHeadBlockInventory(blockHeightOffset, blockHashes, blockIdStore);

        Assert.assertEquals(Long.valueOf(blockHeightOffset + firstUnknownIndex), nodeInventory.blockHeight);
        Assert.assertEquals(firstUnknownHash, nodeInventory.blockHash);
    }

    @Test
    public void should_return_first_unknown_block_ix769() throws Exception {
        final FakeBlockIdStore blockIdStore = new FakeBlockIdStore();

        final long blockHeightOffset = 1000L;
        final long firstUnknownIndex = 769L;
        final Sha256Hash firstUnknownHash = BlockInventoryMessageHandlerTests.generateBlockHash(firstUnknownIndex);

        final int blockHashCount = 1024;
        final List<Sha256Hash> blockHashes = BlockInventoryMessageHandlerTests.generateBlockHashes(blockHashCount, firstUnknownIndex, blockIdStore);

        final BlockInventoryMessageHandlerUtil.NodeInventory nodeInventory = BlockInventoryMessageHandlerUtil.getHeadBlockInventory(blockHeightOffset, blockHashes, blockIdStore);

        Assert.assertEquals(Long.valueOf(blockHeightOffset + firstUnknownIndex), nodeInventory.blockHeight);
        Assert.assertEquals(firstUnknownHash, nodeInventory.blockHash);
    }

    @Test
    public void should_return_first_unknown_block_ix767() throws Exception {
        final FakeBlockIdStore blockIdStore = new FakeBlockIdStore();

        final long blockHeightOffset = 1000L;
        final long firstUnknownIndex = 767L;
        final Sha256Hash firstUnknownHash = BlockInventoryMessageHandlerTests.generateBlockHash(firstUnknownIndex);

        final int blockHashCount = 1024;
        final List<Sha256Hash> blockHashes = BlockInventoryMessageHandlerTests.generateBlockHashes(blockHashCount, firstUnknownIndex, blockIdStore);

        final BlockInventoryMessageHandlerUtil.NodeInventory nodeInventory = BlockInventoryMessageHandlerUtil.getHeadBlockInventory(blockHeightOffset, blockHashes, blockIdStore);

        Assert.assertEquals(Long.valueOf(blockHeightOffset + firstUnknownIndex), nodeInventory.blockHeight);
        Assert.assertEquals(firstUnknownHash, nodeInventory.blockHash);
    }

}
