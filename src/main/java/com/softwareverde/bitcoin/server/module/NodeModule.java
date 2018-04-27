package com.softwareverde.bitcoin.server.module;

import com.softwareverde.async.HaltableThread;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddress;
import com.softwareverde.bitcoin.server.network.NetworkTime;
import com.softwareverde.bitcoin.server.node.Node;
import com.softwareverde.bitcoin.server.node.NodeId;
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
import java.util.HashMap;
import java.util.Map;

public class NodeModule {
    protected final Configuration _configuration;
    protected final Environment _environment;
    protected final NetworkTime _networkTime;

    protected final Map<NodeId, Node> _nodes = new HashMap<NodeId, Node>();

    protected final Object _mutex = new Object();
    protected Boolean _hasGenesisBlock = false;

    protected long _startTime;
    protected int _blockCount = 0;
    protected int _transactionCount = 0;

    protected final MutableList<Runnable> _onNodeConnectedCallbacks = new MutableList<Runnable>();

    protected final HaltableThread _nodeMaintenanceThread = new HaltableThread(new Runnable() {
        @Override
        public void run() {
            _pingIdleNodes();
        }
    });

    protected void _exitFailure() {
        System.exit(1);
    }

    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected void _pingIdleNodes() {
        final Long maxIdleTime = 30000L;

        final Long now = System.currentTimeMillis();

        final MutableList<Node> idleNodes;
        synchronized (_mutex) {
            idleNodes = new MutableList<Node>(_nodes.size());
            for (final Node node : _nodes.values()) {
                final Long lastMessageTime = node.getLastMessageReceivedTimestamp();
                final Long idleDuration = (now - lastMessageTime); // NOTE: Race conditions could result in a negative value...

                if (idleDuration > maxIdleTime) {
                    idleNodes.add(node);
                }
            }
        }

        Logger.log("Idle Node Count: " + idleNodes.getSize() + " / " + _nodes.size());
        for (final Node idleNode : idleNodes) {
            idleNode.ping();
            Logger.log("*** Pinging Idle Node: " + idleNode.getConnectionString());
        }
    }

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            _printError("Invalid configuration file.");
            _exitFailure();
        }

        return new Configuration(configurationFile);
    }

    protected void _addOnNodeConnectedCallback(final Runnable runnable) {
        synchronized (_mutex) {
            _onNodeConnectedCallbacks.add(runnable);
        }
    }

    protected Node _selectNode() {
        final MutableList<Node> activeNodes;
        synchronized (_mutex) {
            activeNodes = new MutableList<Node>(_nodes.size());
            for (final Node node : _nodes.values()) {
                if (node.hasActiveConnection()) {
                    activeNodes.add(node);
                }
            }
        }

        final Integer activeNodeCount = activeNodes.getSize();
        if (activeNodeCount == 0) { return null; }

        final Integer selectedNodeIndex = ( ((int) (Math.random() * 7777)) % activeNodeCount );

        Logger.log("Select Node: " + selectedNodeIndex + " - " + activeNodeCount + " / " + _nodes.size());

        return activeNodes.get(selectedNodeIndex);
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

        final Node node = _selectNode();
        if (node == null) {
            Logger.log("No nodes connected!");

            _addOnNodeConnectedCallback(new Runnable() {
                @Override
                public void run() {
                    _downloadAllBlocks();
                }
            });

            return;
        }

        final Node.DownloadBlockCallback downloadBlockCallback = new Node.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {
                Logger.log("Node: " + node.getConnectionString() + " - DOWNLOADED BLOCK: "+ HexUtil.toHexString(block.getHash().getBytes()));

                if (! lastBlockHash.value.equals(block.getPreviousBlockHash())) { return; } // Ignore blocks sent out of order...

                final Boolean isValidBlock = _processBlock(block);

                if (! isValidBlock) {
                    Logger.log("Invalid block: "+ block.getHash());
                    _exitFailure();
                }

                lastBlockHash.value = block.getHash();

                final Node nextNode = _selectNode();
                if (nextNode == null) {
                    Logger.log("No nodes connected!");

                    _addOnNodeConnectedCallback(new Runnable() {
                        @Override
                        public void run() {
                            _downloadAllBlocks();
                        }
                    });

                    return;
                }

                if (! availableBlockHashes.isEmpty()) {
                    nextNode.requestBlock(availableBlockHashes.remove(0), this);
                }
                else {
                    nextNode.getBlockHashesAfter(lastBlockHash.value, getBlocksHashesAfterCallback.value);
                }
            }
        };

        getBlocksHashesAfterCallback.value = new Node.QueryCallback() {
            @Override
            public void onResult(final java.util.List<Sha256Hash> blockHashes) {
                final List<Sha256Hash> hashes = new ImmutableList<Sha256Hash>(blockHashes); // TODO: Remove the conversion requirement. (Requires Constable.LinkedList)
                availableBlockHashes.addAll(hashes);

                if (! availableBlockHashes.isEmpty()) {
                    node.requestBlock(availableBlockHashes.remove(0), downloadBlockCallback);
                }
            }
        };

        node.getBlockHashesAfter(lastBlockHash.value, getBlocksHashesAfterCallback.value);
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

    protected void _initNode(final Node node) {
        node.setNodeAddressesReceivedCallback(new Node.NodeAddressesReceivedCallback() {
            @Override
            public void onNewNodeAddress(final NodeIpAddress nodeIpAddress) {
                final String address = nodeIpAddress.getIp().toString();
                final Integer port = nodeIpAddress.getPort();
                final String connectionString = (address + ":" + port);

                synchronized (_mutex) {
                    for (final Node existingNode : _nodes.values()) {
                        final Boolean isAlreadyConnectedToNode = (existingNode.getConnectionString().equals(connectionString));
                        if (isAlreadyConnectedToNode) {
                            return;
                        }
                    }

                    final Node newNode = new Node(address, port);
                    _initNode(newNode);
                }
            }
        });

        node.setNodeConnectedCallback(new Node.NodeConnectedCallback() {
            @Override
            public void onNodeConnected() {
                synchronized (_mutex) {
                    for (final Runnable runnable : _onNodeConnectedCallbacks) {
                        new Thread(runnable).start();
                    }
                    _onNodeConnectedCallbacks.clear();
                }
            }
        });

        _nodes.put(node.getId(), node);
    }

    public NodeModule(final String configurationFilename) {
        _nodeMaintenanceThread.setSleepTime(10000L);

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

        synchronized (_mutex) {
            for (final Configuration.SeedNodeProperties seedNodeProperties : serverProperties.getSeedNodeProperties()) {
                final Node node = new Node(seedNodeProperties.getAddress(), seedNodeProperties.getPort());
                _initNode(node);
            }
        }
    }

    public void loop() {
        Logger.log("[Server Online]");

        final EmbeddedMysqlDatabase database = _environment.getDatabase();

        _startTime = System.currentTimeMillis();

        {
            Sha256Hash lastKnownHash = null;
            try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
                lastKnownHash = blockDatabaseManager.getMostRecentBlockHash();
            }
            catch (final DatabaseException e) { }
            synchronized (_mutex) {
                _hasGenesisBlock = (lastKnownHash != null);
            }
        }

        synchronized (_mutex) {
            if (! _hasGenesisBlock) {
                for (final Node node : _nodes.values()) {
                    node.requestBlock(Block.GENESIS_BLOCK_HEADER_HASH, new Node.DownloadBlockCallback() {
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
            }
            else {
                _downloadAllBlocks();
            }
        }

        _nodeMaintenanceThread.start();

        while (true) {
            try { Thread.sleep(5000); } catch (final Exception e) { break; }
        }
    }

    public static void execute(final String configurationFileName) {
        final NodeModule nodeModule = new NodeModule(configurationFileName);
        nodeModule.loop();
    }
}