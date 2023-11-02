package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.validator.BlockHeaderValidator;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.message.BitcoinBinaryPacketFormat;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.query.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.server.module.node.handler.MemoryPoolEnquirerHandler;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeStore;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeStoreCore;
import com.softwareverde.bitcoin.server.module.node.manager.NodeInitializer;
import com.softwareverde.bitcoin.server.module.node.manager.banfilter.BanFilterCore;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStoreCore;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloadPlannerCore;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.inventory.BitcoinNodeBlockInventoryTracker;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.BitcoinNodeFactory;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.server.node.RequestPriority;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.filedb.WorkerManager;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.type.time.SystemTime;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockchainSyncer implements AutoCloseable {
    protected final Blockchain _blockchain;
    protected final PendingBlockStoreCore _pendingBlockStore;
    protected final WorkerManager _syncWorker;
    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final BlockDownloader _blockDownloader;
    protected final UpgradeSchedule _upgradeSchedule;
    protected final DifficultyCalculator _difficultyCalculator;
    protected final BlockchainSynchronizationStatusHandler _synchronizationStatusHandler;
    protected final AtomicBoolean _isShuttingDown = new AtomicBoolean(false);

    protected void _syncHeaders() {
        if (_isShuttingDown.get()) { return; }
        Logger.debug("_syncHeaders.run");

        final NetworkTime networkTime = _bitcoinNodeManager.getNetworkTime();
        final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(_upgradeSchedule, _blockchain, networkTime, _difficultyCalculator);

        final BitcoinNode.DownloadBlockHeadersCallback downloadBlockHeadersCallback = new BitcoinNode.DownloadBlockHeadersCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final List<BlockHeader> response) {
                if (_isShuttingDown.get()) { return; }

                int count = 0;
                boolean hadInvalid = false;
                for (final BlockHeader blockHeader : response) {
                    final Sha256Hash blockHash = blockHeader.getHash();
                    final Long blockHeight = _blockchain.getHeadBlockHeaderHeight();

                    final Long existingBlockHeight = _blockchain.getBlockHeight(blockHash);
                    if (existingBlockHeight == null) {
                        if (blockHeight > 0L) {
                            final BlockHeaderValidator.BlockHeaderValidationResult validationResult = blockHeaderValidator.validateBlockHeader(blockHeader, blockHeight + 1L);
                            if (! validationResult.isValid) {
                                bitcoinNode.disconnect();
                                Logger.debug(validationResult.errorMessage + " " + blockHash);
                                hadInvalid = true;
                                break;
                            }
                        }

                        final Boolean result = _blockchain.addBlockHeader(blockHeader);
                        if (! result) {
                            bitcoinNode.disconnect();
                            Logger.debug("Rejected: " + blockHash);
                            hadInvalid = true;
                            break;
                        }

                        count += 1;

                        final Long nodeBlockHeight = bitcoinNode.getBlockHeight();
                        bitcoinNode.setBlockHeight(Math.max(blockHeight, nodeBlockHeight));
                    }
                    else { // BlockHeader is already known.
                        final Long nodeBlockHeight = bitcoinNode.getBlockHeight();
                        bitcoinNode.setBlockHeight(Math.max(existingBlockHeight, nodeBlockHeight));
                    }
                }

                final Sha256Hash headBlockHash = _blockchain.getHeadBlockHeaderHash();
                Logger.info("Head: " + headBlockHash + " " + _blockchain.getHeadBlockHeaderHeight());

                _synchronizationStatusHandler.recalculateState();

                if (count > 0 && (! hadInvalid)) {
                    if (count < RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT) {
                        Logger.debug("Block Headers Complete.");
                        _syncBlocks();
                    }
                    else {
                        // bitcoinNode.requestBlockHeadersAfter(new ImmutableList<Sha256Hash>(headBlockHash), this, RequestPriority.NORMAL);
                        _syncHeaders();
                        Logger.debug("_syncHeaders");
                    }
                }
                else {
                    Logger.debug("count=" + count + ", hadInvalid=" + hadInvalid);
                }
            }

            @Override
            public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
                _syncBlocks();
            }
        };

        final List<BitcoinNode> bitcoinNodes = _bitcoinNodeManager.getPreferredNodes();
        if (bitcoinNodes.isEmpty()) { return; }
        final BitcoinNode bitcoinNode = bitcoinNodes.get(0);

        _syncWorker.offerTask(new WorkerManager.Task() {
            @Override
            public void run() {
                if (_isShuttingDown.get()) { return; }
                final Sha256Hash headBlockHash = _blockchain.getHeadBlockHeaderHash();
                bitcoinNode.requestBlockHeadersAfter(new ImmutableList<Sha256Hash>(headBlockHash), downloadBlockHeadersCallback, RequestPriority.NORMAL);
            }
        });
    }

    protected void _syncBlocks() {
        _blockDownloader.wakeUp();
    }

    public BlockchainSyncer(final File downloadLocation, final Blockchain blockchain, final UpgradeSchedule upgradeSchedule, final DifficultyCalculator difficultyCalculator, final int maxNodeCount, final TransactionMempool transactionMempool, final BitcoinProperties bitcoinProperties) {
        if (! downloadLocation.exists()) {
            downloadLocation.mkdirs();
        }

        _blockchain = blockchain;
        _upgradeSchedule = upgradeSchedule;
        _difficultyCalculator = difficultyCalculator;
        _pendingBlockStore = new PendingBlockStoreCore(downloadLocation);

        _syncWorker = new WorkerManager(1, 1);
        _syncWorker.setName("Blockchain Sync");
        _syncWorker.start();

        final LocalNodeFeatures localNodeFeatures = new LocalNodeFeatures() {
            @Override
            public NodeFeatures getNodeFeatures() {
                final NodeFeatures nodeFeatures = new NodeFeatures();
                nodeFeatures.enableFeature(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                nodeFeatures.enableFeature(NodeFeatures.Feature.MINIMUM_OF_TWO_DAYS_BLOCKCHAIN_ENABLED);
                return nodeFeatures;
            }
        };

        final BitcoinBinaryPacketFormat binaryPacketFormat = BitcoinProtocolMessage.BINARY_PACKET_FORMAT;
        _synchronizationStatusHandler = new BlockchainSynchronizationStatusHandler(blockchain);

        final NodeInitializer.Context nodeInitializerContext = new NodeInitializer.Context();
        nodeInitializerContext.synchronizationStatus = _synchronizationStatusHandler;
        nodeInitializerContext.localNodeFeatures = localNodeFeatures;
        nodeInitializerContext.binaryPacketFormat = binaryPacketFormat;
        nodeInitializerContext.blockInventoryMessageHandler = new BitcoinNode.BlockInventoryAnnouncementHandler() {
            @Override
            public void onNewInventory(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockHashes) {
                // Nothing. // TODO?
            }

            @Override
            public void onNewHeaders(final BitcoinNode bitcoinNode, final List<BlockHeader> blockHeaders) {
                // Nothing. // TODO?
            }
        };
        nodeInitializerContext.transactionsAnnouncementHandlerFactory = new NodeInitializer.TransactionsAnnouncementHandlerFactory() {
            @Override
            public BitcoinNode.TransactionInventoryAnnouncementHandler createTransactionsAnnouncementHandler(final BitcoinNode bitcoinNode) {
                return new BitcoinNode.TransactionInventoryAnnouncementHandler() {
                    @Override
                    public void onResult(final BitcoinNode bitcoinNode, final List<Sha256Hash> transactionHashes) {
                        // Nothing. // TODO
                    }
                };
            }
        };

        final BitcoinNodeStore bitcoinNodeStore = new BitcoinNodeStoreCore();

        final BitcoinNodeManager.Context nodeManagerContext = new BitcoinNodeManager.Context();
        nodeManagerContext.minNodeCount = 4;
        nodeManagerContext.maxNodeCount = maxNodeCount;
        nodeManagerContext.shouldPrioritizeNewConnections = false;
        nodeManagerContext.nodeFactory = new BitcoinNodeFactory(binaryPacketFormat, localNodeFeatures);
        nodeManagerContext.networkTime = new MutableNetworkTime();
        nodeManagerContext.blockchain = blockchain;
        nodeManagerContext.nodeInitializer = new NodeInitializer(nodeInitializerContext);
        nodeManagerContext.banFilter = new BanFilterCore(bitcoinNodeStore);
        nodeManagerContext.memoryPoolEnquirer = new MemoryPoolEnquirerHandler(transactionMempool);
        nodeManagerContext.synchronizationStatusHandler = _synchronizationStatusHandler;
        nodeManagerContext.systemTime = new SystemTime();
        nodeManagerContext.bitcoinNodeStore = bitcoinNodeStore;

        _bitcoinNodeManager = new BitcoinNodeManager(nodeManagerContext);

        final List<String> dnsSeeds = bitcoinProperties.getDnsSeeds();
        _bitcoinNodeManager.defineDnsSeeds(dnsSeeds);

        _blockDownloader = new BlockDownloader(_pendingBlockStore, new BlockDownloader.BitcoinNodeCollector() {
            @Override
            public List<BitcoinNode> getBitcoinNodes() {
                return _bitcoinNodeManager.getNodes();
            }
        }, new BitcoinNodeBlockInventoryTracker(), new BlockDownloadPlannerCore(blockchain, _pendingBlockStore) { });
    }

    public void open() throws Exception {
        _syncWorker.start();
        _pendingBlockStore.open();
        _bitcoinNodeManager.start();

        _bitcoinNodeManager.setNewNodeHandshakedCallback(new BitcoinNodeManager.NewNodeCallback() {
            @Override
            public void onNodeHandshakeComplete(final BitcoinNode bitcoinNode) {
                _syncHeaders();
            }
        });
    }

    public void run() {
        _syncHeaders();
    }

    public void close() throws Exception {
        _bitcoinNodeManager.close();
        _pendingBlockStore.close();
    }
}