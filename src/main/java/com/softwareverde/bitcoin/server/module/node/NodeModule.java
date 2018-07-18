package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.module.node.handler.QueryBlockHeadersHandler;
import com.softwareverde.bitcoin.server.module.node.handler.QueryBlocksHandler;
import com.softwareverde.bitcoin.server.module.node.handler.RequestDataHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.QueryBalanceHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.ShutdownHandler;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.DatabaseCommandLineArguments;
import com.softwareverde.database.mysql.embedded.DatabaseInitializer;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.database.mysql.embedded.properties.DatabaseProperties;
import com.softwareverde.io.Logger;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.network.socket.BinarySocketServer;
import com.softwareverde.network.socket.JsonSocketServer;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.Container;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NodeModule {
    public static void execute(final String configurationFileName) {
        final NodeModule nodeModule = new NodeModule(configurationFileName);
        nodeModule.loop();
    }

    protected static class BlockValidatorThread extends Thread {
        protected final ConcurrentLinkedQueue<Block> _queuedBlocks;
        protected final BlockProcessor _blockProcessor;

        public BlockValidatorThread(final ConcurrentLinkedQueue<Block> queuedBlocks, final BlockProcessor blockProcessor) {
            _queuedBlocks = queuedBlocks;
            _blockProcessor = blockProcessor;
        }

        @Override
        public void run() {
            while (true) {
                final Block block = _queuedBlocks.poll();
                if (block != null) {
                    final Boolean isValidBlock = _blockProcessor.processBlock(block);

                    if (! isValidBlock) {
                        Logger.log("Invalid block: " + block.getHash());
                        BitcoinUtil.exitFailure();
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
    protected final BlockProcessor _blockProcessor;
    protected final BlockValidatorThread _blockValidatorThread;

    protected final MutableMedianBlockTime _medianBlockTime;
    protected final BitcoinNodeManager _nodeManager;
    protected final BinarySocketServer _socketServer;
    protected final JsonSocketServer _jsonRpcSocketServer;

    protected final NodeInitializer _nodeInitializer;

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            Logger.error("Invalid configuration file.");
            BitcoinUtil.exitFailure();
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

    protected NodeModule(final String configurationFilename) {
        final Thread mainThread = Thread.currentThread();

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

                final DatabaseCommandLineArguments commandLineArguments = new DatabaseCommandLineArguments();
                {
                    commandLineArguments.enableSlowQueryLog("slow-query.log", 1L);
                    commandLineArguments.setInnoDbBufferPoolByteCount(2L * ByteUtil.Unit.GIGABYTES);
                    commandLineArguments.setInnoDbBufferPoolInstanceCount(1);
                    commandLineArguments.setInnoDbLogFileByteCount(64 * ByteUtil.Unit.MEGABYTES);
                    commandLineArguments.setInnoDbLogBufferByteCount(8 * ByteUtil.Unit.MEGABYTES);
                    commandLineArguments.setQueryCacheByteCount(0L);
                    commandLineArguments.addArgument("--performance_schema");
                }

                final Properties connectionProperties = new Properties();
                {
                    connectionProperties.setProperty("dataSource.cachePrepStmts", "true");
                    connectionProperties.setProperty("dataSource.prepStmtCacheSize", "250");
                    connectionProperties.setProperty("dataSource.prepStmtCacheSqlLimit", "2048");
                    connectionProperties.setProperty("dataSource.useServerPrepStmts", "true");
                    connectionProperties.setProperty("dataSource.useLocalSessionState", "true");
                    connectionProperties.setProperty("dataSource.rewriteBatchedStatements", "true");
                    connectionProperties.setProperty("dataSource.cacheResultSetMetadata", "true");
                    connectionProperties.setProperty("dataSource.cacheServerConfiguration", "true");
                }

                databaseInstance = new EmbeddedMysqlDatabase(databaseProperties, databaseInitializer, commandLineArguments, connectionProperties);
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
            }
            database = databaseInstance;

            if (database != null) {
                Logger.log("[Database Online]");
            }
            else {
                BitcoinUtil.exitFailure();
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
                BitcoinUtil.exitFailure();
            }
        }

        {
            final QueryBlocksHandler queryBlocksHandler = new QueryBlocksHandler(databaseConnectionFactory);
            final QueryBlockHeadersHandler queryBlockHeadersHandler = new QueryBlockHeadersHandler(databaseConnectionFactory);
            final RequestDataHandler requestDataHandler = new RequestDataHandler(databaseConnectionFactory);
            _nodeInitializer = new NodeInitializer(queryBlocksHandler, queryBlockHeadersHandler, requestDataHandler);
        }


        for (final Configuration.SeedNodeProperties seedNodeProperties : serverProperties.getSeedNodeProperties()) {
            final String host = seedNodeProperties.getAddress();
            final Integer port = seedNodeProperties.getPort();

            final BitcoinNode node = _nodeInitializer.initializeNode(host, port);
            _nodeManager.addNode(node);
        }

        _socketServer = new BinarySocketServer(serverProperties.getBitcoinPort(), BitcoinProtocolMessage.BINARY_PACKET_FORMAT);
        _socketServer.setSocketConnectedCallback(new BinarySocketServer.SocketConnectedCallback() {
            @Override
            public void run(final BinarySocket binarySocket) {
                Logger.log("New Connection: " + binarySocket);
                final BitcoinNode node = _nodeInitializer.initializeNode(binarySocket);
                _nodeManager.addNode(node);
            }
        });

        _blockProcessor = new BlockProcessor(databaseConnectionFactory, _nodeManager, _medianBlockTime, _readUncommittedDatabaseConnectionPool);
        _blockValidatorThread = new BlockValidatorThread(_queuedBlocks, _blockProcessor);

        final JsonRpcSocketServerHandler.StatisticsContainer statisticsContainer = _blockProcessor.getStatisticsContainer();

        final Integer rpcPort = _configuration.getServerProperties().getBitcoinRpcPort();
        if (rpcPort > 0) {
            final JsonRpcSocketServerHandler.ShutdownHandler shutdownHandler = new ShutdownHandler(mainThread);
            final JsonRpcSocketServerHandler.NodeHandler nodeHandler = new NodeHandler(_nodeManager, _nodeInitializer);
            final JsonRpcSocketServerHandler.QueryBalanceHandler queryBalanceHandler = new QueryBalanceHandler(databaseConnectionFactory);

            final JsonSocketServer jsonRpcSocketServer = new JsonSocketServer(rpcPort);

            final JsonRpcSocketServerHandler rpcSocketServerHandler = new JsonRpcSocketServerHandler(_environment, statisticsContainer);
            rpcSocketServerHandler.setShutdownHandler(shutdownHandler);
            rpcSocketServerHandler.setNodeHandler(nodeHandler);
            rpcSocketServerHandler.setQueryBalanceHandler(queryBalanceHandler);

            jsonRpcSocketServer.setSocketConnectedCallback(rpcSocketServerHandler);
            _jsonRpcSocketServer = jsonRpcSocketServer;
        }
        else {
            _jsonRpcSocketServer = null;
        }
    }

    public void loop() {
        _nodeManager.startNodeMaintenanceThread();

        Logger.log("[Server Online]");

        _blockValidatorThread.start();
        Logger.log("[Block Validator Thread Online]");

        if (_jsonRpcSocketServer != null) {
            _jsonRpcSocketServer.start();
            Logger.log("[RPC Server Online]");
        }
        else {
            Logger.log("NOTICE: Bitcoin RPC Server not started.");
        }

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

                    final Boolean isValidBlock = _blockProcessor.processBlock(block);

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

        if (_jsonRpcSocketServer != null) {
            _jsonRpcSocketServer.stop();
        }

        System.exit(0);
    }
}
