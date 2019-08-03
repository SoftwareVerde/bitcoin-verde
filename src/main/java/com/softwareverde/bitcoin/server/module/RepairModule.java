package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.configuration.SeedNodeProperties;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.ReadOnlyLocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.handler.BlockInventoryMessageHandler;
import com.softwareverde.bitcoin.server.module.node.handler.RequestDataHandler;
import com.softwareverde.bitcoin.server.module.node.handler.SynchronizationStatusHandler;
import com.softwareverde.bitcoin.server.module.node.handler.block.QueryBlockHeadersHandler;
import com.softwareverde.bitcoin.server.module.node.handler.block.QueryBlocksHandler;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.QueryUnconfirmedTransactionsHandler;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.TransactionInventoryMessageHandlerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.NodeInitializer;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.concurrent.pool.ThreadPoolFactory;
import com.softwareverde.concurrent.pool.ThreadPoolThrottle;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Util;

public class RepairModule {
    protected final BitcoinProperties _bitcoinProperties;
    protected final Environment _environment;
    protected final List<Sha256Hash> _blockHashes;

    protected final NodeInitializer _nodeInitializer;
    protected final MainThreadPool _threadPool = new MainThreadPool(256, 10000L);

    public RepairModule(BitcoinProperties bitcoinProperties, final Environment environment, final String[] blockHashes) {
        _bitcoinProperties = bitcoinProperties;
        _environment = environment;

        final ImmutableListBuilder<Sha256Hash> blockHashesBuilder = new ImmutableListBuilder<Sha256Hash>(blockHashes.length);
        for (final String blockHashString : blockHashes) {
            final Sha256Hash blockHash = Sha256Hash.fromHexString(blockHashString);
            if (blockHash != null) {
                blockHashesBuilder.add(blockHash);
            }
        }
        _blockHashes = blockHashesBuilder.build();

        final Database database = _environment.getDatabase();
        final MasterDatabaseManagerCache masterDatabaseManagerCache = _environment.getMasterDatabaseManagerCache();
        final DatabaseManagerCache databaseManagerCache = new ReadOnlyLocalDatabaseManagerCache(masterDatabaseManagerCache);

        final LocalNodeFeatures localNodeFeatures = new LocalNodeFeatures() {
            @Override
            public NodeFeatures getNodeFeatures() {
                final NodeFeatures nodeFeatures = new NodeFeatures();
                nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                return nodeFeatures;
            }
        };

        { // Initialize NodeInitializer...
            final DatabaseConnectionFactory databaseConnectionFactory = database.newConnectionFactory();
            final DatabaseManagerFactory databaseManagerFactory = new FullNodeDatabaseManagerFactory(databaseConnectionFactory, databaseManagerCache);

            final NodeInitializer.Properties nodeInitializerProperties = new NodeInitializer.Properties();
            nodeInitializerProperties.synchronizationStatus = new SynchronizationStatusHandler(databaseManagerFactory);
            nodeInitializerProperties.queryBlocksCallback = QueryBlocksHandler.IGNORE_REQUESTS_HANDLER;
            nodeInitializerProperties.queryBlockHeadersCallback = QueryBlockHeadersHandler.IGNORES_REQUESTS_HANDLER;
            nodeInitializerProperties.requestDataCallback = RequestDataHandler.IGNORE_REQUESTS_HANDLER;
            nodeInitializerProperties.transactionsAnnouncementCallbackFactory = TransactionInventoryMessageHandlerFactory.IGNORE_NEW_TRANSACTIONS_HANDLER_FACTORY;
            nodeInitializerProperties.blockInventoryMessageHandler = BlockInventoryMessageHandler.IGNORE_INVENTORY_HANDLER;
            nodeInitializerProperties.queryUnconfirmedTransactionsCallback = QueryUnconfirmedTransactionsHandler.IGNORE_REQUESTS_HANDLER;
            nodeInitializerProperties.requestPeersHandler = new BitcoinNode.RequestPeersHandler() {
                @Override
                public List<BitcoinNodeIpAddress> getConnectedPeers() {
                    return new MutableList<BitcoinNodeIpAddress>(0);
                }
            };

            final ThreadPoolFactory threadPoolFactory = new ThreadPoolFactory() {
                @Override
                public ThreadPool newThreadPool() {
                    final ThreadPoolThrottle threadPoolThrottle = new ThreadPoolThrottle(bitcoinProperties.getMaxMessagesPerSecond(), _threadPool);
                    threadPoolThrottle.start();
                    return threadPoolThrottle;
                }
            };

            nodeInitializerProperties.threadPoolFactory = threadPoolFactory;
            nodeInitializerProperties.localNodeFeatures = localNodeFeatures;

            _nodeInitializer = new NodeInitializer(nodeInitializerProperties);
        }
    }

    public void run() {
        final Database database = _environment.getDatabase();
        final MasterDatabaseManagerCache masterDatabaseManagerCache = _environment.getMasterDatabaseManagerCache();

        final DatabaseManagerCache databaseManagerCache = new ReadOnlyLocalDatabaseManagerCache(masterDatabaseManagerCache);

        final List<BitcoinNode> bitcoinNodes;
        {
            final ImmutableListBuilder<BitcoinNode> bitcoinNodeListBuilder = new ImmutableListBuilder<BitcoinNode>();
            for (final SeedNodeProperties seedNodeProperties : _bitcoinProperties.getSeedNodeProperties()) {
                final String host = seedNodeProperties.getAddress();
                final Integer port = seedNodeProperties.getPort();

                final BitcoinNode bitcoinNode = _nodeInitializer.initializeNode(host, port);
                bitcoinNodeListBuilder.add(bitcoinNode);
            }
            bitcoinNodes = bitcoinNodeListBuilder.build();

            if (bitcoinNodes.isEmpty()) {
                Logger.log("ERROR: No trusted nodes set.");
                BitcoinUtil.exitFailure();
            }
        }

        final BitcoinNode bitcoinNode = bitcoinNodes.get(0);

        if ( (_blockHashes.getSize() == 1) && (Util.areEqual(BlockHeader.GENESIS_BLOCK_HASH, _blockHashes.get(0))) ) {
            try (final DatabaseConnection databaseConnection = database.newConnection()) {
                final FullNodeDatabaseManager databaseManager = new FullNodeDatabaseManager(databaseConnection, databaseManagerCache);

                TransactionUtil.startTransaction(databaseConnection);
                databaseConnection.executeSql(new Query("UPDATE blocks SET blockchain_segment_id = NULL"));
                databaseConnection.executeSql(new Query("SET foreign_key_checks = 0"));
                databaseConnection.executeSql(new Query("DELETE FROM blockchain_segments"));
                databaseConnection.executeSql(new Query("SET foreign_key_checks = 1"));

                final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();

                long i = 0L;
                final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT id FROM blocks ORDER BY block_height ASC"));
                for (final Row row : rows) {
                    final BlockId blockId = BlockId.wrap(row.getLong("id"));

                    Logger.log(i + " of " + rows.size() + " (" + blockId + ")");
                    synchronized (BlockHeaderDatabaseManager.MUTEX) {
                        blockchainDatabaseManager.updateBlockchainsForNewBlock(blockId);
                    }

                    i += 1L;
                }
                TransactionUtil.commitTransaction(databaseConnection);
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
                Logger.log("Error repairing BlockchainSegments.");
            }

            _environment.getMasterDatabaseManagerCache().close();
            System.exit(0);
        }

        for (final Sha256Hash blockHash : _blockHashes) {
            final Object synchronizer = new Object();
            bitcoinNode.requestBlock(blockHash, new BitcoinNode.DownloadBlockCallback() {
                @Override
                public void onResult(final Block block) {
                    try (final DatabaseConnection databaseConnection = database.newConnection()) {
                        final FullNodeDatabaseManager databaseManager = new FullNodeDatabaseManager(databaseConnection, databaseManagerCache);
                        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                        final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

                        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                        if (blockId == null) {
                            Logger.log("Block not found: " + blockHash);
                            return;
                        }

                        TransactionUtil.startTransaction(databaseConnection);
                        blockDatabaseManager.repairBlock(block);
                        TransactionUtil.commitTransaction(databaseConnection);

                        Logger.log("Repaired block: " + blockHash);

                    }
                    catch (final DatabaseException exception) {
                        Logger.log(exception);
                        Logger.log("Error repairing block: " + blockHash);
                    }

                    synchronizer.notifyAll();
                }
            });

            try { synchronizer.wait(); }
            catch (final InterruptedException exception) { break; }
        }

        _environment.getMasterDatabaseManagerCache().close();
        System.exit(0);
    }
}
