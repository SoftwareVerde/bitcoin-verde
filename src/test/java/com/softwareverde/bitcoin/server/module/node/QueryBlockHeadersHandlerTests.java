package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.QueryResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHash;
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

    protected Block[] _initScenario() throws Exception {
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockInflater blockInflater = new BlockInflater();

        final Block[] blocks = new Block[6];
        int i = 0;
        for (final String blockData : new String[]{ BlockData.MainChain.GENESIS_BLOCK, BlockData.MainChain.BLOCK_1, BlockData.MainChain.BLOCK_2, BlockData.MainChain.BLOCK_3, BlockData.MainChain.BLOCK_4, BlockData.MainChain.BLOCK_5 }) {
            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
            blockDatabaseManager.storeBlock(block);
            blockChainDatabaseManager.updateBlockChainsForNewBlock(block);
            blocks[i] = block;
            i += 1;
        }

        return blocks;
    }

    protected static Block[] _initScenario2(final Block[] blocks) throws Exception {
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockInflater blockInflater = new BlockInflater();

        final Block forkedBlock0 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain3.BLOCK_2));
        blockDatabaseManager.storeBlock(forkedBlock0);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(forkedBlock0);

        final Block forkedBlock1; // NOTE: Has an invalid hash, but shouldn't matter...
        {
            final MutableBlock mutableBlock = new MutableBlock(blocks[blocks.length - 1]);
            mutableBlock.setNonce(mutableBlock.getNonce() + 1);
            forkedBlock1 = mutableBlock;
        }
        blockDatabaseManager.storeBlock(forkedBlock1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(forkedBlock1);

        final Block[] newBlocks = new Block[blocks.length + 2];
        for (int i = 0; i < blocks.length; ++i) {
            newBlocks[i] = blocks[i];
        }
        newBlocks[blocks.length + 0] = forkedBlock0;
        newBlocks[blocks.length + 1] = forkedBlock1;
        return newBlocks;
    }

    @Before
    public void setup() {
        _resetDatabase();
    }

    @Test
    public void should_return_genesis_blocks_when_no_matches() throws Exception {
        // Setup
        final Block[] blocks = (_initScenario());

        final MysqlDatabaseConnectionFactory databaseConnectionFactory = _database.getDatabaseConnectionFactory();
        final QueryBlockHeadersHandler queryBlockHeadersHandler = new QueryBlockHeadersHandler(databaseConnectionFactory);

        final FakeNodeConnection fakeNodeConnection = new FakeNodeConnection(new FakeBinarySocket(new FakeSocket()));

        final List<Sha256Hash> blockHashes = new MutableList<Sha256Hash>();

        // Action
        queryBlockHeadersHandler.run(blockHashes, new ImmutableSha256Hash(), fakeNodeConnection);

        // Assert
        final List<ProtocolMessage> sentMessages = fakeNodeConnection.getSentMessages();
        Assert.assertEquals(1, sentMessages.getSize());

        final QueryResponseMessage queryResponseMessage = (QueryResponseMessage) (sentMessages.get(0));
        final List<DataHash> dataHashes = queryResponseMessage.getDataHashes();
        Assert.assertEquals(blocks.length, dataHashes.getSize());

        int i = 0;
        for (final DataHash dataHash : dataHashes) {
            Assert.assertEquals(blocks[i].getHash(), dataHash.getObjectHash());
            i += 1;
        }

        fakeNodeConnection.disconnect();
    }

    @Test
    public void should_return_genesis_blocks_when_match_found() throws Exception {
        // Setup
        final Block[] blocks = (_initScenario());

        final MysqlDatabaseConnectionFactory databaseConnectionFactory = _database.getDatabaseConnectionFactory();
        final QueryBlockHeadersHandler queryBlockHeadersHandler = new QueryBlockHeadersHandler(databaseConnectionFactory);

        final FakeNodeConnection fakeNodeConnection = new FakeNodeConnection(new FakeBinarySocket(new FakeSocket()));

        final MutableList<Sha256Hash> blockHashes = new MutableList<Sha256Hash>();
        blockHashes.add(Block.GENESIS_BLOCK_HEADER_HASH);

        // Action
        queryBlockHeadersHandler.run(blockHashes, new ImmutableSha256Hash(), fakeNodeConnection);

        // Assert
        final List<ProtocolMessage> sentMessages = fakeNodeConnection.getSentMessages();
        Assert.assertEquals(1, sentMessages.getSize());

        final QueryResponseMessage queryResponseMessage = (QueryResponseMessage) (sentMessages.get(0));
        final List<DataHash> dataHashes = queryResponseMessage.getDataHashes();
        Assert.assertEquals(blocks.length, dataHashes.getSize());

        int i = 0;
        for (final DataHash dataHash : dataHashes) {
            Assert.assertEquals(blocks[i].getHash(), dataHash.getObjectHash());
            i += 1;
        }

        fakeNodeConnection.disconnect();
    }

    @Test
    public void should_return_genesis_blocks_when_non_genesis_match_found() throws Exception {
        // Setup
        final Block[] blocks = (_initScenario());

        final MysqlDatabaseConnectionFactory databaseConnectionFactory = _database.getDatabaseConnectionFactory();
        final QueryBlockHeadersHandler queryBlockHeadersHandler = new QueryBlockHeadersHandler(databaseConnectionFactory);

        final FakeNodeConnection fakeNodeConnection = new FakeNodeConnection(new FakeBinarySocket(new FakeSocket()));

        final MutableList<Sha256Hash> blockHashes = new MutableList<Sha256Hash>();
        blockHashes.add(blocks[1].getHash());

        // Action
        queryBlockHeadersHandler.run(blockHashes, new ImmutableSha256Hash(), fakeNodeConnection);

        // Assert
        final List<ProtocolMessage> sentMessages = fakeNodeConnection.getSentMessages();
        Assert.assertEquals(1, sentMessages.getSize());

        final QueryResponseMessage queryResponseMessage = (QueryResponseMessage) (sentMessages.get(0));
        final List<DataHash> dataHashes = queryResponseMessage.getDataHashes();
        Assert.assertEquals(blocks.length - 1, dataHashes.getSize());

        int i = 1;
        for (final DataHash dataHash : dataHashes) {
            Assert.assertEquals(blocks[i].getHash(), dataHash.getObjectHash());
            i += 1;
        }

        fakeNodeConnection.disconnect();
    }
}
