package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.DatabaseInitializer;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.database.mysql.embedded.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.properties.DatabaseProperties;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.NodeConnection;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.network.socket.BinarySocketServer;
import com.softwareverde.network.socket.JsonSocketServer;
import com.softwareverde.util.Container;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.RotatingQueue;
import com.softwareverde.util.Util;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NodeModule {
    public static void execute(final String configurationFileName) {
        final NodeModule nodeModule = new NodeModule(configurationFileName);
        nodeModule.loop();
    }

    class BlockValidatorThread extends Thread {
        @Override
        public void run() {
            while (true) {
                final Block block = _queuedBlocks.poll();
                if (block != null) {
                    final Boolean isValidBlock = _processBlock(block);

                    if (! isValidBlock) {
                        Logger.log("Invalid block: " + block.getHash());
                        _exitFailure();
                    }
                }
                else {
                    try { Thread.sleep(500L); } catch (final Exception exception) { break; }
                }
            }

            Logger.log("Block Validator Thread exiting...");
        }
    }

    protected final Configuration _configuration;
    protected final Environment _environment;
    protected final ReadUncommittedDatabaseConnectionPool _readUncommittedDatabaseConnectionPool;

    protected Boolean _hasGenesisBlock = false;

    protected final Integer _maxQueueSize;
    protected final ConcurrentLinkedQueue<Block> _queuedBlocks = new ConcurrentLinkedQueue<Block>();
    protected final BlockValidatorThread _blockValidatorThread = new BlockValidatorThread();

    protected final MutableMedianBlockTime _medianBlockTime;
    protected final BitcoinNodeManager _nodeManager;
    protected final BinarySocketServer _socketServer;
    protected final JsonSocketServer _jsonRpcSocketServer;

    protected final Object _statisticsMutex = new Object();
    protected final RotatingQueue<Long> _blocksPerSecond = new RotatingQueue<Long>(100);
    protected final RotatingQueue<Integer> _transactionsPerBlock = new RotatingQueue<Integer>(100);
    protected final Container<Float> _averageBlocksPerSecond = new Container<Float>(0F);
    protected final Container<Float> _averageTransactionsPerSecond = new Container<Float>(0F);

    protected final BitcoinNode.QueryBlocksCallback _queryBlocksCallback;
    protected final BitcoinNode.QueryBlockHeadersCallback _queryBlockHeadersCallback;

    protected void _exitFailure() {
        Logger.shutdown();
        System.exit(1);
    }

    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            _printError("Invalid configuration file.");
            _exitFailure();
        }

        return new Configuration(configurationFile);
    }

    protected void _downloadAllBlocks() {
        final EmbeddedMysqlDatabase database = _environment.getDatabase();

        final Sha256Hash resumeAfterHash;
        {
            Sha256Hash lastKnownHash = null;
            try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
                lastKnownHash = blockDatabaseManager.getHeadBlockHash();
            }
            catch (final DatabaseException e) { }

            resumeAfterHash = Util.coalesce(lastKnownHash, Block.GENESIS_BLOCK_HASH);
        }

        final Container<Sha256Hash> lastBlockHash = new Container<Sha256Hash>(resumeAfterHash);
        final Container<BitcoinNode.QueryCallback> getBlocksHashesAfterCallback = new Container<BitcoinNode.QueryCallback>();

        final MutableList<Sha256Hash> availableBlockHashes = new MutableList<Sha256Hash>();

        final BitcoinNode.DownloadBlockCallback downloadBlockCallback = new BitcoinNode.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {
                Logger.log("DOWNLOADED BLOCK: "+ HexUtil.toHexString(block.getHash().getBytes()));

                if (! lastBlockHash.value.equals(block.getPreviousBlockHash())) { return; } // Ignore blocks sent out of order...

                _queuedBlocks.add(block);
                Logger.log("Block Queue Size: "+ _queuedBlocks.size() + " / " + _maxQueueSize);

                lastBlockHash.value = block.getHash();

                while (_queuedBlocks.size() >= _maxQueueSize) {
                    try { Thread.sleep(500L); } catch (final Exception exception) { return; }
                }

                if (! availableBlockHashes.isEmpty()) {
                    _nodeManager.requestBlock(availableBlockHashes.remove(0), this);
                }
                else {
                    _nodeManager.requestBlockHashesAfter(lastBlockHash.value, getBlocksHashesAfterCallback.value);
                }
            }
        };

        getBlocksHashesAfterCallback.value = new BitcoinNode.QueryCallback() {
            @Override
            public void onResult(final java.util.List<Sha256Hash> blockHashes) {
                final List<Sha256Hash> hashes = new ImmutableList<Sha256Hash>(blockHashes); // TODO: Remove the conversion requirement. (Requires Constable.LinkedList)
                availableBlockHashes.addAll(hashes);

                if (! availableBlockHashes.isEmpty()) {
                    _nodeManager.requestBlock(availableBlockHashes.remove(0), downloadBlockCallback);
                }
            }
        };

        _nodeManager.requestBlockHashesAfter(lastBlockHash.value, getBlocksHashesAfterCallback.value);
    }

    protected Boolean _processBlock(final Block block) {
        final EmbeddedMysqlDatabase database = _environment.getDatabase();
        final NetworkTime networkTime = _nodeManager.getNetworkTime();

        try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
            TransactionUtil.startTransaction(databaseConnection);

            final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

            final BlockId blockId = blockDatabaseManager.storeBlock(block);
            blockChainDatabaseManager.updateBlockChainsForNewBlock(block);
            final BlockChainSegmentId blockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(blockId);

            final BlockValidator blockValidator = new BlockValidator(_readUncommittedDatabaseConnectionPool, networkTime, _medianBlockTime);
            final long blockValidationStartTime = System.currentTimeMillis();
            final Boolean blockIsValid = blockValidator.validateBlock(blockChainSegmentId, block);
            final long blockValidationEndTime = System.currentTimeMillis();
            final long blockValidationMsElapsed = (blockValidationEndTime - blockValidationStartTime);

            if (blockIsValid) {
                _medianBlockTime.addBlock(block);
                TransactionUtil.commitTransaction(databaseConnection);

                final Integer blockTransactionCount = block.getTransactions().getSize();

                final Float averageBlocksPerSecond;
                final Float averageTransactionsPerSecond;
                synchronized (_statisticsMutex) {
                    _blocksPerSecond.add(blockValidationMsElapsed);
                    _transactionsPerBlock.add(blockTransactionCount);

                    final Integer blockCount = _blocksPerSecond.size();
                    final Long validationTimeElapsed;
                    {
                        long value = 0L;
                        for (final Long elapsed : _blocksPerSecond) {
                            value += elapsed;
                        }
                        validationTimeElapsed = value;
                    }

                    final Integer totalTransactionCount;
                    {
                        int value = 0;
                        for (final Integer transactionCount : _transactionsPerBlock) {
                            value += transactionCount;
                        }
                        totalTransactionCount = value;
                    }

                    averageBlocksPerSecond = ( (blockCount.floatValue() / validationTimeElapsed.floatValue()) * 1000F );
                    averageTransactionsPerSecond = ( (totalTransactionCount.floatValue() / validationTimeElapsed.floatValue()) * 1000F );
                }

                _averageBlocksPerSecond.value = averageBlocksPerSecond;
                _averageTransactionsPerSecond.value = averageTransactionsPerSecond;

                return true;
            }
            else {
                TransactionUtil.rollbackTransaction(databaseConnection);
            }
        }
        catch (final Exception exception) {
            exception.printStackTrace();
        }

        Logger.log("Invalid block: "+ block.getHash());
        return false;
    }

    public NodeModule(final String configurationFilename) {
        _configuration = _loadConfigurationFile(configurationFilename);

        final Configuration.ServerProperties serverProperties = _configuration.getServerProperties();
        final DatabaseProperties databaseProperties = _configuration.getDatabaseProperties();

        final EmbeddedMysqlDatabase database;
        {
            EmbeddedMysqlDatabase databaseInstance = null;
            try {
                final DatabaseInitializer databaseInitializer = new DatabaseInitializer("queries/init.sql", Constants.DATABASE_VERSION, new DatabaseInitializer.DatabaseUpgradeHandler() {
                    @Override
                    public Boolean onUpgrade(final int currentVersion, final int requiredVersion) { return false; }
                });

                databaseInstance = new EmbeddedMysqlDatabase(databaseProperties, databaseInitializer);
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
            }
            database = databaseInstance;

            if (database != null) {
                Logger.log("[Database Online]");
            }
            else {
                _exitFailure();
            }
        }

        _maxQueueSize = serverProperties.getMaxBlockQueueSize();

        _environment = new Environment(database);

        final MysqlDatabaseConnectionFactory databaseConnectionFactory = database.getDatabaseConnectionFactory();
        _readUncommittedDatabaseConnectionPool = new ReadUncommittedDatabaseConnectionPool(databaseConnectionFactory);

        final Integer maxPeerCount = serverProperties.getMaxPeerCount();
        _nodeManager = new BitcoinNodeManager(maxPeerCount);

        _medianBlockTime = new MutableMedianBlockTime();
        { // Initialize _medianBlockTime with the N most recent blocks...
            try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

                Sha256Hash blockHash = Util.coalesce(blockDatabaseManager.getHeadBlockHash(), Block.GENESIS_BLOCK_HASH);
                for (int i = 0; i < MedianBlockTime.BLOCK_COUNT; ++i) {
                    final BlockId blockId = blockDatabaseManager.getBlockIdFromHash(blockHash);
                    if (blockId == null) { break; }

                    final BlockHeader blockHeader = blockDatabaseManager.getBlockHeader(blockId);
                    _medianBlockTime.addBlock(blockHeader);
                    blockHash = blockHeader.getPreviousBlockHash();
                }
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
                _exitFailure();
            }
        }

        _queryBlocksCallback = new BitcoinNode.QueryBlocksCallback() {
            @Override
            public void run(final List<Sha256Hash> blockHashes, final Sha256Hash desiredBlockHash, final NodeConnection nodeConnection) {
                Logger.log("NOTICE: QueryBlocks unimplemented.");
                // final EmbeddedMysqlDatabase mysqlDatabase = _environment.getDatabase();
                // try (final MysqlDatabaseConnection databaseConnection = mysqlDatabase.newConnection()) {
                //     // TODO...
                // }
                // catch (final DatabaseException exception) { Logger.log(exception); }
            }
        };

        _queryBlockHeadersCallback = new QueryBlockHeadersHandler(databaseConnectionFactory);

        for (final Configuration.SeedNodeProperties seedNodeProperties : serverProperties.getSeedNodeProperties()) {
            final BitcoinNode node = new BitcoinNode(seedNodeProperties.getAddress(), seedNodeProperties.getPort());
            node.setQueryBlocksCallback(_queryBlocksCallback);
            node.setQueryBlockHeadersCallback(_queryBlockHeadersCallback);
            node.connect();
            node.handshake();
            _nodeManager.addNode(node);
        }

        _socketServer = new BinarySocketServer(serverProperties.getBitcoinPort(), BitcoinProtocolMessage.BINARY_PACKET_FORMAT);
        _socketServer.setSocketConnectedCallback(new BinarySocketServer.SocketConnectedCallback() {
            @Override
            public void run(final BinarySocket binarySocket) {
                Logger.log("New Connection: " + binarySocket);
                final BitcoinNode node = new BitcoinNode(binarySocket);
                node.setQueryBlocksCallback(_queryBlocksCallback);
                node.setQueryBlockHeadersCallback(_queryBlockHeadersCallback);
                node.connect();
                node.handshake();
                _nodeManager.addNode(node);
            }
        });

        final JsonRpcSocketServerHandler.StatisticsContainer statisticsContainer = new JsonRpcSocketServerHandler.StatisticsContainer();
        statisticsContainer.averageBlocksPerSecond = _averageBlocksPerSecond;
        statisticsContainer.averageTransactionsPerSecond = _averageTransactionsPerSecond;

        final Thread mainThread = Thread.currentThread();
        final JsonRpcSocketServerHandler.ShutdownHandler shutdownHandler = new JsonRpcSocketServerHandler.ShutdownHandler() {
            @Override
            public void shutdown() {
                mainThread.interrupt();
            }
        };

        _jsonRpcSocketServer = new JsonSocketServer(8081);
        _jsonRpcSocketServer.setSocketConnectedCallback(new JsonRpcSocketServerHandler(_environment, shutdownHandler, statisticsContainer));
    }

    public void loop() {
        _nodeManager.startNodeMaintenanceThread();

        Logger.log("[Server Online]");

        _blockValidatorThread.start();
        Logger.log("[Block Validator Thread Online]");

        _jsonRpcSocketServer.start();
        Logger.log("[RPC Server Online]");

        _socketServer.start();
        Logger.log("[Listening For Connections]");

        final EmbeddedMysqlDatabase database = _environment.getDatabase();

        { // Determine if the Genesis Block has been downloaded...
            _hasGenesisBlock = false;
            try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
                final Sha256Hash lastKnownHash = blockDatabaseManager.getHeadBlockHash();
                _hasGenesisBlock = (lastKnownHash != null);
            }
            catch (final DatabaseException e) { }
        }

        if (! _hasGenesisBlock) {
            _nodeManager.requestBlock(Block.GENESIS_BLOCK_HASH, new BitcoinNode.DownloadBlockCallback() {
                @Override
                public void onResult(final Block block) {
                    if (_hasGenesisBlock) {
                        return; // NOTE: Can happen if the NodeModule received GenesisBlock from another node...
                    }

                    final Boolean isValidBlock = _processBlock(block);

                    if (isValidBlock) {
                        _downloadAllBlocks();
                    }
                }
            });
        }
        else {
            _downloadAllBlocks();
        }

        while (! Thread.currentThread().isInterrupted()) {
            try { Thread.sleep(5000); } catch (final Exception e) { break; }
        }

        _nodeManager.stopNodeMaintenanceThread();
        _blockValidatorThread.interrupt();
        _readUncommittedDatabaseConnectionPool.shutdown();
        _socketServer.stop();
        _jsonRpcSocketServer.stop();

        System.exit(0);
    }
}
