package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.NodeManager;
import com.softwareverde.bitcoin.server.network.NetworkTime;
import com.softwareverde.bitcoin.server.node.Node;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.DatabaseInitializer;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.database.mysql.embedded.factory.ReadUncommittedDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.properties.DatabaseProperties;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.HexUtil;

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
    protected final NetworkTime _networkTime;

    protected Boolean _hasGenesisBlock = false;

    protected final Integer _maxQueueSize = 10;
    protected final ConcurrentLinkedQueue<Block> _queuedBlocks = new ConcurrentLinkedQueue<Block>();
    protected final BlockValidatorThread _blockValidatorThread = new BlockValidatorThread();

    protected long _startTime;
    protected int _blockCount = 0;
    protected int _transactionCount = 0;

    protected final NodeManager _nodeManager;

    protected void _exitFailure() {
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
                lastKnownHash = blockDatabaseManager.getMostRecentBlockHash();
            }
            catch (final DatabaseException e) { }

            resumeAfterHash = ((lastKnownHash == null) ? Block.GENESIS_BLOCK_HEADER_HASH : lastKnownHash);
        }

        final Container<Sha256Hash> lastBlockHash = new Container<Sha256Hash>(resumeAfterHash);
        final Container<Node.QueryCallback> getBlocksHashesAfterCallback = new Container<Node.QueryCallback>();

        final MutableList<Sha256Hash> availableBlockHashes = new MutableList<Sha256Hash>();

        final Node.DownloadBlockCallback downloadBlockCallback = new Node.DownloadBlockCallback() {
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

        getBlocksHashesAfterCallback.value = new Node.QueryCallback() {
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

        try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
            TransactionUtil.startTransaction(databaseConnection);

            final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

            final BlockId blockId = blockDatabaseManager.storeBlock(block);
            blockChainDatabaseManager.updateBlockChainsForNewBlock(block);
            final BlockChainSegmentId blockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(blockId);

            final ReadUncommittedDatabaseConnectionFactory connectionFactory = new ReadUncommittedDatabaseConnectionFactory(database.getDatabaseConnectionFactory());
            final BlockValidator blockValidator = new BlockValidator(connectionFactory);
            final Boolean blockIsValid = blockValidator.validateBlock(blockChainSegmentId, block);

            if (blockIsValid) {
                TransactionUtil.commitTransaction(databaseConnection);
                _blockCount += 1;
                _transactionCount += block.getTransactions().getSize();

                final long msElapsed = (System.currentTimeMillis() - _startTime);
                Logger.log("Processed "+ _transactionCount + " transactions in " + msElapsed +" ms. (" + String.format("%.2f", ((((double) _transactionCount) / msElapsed) * 1000)) + " tps)");
                Logger.log("Processed "+ _blockCount + " blocks in " + msElapsed +" ms. (" + String.format("%.2f", ((((double) _blockCount) / msElapsed) * 1000)) + " bps)");

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
                _exitFailure();
            }
            database = databaseInstance;
            Logger.log("[Database Online]");
        }

        _environment = new Environment(database);
        _networkTime = new NetworkTime();

        final Integer maxPeerCount = serverProperties.getMaxPeerCount();
        _nodeManager = new NodeManager(maxPeerCount);

        for (final Configuration.SeedNodeProperties seedNodeProperties : serverProperties.getSeedNodeProperties()) {
            final Node node = new Node(seedNodeProperties.getAddress(), seedNodeProperties.getPort());
            _nodeManager.addNode(node);
        }
    }

    public void loop() {
        _nodeManager.startNodeMaintenanceThread();

        Logger.log("[Server Online]");

        _blockValidatorThread.start();
        Logger.log("[Block Validator Thread Online]");

        final EmbeddedMysqlDatabase database = _environment.getDatabase();

        _startTime = System.currentTimeMillis();

        { // Determine if the Genesis Block has been downloaded...
            _hasGenesisBlock = false;
            try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
                final Sha256Hash lastKnownHash = blockDatabaseManager.getMostRecentBlockHash();
                _hasGenesisBlock = (lastKnownHash != null);
            }
            catch (final DatabaseException e) { }
        }

        if (! _hasGenesisBlock) {
            _nodeManager.requestBlock(Block.GENESIS_BLOCK_HEADER_HASH, new Node.DownloadBlockCallback() {
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

        while (true) {
            try { Thread.sleep(5000); } catch (final Exception e) { break; }
        }
    }
}
