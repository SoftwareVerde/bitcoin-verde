package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.QueryResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHash;
import com.softwareverde.bitcoin.server.module.node.handler.QueryBlockHeadersHandler;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.MysqlDatabaseConnectionFactory;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.node.NodeConnection;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class QueryBlockHeadersHandlerTests extends IntegrationTest {

    public static class FakeSocket extends Socket {
        public final ByteArrayInputStream inputStream;
        public final ByteArrayOutputStream outputStream;

        public FakeSocket() {
            inputStream = new ByteArrayInputStream(new byte[0]);
            outputStream = new ByteArrayOutputStream();
        }

        public FakeSocket(final byte[] inputBytes) {
            inputStream = new ByteArrayInputStream(inputBytes);
            outputStream = new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return this.inputStream;
        }

        @Override
        public OutputStream getOutputStream() {
            return this.outputStream;
        }
    }

    public static class FakeBinarySocket extends BinarySocket {
        public final FakeSocket fakeSocket;

        public FakeBinarySocket(final FakeSocket fakeSocket) {
            super(fakeSocket, BitcoinProtocolMessage.BINARY_PACKET_FORMAT);
            this.fakeSocket = fakeSocket;
        }

        @Override
        public String getHost() {
            return "";
        }

        @Override
        public Integer getPort() {
            return 0;
        }
    }

    public static class FakeNodeConnection extends NodeConnection {
        public final FakeBinarySocket fakeBinarySocket;

        public FakeNodeConnection(final FakeBinarySocket fakeBinarySocket) {
            super(fakeBinarySocket);
            this.fakeBinarySocket = fakeBinarySocket;
        }

        public List<ProtocolMessage> getSentMessages() {
            try { Thread.sleep(500L); } catch (final Exception e) { } // Required to wait for messageQueue...

            final MutableList<ProtocolMessage> protocolMessages = new MutableList<ProtocolMessage>();

            while (! _outboundMessageQueue.isEmpty()) {
                protocolMessages.add(_outboundMessageQueue.removeLast());
            }

            return protocolMessages;
        }
    }

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
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockInflater blockInflater = new BlockInflater();

        final String[] blockDatas = new String[]{ BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2, BlockData.MainChain.BLOCK_3, BlockData.MainChain.BLOCK_4 };
        final Block[] blocks = new Block[blockDatas.length];
        int i = 0;
        for (final String blockData : blockDatas) {
            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
            blockDatabaseManager.insertBlock(block);
            blockChainDatabaseManager.updateBlockChainsForNewBlock(block);
            blocks[i] = block;
            i += 1;
        }

        return blocks;
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
    protected static Block[] _initScenario2(final Block[] blocks) throws Exception {
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockInflater blockInflater = new BlockInflater();

        final Block block5 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_5));
        blockDatabaseManager.insertBlock(block5);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block5);

        final Block forkedBlock0 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain3.BLOCK_2));
        blockDatabaseManager.insertBlock(forkedBlock0);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(forkedBlock0);

        final Block forkedBlock1; // NOTE: Has an invalid hash, but shouldn't matter...
        {
            final MutableBlock mutableBlock = new MutableBlock(blocks[blocks.length - 1]);
            mutableBlock.setNonce(mutableBlock.getNonce() + 1);
            forkedBlock1 = mutableBlock;
        }
        blockDatabaseManager.insertBlock(forkedBlock1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(forkedBlock1);

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

    @Before
    public void setup() {
        _resetDatabase();
    }

    @Test
    public void should_return_genesis_blocks_when_no_matches() throws Exception {
        // Setup
        final Block[] scenarioBlocks = _initScenario();
        final Block[] allBlocks = _initScenario2(scenarioBlocks);

        final Integer bestChainHeight = scenarioBlocks.length + 1;
        final Block[] mainChainBlocks = new Block[bestChainHeight];
        for (int i = 0; i < scenarioBlocks.length + 1; ++i) { mainChainBlocks[i] = allBlocks[i]; }

        final MysqlDatabaseConnectionFactory databaseConnectionFactory = _database.getDatabaseConnectionFactory();
        final QueryBlockHeadersHandler queryBlockHeadersHandler = new QueryBlockHeadersHandler(databaseConnectionFactory);

        final FakeNodeConnection fakeNodeConnection = new FakeNodeConnection(new FakeBinarySocket(new FakeSocket()));

        final Integer blockOffset = 0; // The block header/offset that is provided as the last known header...

        final List<Sha256Hash> blockHashes = new MutableList<Sha256Hash>();

        // Action
        queryBlockHeadersHandler.run(blockHashes, new ImmutableSha256Hash(), fakeNodeConnection);

        // Assert
        final List<ProtocolMessage> sentMessages = fakeNodeConnection.getSentMessages();
        Assert.assertEquals(1, sentMessages.getSize());

        final QueryResponseMessage queryResponseMessage = (QueryResponseMessage) (sentMessages.get(0));
        final List<DataHash> dataHashes = queryResponseMessage.getDataHashes();
        Assert.assertEquals(bestChainHeight - blockOffset, dataHashes.getSize());

        int i = blockOffset;
        for (final DataHash dataHash : dataHashes) {
            Assert.assertEquals(mainChainBlocks[i].getHash(), dataHash.getObjectHash());
            i += 1;
        }

        fakeNodeConnection.disconnect();
    }

    @Test
    public void should_return_genesis_blocks_when_match_found() throws Exception {
        // Setup
        final Block[] scenarioBlocks = _initScenario();
        final Block[] allBlocks = _initScenario2(scenarioBlocks);

        final Integer bestChainHeight = scenarioBlocks.length + 1;
        final Block[] mainChainBlocks = new Block[bestChainHeight];
        for (int i = 0; i < scenarioBlocks.length + 1; ++i) { mainChainBlocks[i] = allBlocks[i]; }

        final MysqlDatabaseConnectionFactory databaseConnectionFactory = _database.getDatabaseConnectionFactory();
        final QueryBlockHeadersHandler queryBlockHeadersHandler = new QueryBlockHeadersHandler(databaseConnectionFactory);

        final FakeNodeConnection fakeNodeConnection = new FakeNodeConnection(new FakeBinarySocket(new FakeSocket()));

        final Integer blockOffset = 0; // The block header/offset that is provided as the last known header...

        final MutableList<Sha256Hash> blockHashes = new MutableList<Sha256Hash>();
        blockHashes.add(allBlocks[blockOffset].getHash());

        // Action
        queryBlockHeadersHandler.run(blockHashes, new ImmutableSha256Hash(), fakeNodeConnection);

        // Assert
        final List<ProtocolMessage> sentMessages = fakeNodeConnection.getSentMessages();
        Assert.assertEquals(1, sentMessages.getSize());

        final QueryResponseMessage queryResponseMessage = (QueryResponseMessage) (sentMessages.get(0));
        final List<DataHash> dataHashes = queryResponseMessage.getDataHashes();
        Assert.assertEquals(bestChainHeight - blockOffset, dataHashes.getSize());

        int i = blockOffset;
        for (final DataHash dataHash : dataHashes) {
            Assert.assertEquals(mainChainBlocks[i].getHash(), dataHash.getObjectHash());
            i += 1;
        }

        fakeNodeConnection.disconnect();
    }

    @Test
    public void should_return_genesis_blocks_when_non_genesis_match_found() throws Exception {
        // Setup
        final Block[] scenarioBlocks = _initScenario();
        final Block[] allBlocks = _initScenario2(scenarioBlocks);

        final Integer bestChainHeight = scenarioBlocks.length + 1;
        final Block[] mainChainBlocks = new Block[bestChainHeight];
        for (int i = 0; i < scenarioBlocks.length + 1; ++i) { mainChainBlocks[i] = allBlocks[i]; }

        final MysqlDatabaseConnectionFactory databaseConnectionFactory = _database.getDatabaseConnectionFactory();
        final QueryBlockHeadersHandler queryBlockHeadersHandler = new QueryBlockHeadersHandler(databaseConnectionFactory);

        final FakeNodeConnection fakeNodeConnection = new FakeNodeConnection(new FakeBinarySocket(new FakeSocket()));

        final Integer blockOffset = 1; // The block header/offset that is provided as the last known header...

        final MutableList<Sha256Hash> blockHashes = new MutableList<Sha256Hash>();
        blockHashes.add(allBlocks[blockOffset].getHash());

        // Action
        queryBlockHeadersHandler.run(blockHashes, new ImmutableSha256Hash(), fakeNodeConnection);

        // Assert
        final List<ProtocolMessage> sentMessages = fakeNodeConnection.getSentMessages();
        Assert.assertEquals(1, sentMessages.getSize());

        final QueryResponseMessage queryResponseMessage = (QueryResponseMessage) (sentMessages.get(0));
        final List<DataHash> dataHashes = queryResponseMessage.getDataHashes();
        Assert.assertEquals(bestChainHeight - blockOffset, dataHashes.getSize());

        int i = blockOffset;
        for (final DataHash dataHash : dataHashes) {
            Assert.assertEquals(mainChainBlocks[i].getHash(), dataHash.getObjectHash());
            i += 1;
        }

        fakeNodeConnection.disconnect();
    }

    @Test
    public void should_return_blocks_when_match_found() throws Exception {
        // Setup
        final Block[] scenarioBlocks = _initScenario();
        final Block[] allBlocks = _initScenario2(scenarioBlocks);

        final Integer bestChainHeight = scenarioBlocks.length + 1;
        final Block[] mainChainBlocks = new Block[bestChainHeight];
        for (int i = 0; i < scenarioBlocks.length + 1; ++i) { mainChainBlocks[i] = allBlocks[i]; }

        final MysqlDatabaseConnectionFactory databaseConnectionFactory = _database.getDatabaseConnectionFactory();
        final QueryBlockHeadersHandler queryBlockHeadersHandler = new QueryBlockHeadersHandler(databaseConnectionFactory);

        final FakeNodeConnection fakeNodeConnection = new FakeNodeConnection(new FakeBinarySocket(new FakeSocket()));

        final Integer blockOffset = 2; // The block header/offset that is provided as the last known header...

        final MutableList<Sha256Hash> blockHashes = new MutableList<Sha256Hash>();
        blockHashes.add(allBlocks[blockOffset].getHash());

        // Action
        queryBlockHeadersHandler.run(blockHashes, new ImmutableSha256Hash(), fakeNodeConnection);

        // Assert
        final List<ProtocolMessage> sentMessages = fakeNodeConnection.getSentMessages();
        Assert.assertEquals(1, sentMessages.getSize());

        final QueryResponseMessage queryResponseMessage = (QueryResponseMessage) (sentMessages.get(0));
        final List<DataHash> dataHashes = queryResponseMessage.getDataHashes();
        Assert.assertEquals(bestChainHeight - blockOffset, dataHashes.getSize());

        int i = blockOffset;
        for (final DataHash dataHash : dataHashes) {
            Assert.assertEquals(mainChainBlocks[i].getHash(), dataHash.getObjectHash());
            i += 1;
        }

        fakeNodeConnection.disconnect();
    }

    @Test
    public void should_return_forked_blocks_when_match_found() throws Exception {
        // Setup
        final Block[] scenarioBlocks = _initScenario();
        final Block[] allBlocks = _initScenario2(scenarioBlocks);

        final Block extraChildEPrimeBlock;
        { // Create an additional child onto block E'...
            final MutableBlock mutableBlock = new MutableBlock(allBlocks[allBlocks.length - 1]);
            mutableBlock.setPreviousBlockHash(allBlocks[allBlocks.length - 1].getHash());

            final MysqlDatabaseConnection databaseConnection = _database.newConnection();
            final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
            blockDatabaseManager.insertBlock(mutableBlock);
            blockChainDatabaseManager.updateBlockChainsForNewBlock(mutableBlock);

            extraChildEPrimeBlock = mutableBlock;
        }

        final Integer bestChainHeight = scenarioBlocks.length + 1;
        final Block[] mainChainBlocks = new Block[bestChainHeight];
        for (int i = 0; i < scenarioBlocks.length + 1; ++i) { mainChainBlocks[i] = allBlocks[i]; }

        final MysqlDatabaseConnectionFactory databaseConnectionFactory = _database.getDatabaseConnectionFactory();
        final QueryBlockHeadersHandler queryBlockHeadersHandler = new QueryBlockHeadersHandler(databaseConnectionFactory);

        final FakeNodeConnection fakeNodeConnection = new FakeNodeConnection(new FakeBinarySocket(new FakeSocket()));

        final MutableList<Sha256Hash> blockHashes = new MutableList<Sha256Hash>();
        blockHashes.add(allBlocks[allBlocks.length - 1].getHash()); // Request the forked block (E')...

        // Action
        queryBlockHeadersHandler.run(blockHashes, new ImmutableSha256Hash(), fakeNodeConnection);

        // Assert
        final List<ProtocolMessage> sentMessages = fakeNodeConnection.getSentMessages();
        Assert.assertEquals(1, sentMessages.getSize());

        final QueryResponseMessage queryResponseMessage = (QueryResponseMessage) (sentMessages.get(0));
        final List<DataHash> dataHashes = queryResponseMessage.getDataHashes();
        Assert.assertEquals(2, dataHashes.getSize());

        Assert.assertEquals(allBlocks[allBlocks.length - 1].getHash(), dataHashes.get(dataHashes.getSize() - 2).getObjectHash());
        Assert.assertEquals(extraChildEPrimeBlock.getHash(), dataHashes.get(dataHashes.getSize() - 1).getObjectHash());

        fakeNodeConnection.disconnect();
    }
}
