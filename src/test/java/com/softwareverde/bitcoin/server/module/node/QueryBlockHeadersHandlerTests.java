package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.query.response.InventoryMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.handler.block.QueryBlocksHandler;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.test.fake.FakeBinarySocket;
import com.softwareverde.bitcoin.test.fake.FakeBitcoinNode;
import com.softwareverde.bitcoin.test.fake.FakeSocket;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.util.HexUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class QueryBlockHeadersHandlerTests extends IntegrationTest {

    protected static final LocalNodeFeatures _localNodeFeatures = new LocalNodeFeatures() {
        @Override
        public NodeFeatures getNodeFeatures() {
            final NodeFeatures nodeFeatures = new NodeFeatures();
            nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
            return nodeFeatures;
        }
    };

    /**
     * Creates the following scenario...
     *
     *       E      Height: 4
     *       |
     *       D      Height: 3
     *       |
     *       C      Height: 2
     *       |
     *       B      Height: 1
     *       |
     *    #1 A      Height: 0
     *
     */
    protected Block[] _initScenario() throws Exception {
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final BlockInflater blockInflater = new BlockInflater();

            final String[] blockDatas = new String[]{BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2, BlockData.MainChain.BLOCK_3, BlockData.MainChain.BLOCK_4};
            final Block[] blocks = new Block[blockDatas.length];
            int i = 0;
            for (final String blockData : blockDatas) {
                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
                blockDatabaseManager.insertBlock(block);
                blocks[i] = block;
                i += 1;
            }

            return blocks;
        }
    }

    /**
     * Creates the following scenario...
     *
     *     F                        Height: 5
     *     |
     *     E         E'             Height: 4
     *     |         |
     *  #4 +----D----+ #5           Height: 3
     *          |
     *          C         C''       Height: 2
     *          |         |
     *       #2 +----B----+ #3      Height: 1
     *               |
     *               A #1           Height: 0
     *
     */
    protected Block[] _initScenario2(final Block[] blocks) throws Exception {
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final BlockInflater blockInflater = new BlockInflater();

            final Block block5 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_5));
            blockDatabaseManager.insertBlock(block5);

            final Block forkedBlock0 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain3.BLOCK_2));
            blockDatabaseManager.insertBlock(forkedBlock0);

            final Block forkedBlock1; // NOTE: Has an invalid hash, but shouldn't matter...
            {
                final MutableBlock mutableBlock = new MutableBlock(blocks[blocks.length - 1]);
                mutableBlock.setNonce(mutableBlock.getNonce() + 1);
                forkedBlock1 = mutableBlock;
            }
            blockDatabaseManager.insertBlock(forkedBlock1);

            final Block[] newBlocks = new Block[blocks.length + 3];
            for (int i = 0; i < blocks.length; ++i) {
                newBlocks[i] = blocks[i];
            }
            newBlocks[blocks.length + 0] = block5;
            newBlocks[blocks.length + 1] = forkedBlock0;
            newBlocks[blocks.length + 2] = forkedBlock1;

            { //  Sanity check for the appropriate chain structure...
                Assert.assertEquals(newBlocks[0].getHash(), newBlocks[1].getPreviousBlockHash());
                Assert.assertEquals(newBlocks[1].getHash(), newBlocks[2].getPreviousBlockHash());
                Assert.assertEquals(newBlocks[2].getHash(), newBlocks[3].getPreviousBlockHash());
                Assert.assertEquals(newBlocks[3].getHash(), newBlocks[4].getPreviousBlockHash());
                Assert.assertEquals(newBlocks[4].getHash(), newBlocks[5].getPreviousBlockHash());

                Assert.assertEquals(newBlocks[1].getHash(), newBlocks[6].getPreviousBlockHash());

                Assert.assertEquals(newBlocks[3].getHash(), newBlocks[7].getPreviousBlockHash());
            }

            return newBlocks;
        }
    }

    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_return_genesis_blocks_when_no_matches() throws Exception {
        // Setup
        final Block[] scenarioBlocks;
        final Block[] allBlocks;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            scenarioBlocks = _initScenario();
            allBlocks = _initScenario2(scenarioBlocks);
        }

        final Integer bestChainHeight = scenarioBlocks.length + 1;
        final Block[] mainChainBlocks = new Block[bestChainHeight];
        for (int i = 0; i < scenarioBlocks.length + 1; ++i) { mainChainBlocks[i] = allBlocks[i]; }

        final QueryBlocksHandler queryBlocksHandler = new QueryBlocksHandler(_fullNodeDatabaseManagerFactory);

        final FakeBitcoinNode bitcoinNode = new FakeBitcoinNode(new FakeBinarySocket(new FakeSocket(), _threadPool), _threadPool, _localNodeFeatures);

        final Integer blockOffset = 0; // The block header/offset that is provided as the last known header...

        final List<Sha256Hash> blockHashes = new MutableList<Sha256Hash>();

        // Action
        queryBlocksHandler.run(blockHashes, new ImmutableSha256Hash(), bitcoinNode);

        // Assert
        final List<ProtocolMessage> sentMessages = bitcoinNode.getSentMessages();
        Assert.assertEquals(1, sentMessages.getCount());

        final InventoryMessage inventoryMessage = (InventoryMessage) (sentMessages.get(0));
        final List<InventoryItem> dataHashes = inventoryMessage.getInventoryItems();
        Assert.assertEquals(bestChainHeight - blockOffset, dataHashes.getCount());

        int i = blockOffset;
        for (final InventoryItem inventoryItem : dataHashes) {
            Assert.assertEquals(mainChainBlocks[i].getHash(), inventoryItem.getItemHash());
            i += 1;
        }

        bitcoinNode.disconnect();
    }

    @Test
    public void should_return_first_block_when_match_found() throws Exception {
        // Setup
        final Block[] scenarioBlocks;
        final Block[] allBlocks;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            scenarioBlocks = _initScenario();
            allBlocks = _initScenario2(scenarioBlocks);
        }

        final Integer bestChainHeight = scenarioBlocks.length + 1;
        final Block[] mainChainBlocks = new Block[bestChainHeight];
        for (int i = 0; i < scenarioBlocks.length + 1; ++i) { mainChainBlocks[i] = allBlocks[i]; }

        final QueryBlocksHandler queryBlocksHandler = new QueryBlocksHandler(_fullNodeDatabaseManagerFactory);

        final FakeBitcoinNode bitcoinNode = new FakeBitcoinNode(new FakeBinarySocket(new FakeSocket(), _threadPool), _threadPool, _localNodeFeatures);

        final Integer blockOffset = 0; // The block header/offset that is provided as the last known header...

        final MutableList<Sha256Hash> blockHashes = new MutableList<Sha256Hash>();
        blockHashes.add(allBlocks[blockOffset].getHash());

        // Action
        queryBlocksHandler.run(blockHashes, new ImmutableSha256Hash(), bitcoinNode);

        // Assert
        final List<ProtocolMessage> sentMessages = bitcoinNode.getSentMessages();
        Assert.assertEquals(1, sentMessages.getCount());

        final InventoryMessage inventoryMessage = (InventoryMessage) (sentMessages.get(0));
        final List<InventoryItem> dataHashes = inventoryMessage.getInventoryItems();
        Assert.assertEquals(bestChainHeight - blockOffset - 1, dataHashes.getCount());

        int i = blockOffset;
        for (final InventoryItem inventoryItem : dataHashes) {
            Assert.assertEquals(mainChainBlocks[i + 1].getHash(), inventoryItem.getItemHash());
            i += 1;
        }

        bitcoinNode.disconnect();
    }

    @Test
    public void should_return_first_blocks_when_non_genesis_match_found() throws Exception {
        // Setup
        final Block[] scenarioBlocks;
        final Block[] allBlocks;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            scenarioBlocks = _initScenario();
            allBlocks = _initScenario2(scenarioBlocks);
        }

        final Integer bestChainHeight = scenarioBlocks.length + 1;
        final Block[] mainChainBlocks = new Block[bestChainHeight];
        for (int i = 0; i < scenarioBlocks.length + 1; ++i) { mainChainBlocks[i] = allBlocks[i]; }

        final QueryBlocksHandler queryBlocksHandler = new QueryBlocksHandler(_fullNodeDatabaseManagerFactory);

        final FakeBitcoinNode bitcoinNode = new FakeBitcoinNode(new FakeBinarySocket(new FakeSocket(), _threadPool), _threadPool, _localNodeFeatures);

        final Integer blockOffset = 1; // The block header/offset that is provided as the last known header...

        final MutableList<Sha256Hash> blockHashes = new MutableList<Sha256Hash>();
        blockHashes.add(allBlocks[blockOffset].getHash());

        // Action
        queryBlocksHandler.run(blockHashes, new ImmutableSha256Hash(), bitcoinNode);

        // Assert
        final List<ProtocolMessage> sentMessages = bitcoinNode.getSentMessages();
        Assert.assertEquals(1, sentMessages.getCount());

        final InventoryMessage inventoryMessage = (InventoryMessage) (sentMessages.get(0));
        final List<InventoryItem> dataHashes = inventoryMessage.getInventoryItems();
        Assert.assertEquals(bestChainHeight - blockOffset - 1, dataHashes.getCount());

        int i = blockOffset;
        for (final InventoryItem inventoryItem : dataHashes) {
            Assert.assertEquals(mainChainBlocks[i + 1].getHash(), inventoryItem.getItemHash());
            i += 1;
        }

        bitcoinNode.disconnect();
    }

    @Test
    public void should_return_blocks_when_match_found() throws Exception {
        // Setup
        final Block[] scenarioBlocks;
        final Block[] allBlocks;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            scenarioBlocks = _initScenario();
            allBlocks = _initScenario2(scenarioBlocks);
        }

        final Integer bestChainHeight = scenarioBlocks.length + 1;
        final Block[] mainChainBlocks = new Block[bestChainHeight];
        for (int i = 0; i < scenarioBlocks.length + 1; ++i) { mainChainBlocks[i] = allBlocks[i]; }

        final QueryBlocksHandler queryBlocksHandler = new QueryBlocksHandler(_fullNodeDatabaseManagerFactory);

        final FakeBitcoinNode bitcoinNode = new FakeBitcoinNode(new FakeBinarySocket(new FakeSocket(), _threadPool), _threadPool, _localNodeFeatures);

        final Integer blockOffset = 2; // The block header/offset that is provided as the last known header...

        final MutableList<Sha256Hash> blockHashes = new MutableList<Sha256Hash>();
        blockHashes.add(allBlocks[blockOffset].getHash());

        // Action
        queryBlocksHandler.run(blockHashes, new ImmutableSha256Hash(), bitcoinNode);

        // Assert
        final List<ProtocolMessage> sentMessages = bitcoinNode.getSentMessages();
        Assert.assertEquals(1, sentMessages.getCount());

        final InventoryMessage inventoryMessage = (InventoryMessage) (sentMessages.get(0));
        final List<InventoryItem> dataHashes = inventoryMessage.getInventoryItems();
        Assert.assertEquals(bestChainHeight - blockOffset - 1, dataHashes.getCount());

        int i = blockOffset;
        for (final InventoryItem inventoryItem : dataHashes) {
            Assert.assertEquals(mainChainBlocks[i + 1].getHash(), inventoryItem.getItemHash());
            i += 1;
        }

        bitcoinNode.disconnect();
    }

    @Test
    public void should_return_forked_blocks_when_match_found() throws Exception {
        // Setup
        final Block[] scenarioBlocks ;
        final Block[] allBlocks;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            scenarioBlocks = _initScenario();
            allBlocks = _initScenario2(scenarioBlocks);
        }

        final Block fPrimeBlock;
        { // Create an additional child onto block E' (F')...
            final MutableBlock mutableBlock = new MutableBlock(allBlocks[allBlocks.length - 1]);
            mutableBlock.setPreviousBlockHash(allBlocks[allBlocks.length - 1].getHash());

            try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
                final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockDatabaseManager.insertBlock(mutableBlock);
                }
            }

            fPrimeBlock = mutableBlock;
        }

        final Integer bestChainHeight = scenarioBlocks.length + 1;
        final Block[] mainChainBlocks = new Block[bestChainHeight];
        for (int i = 0; i < scenarioBlocks.length + 1; ++i) { mainChainBlocks[i] = allBlocks[i]; }

        final QueryBlocksHandler queryBlocksHandler = new QueryBlocksHandler(_fullNodeDatabaseManagerFactory);

        final FakeBitcoinNode bitcoinNode = new FakeBitcoinNode(new FakeBinarySocket(new FakeSocket(), _threadPool), _threadPool, _localNodeFeatures);

        final MutableList<Sha256Hash> blockHashes = new MutableList<Sha256Hash>();
        blockHashes.add(allBlocks[allBlocks.length - 1].getHash()); // Request the forked block (E')...

        // Action
        queryBlocksHandler.run(blockHashes, new ImmutableSha256Hash(), bitcoinNode);

        // Assert
        final List<ProtocolMessage> sentMessages = bitcoinNode.getSentMessages();
        Assert.assertEquals(1, sentMessages.getCount());

        final InventoryMessage inventoryMessage = (InventoryMessage) (sentMessages.get(0));
        final List<InventoryItem> dataHashes = inventoryMessage.getInventoryItems();
        Assert.assertEquals(1, dataHashes.getCount());

        Assert.assertEquals(fPrimeBlock.getHash(), dataHashes.get(0).getItemHash());

        bitcoinNode.disconnect();
    }
}
