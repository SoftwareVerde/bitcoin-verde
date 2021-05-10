package com.softwareverde.bitcoin.server.module.node.sync.block;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeBlockStore;
import com.softwareverde.bitcoin.test.fake.database.FakeFullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.test.fake.database.MockBlockDatabaseManager;
import com.softwareverde.bitcoin.test.fake.database.MockBlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.test.fake.database.MockBlockchainDatabaseManager;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

public class BlockDownloadPlannerCoreTests extends UnitTest {

    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_download_genesis_block_when_no_blocks_available() throws Exception {
        // Setup
        final MockBlockStore blockStore = new MockBlockStore();
        final FakeFullNodeDatabaseManagerFactory databaseManagerFactory = new FakeFullNodeDatabaseManagerFactory();

        final MockBlockchainDatabaseManager blockchainDatabaseManager = new MockBlockchainDatabaseManager();
        databaseManagerFactory.setBlockchainDatabaseManager(blockchainDatabaseManager);

        blockchainDatabaseManager.setHeadBlockchainSegmentId(BlockchainSegmentId.wrap(1L));

        final BlockDownloadPlannerCore blockDownloadPlanner = new BlockDownloadPlannerCore(databaseManagerFactory, blockStore);

        // Action
        final List<PendingBlockInventory> nextPendingBlockInventoryBatch = blockDownloadPlanner.getNextPendingBlockInventoryBatch();

        // Assert
        Assert.assertTrue(nextPendingBlockInventoryBatch.getCount() > 0);

        final PendingBlockInventory pendingBlockInventory = nextPendingBlockInventoryBatch.get(0);
        Assert.assertEquals(BlockHeader.GENESIS_BLOCK_HASH, pendingBlockInventory.blockHash);
    }

    @Test
    public void should_return_empty_list_when_all_genesis_is_downloaded_and_no_headers_are_processed() throws Exception {
        // Setup
        final MockBlockStore blockStore = new MockBlockStore();
        final FakeFullNodeDatabaseManagerFactory databaseManagerFactory = new FakeFullNodeDatabaseManagerFactory();

        blockStore.setPendingBlockExists(BlockHeader.GENESIS_BLOCK_HASH, true);

        final MockBlockchainDatabaseManager blockchainDatabaseManager = new MockBlockchainDatabaseManager();
        databaseManagerFactory.setBlockchainDatabaseManager(blockchainDatabaseManager);

        final MockBlockHeaderDatabaseManager blockHeaderDatabaseManager = new MockBlockHeaderDatabaseManager();
        databaseManagerFactory.setBlockHeaderDatabaseManager(blockHeaderDatabaseManager);

        blockchainDatabaseManager.setHeadBlockchainSegmentId(BlockchainSegmentId.wrap(1L));

        final BlockDownloadPlannerCore blockDownloadPlanner = new BlockDownloadPlannerCore(databaseManagerFactory, blockStore);

        // Action
        final List<PendingBlockInventory> nextPendingBlockInventoryBatch = blockDownloadPlanner.getNextPendingBlockInventoryBatch();

        // Assert
        Assert.assertTrue(nextPendingBlockInventoryBatch.isEmpty());
    }

    @Test
    public void should_not_download_alternate_blocks_with_insufficient_work() throws Exception {
        // Setup
        final MockBlockStore blockStore = new MockBlockStore();
        final FakeFullNodeDatabaseManagerFactory databaseManagerFactory = new FakeFullNodeDatabaseManagerFactory();

        blockStore.setPendingBlockExists(BlockHeader.GENESIS_BLOCK_HASH, true);

        final MockBlockchainDatabaseManager blockchainDatabaseManager = new MockBlockchainDatabaseManager();
        databaseManagerFactory.setBlockchainDatabaseManager(blockchainDatabaseManager);

        final MockBlockHeaderDatabaseManager blockHeaderDatabaseManager = new MockBlockHeaderDatabaseManager();
        databaseManagerFactory.setBlockHeaderDatabaseManager(blockHeaderDatabaseManager);

        final MockBlockDatabaseManager blockDatabaseManager = new MockBlockDatabaseManager();
        databaseManagerFactory.setBlockDatabaseManager(blockDatabaseManager);

        final BlockchainSegmentId headBlockchainSegmentId = BlockchainSegmentId.wrap(28L);
        blockchainDatabaseManager.setHeadBlockchainSegmentId(headBlockchainSegmentId);
        blockchainDatabaseManager.addLeafBlockchainSegmentId(headBlockchainSegmentId);

        { // Main Chain...
            final BlockId blockId = BlockId.wrap(686125L);
            final Sha256Hash blockHash = Sha256Hash.fromHexString("000000000000000002A145CF5121EB801B48591B76BF0FDB13115A8860ECCE75");
            final Long blockHeight = 684895L;
            final ChainWork chainWork = ChainWork.fromHexString("000000000000000000000000000000000000000001666B80DAE7C4FCD81283C5");
            blockHeaderDatabaseManager.defineBlock(headBlockchainSegmentId, blockId, blockHash, blockHeight);
            blockHeaderDatabaseManager.setChainWork(blockId, chainWork);

            blockHeaderDatabaseManager.setHeadBlockId(blockId);
            blockchainDatabaseManager.setHeadBlockId(headBlockchainSegmentId, blockId);
        }

        { // Block Height 684751 (Aka: 684895 - maxAlternateEvaluateBlockDepth)
            final BlockId blockId = BlockId.wrap(685981L);
            final Sha256Hash blockHash = Sha256Hash.fromHexString("000000000000000000DF699A93184C6314AE83E555FABA12EE61E27066970EFE");
            final Long blockHeight = 684751L;
            final ChainWork chainWork = ChainWork.fromHexString("0000000000000000000000000000000000000000016642E4E2F7A8999F607934");
            blockHeaderDatabaseManager.defineBlock(headBlockchainSegmentId, blockId, blockHash, blockHeight);
            blockHeaderDatabaseManager.setChainWork(blockId, chainWork);
        }

        final BlockchainSegmentId alternateBlockchainSegmentId = BlockchainSegmentId.wrap(11L);
        blockchainDatabaseManager.addLeafBlockchainSegmentId(alternateBlockchainSegmentId);

        {
            final BlockId blockId = BlockId.wrap(666442L);
            final Sha256Hash blockHash = Sha256Hash.fromHexString("000000000000000003E7EA491122031D5508D60CCD78665FD8000EFB37EB1E85");
            final Long blockHeight = 662858L;
            final ChainWork chainWork = ChainWork.fromHexString("000000000000000000000000000000000000000001536D6D26DE4CB4937B8685");
            blockHeaderDatabaseManager.defineBlock(alternateBlockchainSegmentId, blockId, blockHash, blockHeight);
            blockHeaderDatabaseManager.setChainWork(blockId, chainWork);
            blockchainDatabaseManager.setHeadBlockId(alternateBlockchainSegmentId, blockId);
        }

        final BlockDownloadPlannerCore blockDownloadPlanner = new BlockDownloadPlannerCore(databaseManagerFactory, blockStore);

        // Action
        final List<PendingBlockInventory> nextPendingBlockInventoryBatch = blockDownloadPlanner.getNextPendingBlockInventoryBatch();

        // Assert
        Assert.assertTrue(nextPendingBlockInventoryBatch.isEmpty());
    }


    @Test
    public void should_download_alternate_blocks_with_sufficient_work() throws Exception {
        // Setup
        final MockBlockStore blockStore = new MockBlockStore();
        final FakeFullNodeDatabaseManagerFactory databaseManagerFactory = new FakeFullNodeDatabaseManagerFactory();

        blockStore.setPendingBlockExists(BlockHeader.GENESIS_BLOCK_HASH, true);

        final MockBlockchainDatabaseManager blockchainDatabaseManager = new MockBlockchainDatabaseManager();
        databaseManagerFactory.setBlockchainDatabaseManager(blockchainDatabaseManager);

        final MockBlockHeaderDatabaseManager blockHeaderDatabaseManager = new MockBlockHeaderDatabaseManager();
        databaseManagerFactory.setBlockHeaderDatabaseManager(blockHeaderDatabaseManager);

        final MockBlockDatabaseManager blockDatabaseManager = new MockBlockDatabaseManager();
        databaseManagerFactory.setBlockDatabaseManager(blockDatabaseManager);

        final BlockchainSegmentId headBlockchainSegmentId = BlockchainSegmentId.wrap(28L);
        blockchainDatabaseManager.setHeadBlockchainSegmentId(headBlockchainSegmentId);
        blockchainDatabaseManager.addLeafBlockchainSegmentId(headBlockchainSegmentId);

        { // Main Chain head Block...
            final BlockId blockId = BlockId.wrap(686125L);
            final Sha256Hash blockHash = Sha256Hash.fromHexString("000000000000000002A145CF5121EB801B48591B76BF0FDB13115A8860ECCE75");
            final Long blockHeight = 684895L;
            final ChainWork chainWork = ChainWork.fromHexString("000000000000000000000000000000000000000001666B80DAE7C4FCD81283C5");
            blockHeaderDatabaseManager.defineBlock(headBlockchainSegmentId, blockId, blockHash, blockHeight);
            blockHeaderDatabaseManager.setChainWork(blockId, chainWork);

            blockHeaderDatabaseManager.setHeadBlockId(blockId);
            blockchainDatabaseManager.setHeadBlockId(headBlockchainSegmentId, blockId);
        }

        { // Main Chain Block Height 684751 (Aka: 684895 - maxAlternateEvaluateBlockDepth)
            final BlockId blockId = BlockId.wrap(685981L);
            final Sha256Hash blockHash = Sha256Hash.fromHexString("000000000000000000DF699A93184C6314AE83E555FABA12EE61E27066970EFE");
            final Long blockHeight = 684751L;
            final ChainWork chainWork = ChainWork.fromHexString("0000000000000000000000000000000000000000000000000000000000000000"); // Disables the minimum amount of chain work....
            blockHeaderDatabaseManager.defineBlock(headBlockchainSegmentId, blockId, blockHash, blockHeight);
            blockHeaderDatabaseManager.setChainWork(blockId, chainWork);
        }

        final BlockchainSegmentId alternateBlockchainSegmentId = BlockchainSegmentId.wrap(11L);
        blockchainDatabaseManager.addLeafBlockchainSegmentId(alternateBlockchainSegmentId);
        blockDatabaseManager.setHeadBlockIdOfBlockchainSegment(alternateBlockchainSegmentId, null); // Unnecessary, but explicitly un-define a processed Block (not BlockHeader) for this segment to force BlockchainSegment recursion search...

        { // The head Block of the alternate chain...
            final BlockId blockId = BlockId.wrap(666442L);
            final Sha256Hash blockHash = Sha256Hash.fromHexString("000000000000000003E7EA491122031D5508D60CCD78665FD8000EFB37EB1E85");
            final Long blockHeight = 662858L;
            final ChainWork chainWork = ChainWork.fromHexString("000000000000000000000000000000000000000001536D6D26DE4CB4937B8685");
            blockHeaderDatabaseManager.defineBlock(alternateBlockchainSegmentId, blockId, blockHash, blockHeight);
            blockHeaderDatabaseManager.setChainWork(blockId, chainWork);
            blockchainDatabaseManager.setHeadBlockId(alternateBlockchainSegmentId, blockId);
        }

        {
            final BlockId blockId = BlockId.wrap(664209L);
            final Sha256Hash blockHash = Sha256Hash.fromHexString("0000000000000000223D4B559F17C33E6E463EB9656F4F66D5E76FC341881C7A");
            final Long blockHeight = 662379L;
            final ChainWork chainWork = ChainWork.fromHexString("0000000000000000000000000000000000000000015367340B20A6B5684CB501");
            blockHeaderDatabaseManager.defineBlock(alternateBlockchainSegmentId, blockId, blockHash, blockHeight);
            blockHeaderDatabaseManager.setChainWork(blockId, chainWork);
            blockchainDatabaseManager.setHeadBlockId(alternateBlockchainSegmentId, blockId);

            blockchainDatabaseManager.setTailBlockId(alternateBlockchainSegmentId, blockId);
        }

        final BlockchainSegmentId rootBlockchainSegmentId = BlockchainSegmentId.wrap(1L);

        { // Shared block between segments...
            final BlockId blockId = BlockId.wrap(661648L);
            final Sha256Hash blockHash = Sha256Hash.fromHexString("00000000000000000083ED4B7A780D59E3983513215518AD75654BB02DEEE62F");
            final Long blockHeight = 661647L;
            blockHeaderDatabaseManager.defineBlock(rootBlockchainSegmentId, blockId, blockHash, blockHeight);
            blockchainDatabaseManager.setHeadBlockId(rootBlockchainSegmentId, blockId);

            blockDatabaseManager.setHeadBlockIdOfBlockchainSegment(rootBlockchainSegmentId, blockId);
            blockStore.setPendingBlockExists(blockHash, true);
        }

        final MutableList<Sha256Hash> expectedInventories = new MutableList<>();
        { // Children BlockHeaders of the shared block... (i.e. the expected return set)
            final BlockchainSegmentId blockchainSegmentId = BlockchainSegmentId.wrap(3L);

            blockchainDatabaseManager.setParentBlockchainSegmentId(alternateBlockchainSegmentId, blockchainSegmentId);
            blockchainDatabaseManager.setParentBlockchainSegmentId(blockchainSegmentId, rootBlockchainSegmentId);

            blockDatabaseManager.setHeadBlockIdOfBlockchainSegment(blockchainSegmentId, null); // Unnecessary, but explicitly un-define a processed Block (not BlockHeader) for this segment to force BlockchainSegment recursion search...

            {
                final BlockId blockId = BlockId.wrap(662138L);
                final Sha256Hash blockHash = Sha256Hash.fromHexString("000000000000000004284C9D8B2C8FF731EFEAEC6BE50729BDC9BD07F910757D");
                final Long blockHeight = 661648L;
                blockHeaderDatabaseManager.defineBlock(alternateBlockchainSegmentId, blockId, blockHash, blockHeight);
                blockchainDatabaseManager.setTailBlockId(blockchainSegmentId, blockId);
                blockStore.setPendingBlockExists(blockHash, false);

                expectedInventories.add(blockHash);
            }

            {
                final BlockId blockId = BlockId.wrap(662139L);
                final Sha256Hash blockHash = Sha256Hash.fromHexString("000000000000000003C0396339897BAC7EFC5075B74B0BA3C7DB6E94BF682CB6");
                final Long blockHeight = 661649L;
                blockHeaderDatabaseManager.defineBlock(alternateBlockchainSegmentId, blockId, blockHash, blockHeight);
                blockStore.setPendingBlockExists(blockHash, false);

                expectedInventories.add(blockHash);
            }

            {
                final BlockId blockId = BlockId.wrap(662140L);
                final Sha256Hash blockHash = Sha256Hash.fromHexString("000000000000000000FC006AC84F3E6D4273E7E162F44887FFE9944D56F27824");
                final Long blockHeight = 661650L;
                blockHeaderDatabaseManager.defineBlock(alternateBlockchainSegmentId, blockId, blockHash, blockHeight);
                blockchainDatabaseManager.setHeadBlockId(blockchainSegmentId, blockId);
                blockStore.setPendingBlockExists(blockHash, false);

                expectedInventories.add(blockHash);
            }

            // NOTE: The test should provide at least [batchSize] blocks available to list, but it isn't strictly necessary, however
            //  a warning will be generated during execution due to there being blocks not defined leading up to batchSize and/or the original
            //  "alternateBlock".
        }

        final BlockDownloadPlannerCore blockDownloadPlanner = new BlockDownloadPlannerCore(databaseManagerFactory, blockStore);

        // Action
        final List<PendingBlockInventory> nextPendingBlockInventoryBatch = blockDownloadPlanner.getNextPendingBlockInventoryBatch();

        // Assert
        Assert.assertEquals(3, nextPendingBlockInventoryBatch.getCount());
        Assert.assertEquals(expectedInventories.get(0), nextPendingBlockInventoryBatch.get(0).blockHash);
        Assert.assertEquals(expectedInventories.get(1), nextPendingBlockInventoryBatch.get(1).blockHash);
        Assert.assertEquals(expectedInventories.get(2), nextPendingBlockInventoryBatch.get(2).blockHash);
    }
}

class MockBlockStore implements FakeBlockStore {
    protected final HashMap<Sha256Hash, Boolean> _existingBlocks = new HashMap<>();
    protected final HashMap<Sha256Hash, Boolean> _existingPendingBlocks = new HashMap<>();

    @Override
    public Boolean pendingBlockExists(final Sha256Hash blockHash) {
        return Util.coalesce(_existingPendingBlocks.get(blockHash), false);
    }

    public void setPendingBlockExists(final Sha256Hash blockHash, final Boolean exists) {
        _existingPendingBlocks.put(blockHash, exists);
    }

    public void setBlockExists(final Sha256Hash blockHash, final Boolean exists) {
        _existingBlocks.put(blockHash, exists);
    }

    @Override
    public Boolean blockExists(final Sha256Hash blockHash, final Long blockHeight) {
        return Util.coalesce(_existingBlocks.get(blockHash), false);
    }

    @Override
    public String getDataDirectory() {
        return null;
    }
}