package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.bip.ChipNetUpgradeSchedule;
import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.TestNet4UpgradeSchedule;
import com.softwareverde.bitcoin.bip.TestNetUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.validator.BlockHeaderValidator;
import com.softwareverde.bitcoin.block.validator.BlockValidationResult;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.block.validator.difficulty.TestNetDifficultyCalculator;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.context.core.MutableUnspentTransactionOutputSet;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.main.NetworkType;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.query.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.CommitAsyncMode;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputFileDbManager;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.bitcoin.server.module.node.store.BlockStoreCore;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.server.node.RequestPriority;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.set.mutable.MutableHashSet;
import com.softwareverde.constable.set.mutable.MutableSet;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.Node;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class NodeModule {
    protected String _toCanonicalConnectionString(final NodeIpAddress nodeIpAddress) {
        final Ip ip = nodeIpAddress.getIp();
        final Integer port = nodeIpAddress.getPort();
        return (ip + ":" + port);
    }

    protected final BitcoinProperties _bitcoinProperties;
    protected final File _blockchainFile;
    protected final MutableList<BitcoinNode> _bitcoinNodes = new MutableArrayList<>();
    protected final MutableHashSet<Ip> _previouslyConnectedIps = new MutableHashSet<>();
    protected final Blockchain _blockchain;
    protected final UnspentTransactionOutputDatabaseManager _unspentTransactionOutputDatabaseManager;
    protected final BlockStore _blockStore;
    protected final UpgradeSchedule _upgradeSchedule;
    protected final VolatileNetworkTime _networkTime;
    protected final DifficultyCalculator _difficultyCalculator;
    protected final AtomicBoolean _isShuttingDown = new AtomicBoolean(false);

    protected void _syncBlockHeaders(final BitcoinNode bitcoinNode) {
        final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(_upgradeSchedule, _blockchain, _networkTime, _difficultyCalculator);

        final BitcoinNode.DownloadBlockHeadersCallback downloadBlockHeadersCallback = new BitcoinNode.DownloadBlockHeadersCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final List<BlockHeader> response) {
                if (_isShuttingDown.get()) { return; }

                int count = 0;
                boolean hadInvalid = false;
                for (final BlockHeader blockHeader : response) {
                    final Long blockHeight = _blockchain.getHeadBlockHeaderHeight();
                    final Sha256Hash blockHash = blockHeader.getHash();
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

                final Sha256Hash headBlockHash = _blockchain.getHeadBlockHeaderHash();
                Logger.info("Head: " + headBlockHash + " " + _blockchain.getHeadBlockHeaderHeight());

                if (count > 0 && (! hadInvalid)) {
                    if (count < RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT) {
                        Logger.debug("Block Headers Complete.");
                        try {
                            _blockchain.save(_blockchainFile);
                        }
                        catch (final Exception exception) {
                            Logger.debug(exception);
                        }
                        _syncBlocks(bitcoinNode);
                    }
                    else {
                        bitcoinNode.requestBlockHeadersAfter(new ImmutableList<Sha256Hash>(headBlockHash), this, RequestPriority.NORMAL);
                    }
                }
            }

            @Override
            public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
                _syncBlocks(bitcoinNode);
            }
        };

        final Sha256Hash headBlockHash = _blockchain.getHeadBlockHeaderHash();
        bitcoinNode.requestBlockHeadersAfter(new ImmutableList<Sha256Hash>(headBlockHash), downloadBlockHeadersCallback, RequestPriority.NORMAL);
    }

    protected UnspentTransactionOutputContext _getUnspentTransactionOutputContext(final Block block) throws Exception {
        final Sha256Hash blockHash = block.getHash();
        final Long blockHeight = _blockchain.getBlockHeight(blockHash);
        final MutableUnspentTransactionOutputSet transactionOutputSet = new MutableUnspentTransactionOutputSet();
        transactionOutputSet.loadOutputsForBlock(_blockchain, block, blockHeight, _upgradeSchedule);
        return transactionOutputSet;
    }

    protected void _syncBlocks(final BitcoinNode bitcoinNode) {
        Logger.info("Syncing blocks.");
        final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(_upgradeSchedule, _blockchain, _networkTime, _difficultyCalculator);
        final BlockValidator blockValidator = new BlockValidator(_upgradeSchedule, _blockchain, _networkTime, blockHeaderValidator);

        final Object mutex = new Object();
        final NanoTimer downloadTimer = new NanoTimer();
        final BitcoinNode.DownloadBlockCallback downloadBlockCallback = new BitcoinNode.DownloadBlockCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Block block) {
                if (_isShuttingDown.get()) { return; }
                downloadTimer.stop();

                final Sha256Hash blockHash = block.getHash();
                final Long blockHeight = _blockchain.getBlockHeight(blockHash);

                Logger.debug("Downloaded " + blockHash + " in " + downloadTimer.getMillisecondsElapsed() + "ms.");

                synchronized (mutex) {
                    Logger.debug("Processing: " + blockHash);
                    final NanoTimer utxoTimer = new NanoTimer();
                    final NanoTimer validationTimer = new NanoTimer();
                    final NanoTimer addBlockTimer = new NanoTimer();
                    final NanoTimer applyBlockTimer = new NanoTimer();

                    final BlockHeader nextBlock = _blockchain.getBlockHeader(blockHeight + 1L);
                    if (nextBlock != null) {
                        downloadTimer.start();
                        final Sha256Hash nextBlockHash = nextBlock.getHash();
                        Logger.debug("Requested: " + nextBlockHash + " from " + bitcoinNode);
                        bitcoinNode.requestBlock(nextBlockHash, this, RequestPriority.NORMAL);
                    }

                    try {
                        utxoTimer.start();
                        final UnspentTransactionOutputContext unspentTransactionOutputContext = _getUnspentTransactionOutputContext(block);
                        utxoTimer.stop();
                        validationTimer.start();
                        final BlockValidationResult result = blockValidator.validateBlock(block, unspentTransactionOutputContext);
                        validationTimer.stop();
                        if (! result.isValid) {
                            Logger.info(result.errorMessage + " " + block.getHash());
                            bitcoinNode.disconnect();
                            return;
                        }

                        Logger.info("Valid: " + blockHeight + " " + blockHash);
                        addBlockTimer.start();
                        _blockchain.addBlock(block);
                        addBlockTimer.stop();
                        applyBlockTimer.start();
                        _unspentTransactionOutputDatabaseManager.applyBlock(block, blockHeight);
                        applyBlockTimer.stop();
                        Logger.debug("Finished: " + blockHash);
                    }
                    catch (final Exception exception) {
                        Logger.debug(exception);
                    }

                    Logger.debug(blockHash + " " +
                        "utxoTimer=" + utxoTimer.getMillisecondsElapsed() + " " +
                        "validationTimer=" + validationTimer.getMillisecondsElapsed() + " " +
                        "addBlockTimer=" + addBlockTimer.getMillisecondsElapsed() + " " +
                        "applyBlockTimer=" + applyBlockTimer.getMillisecondsElapsed()
                    );
                }
            }

            @Override
            public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
                Logger.info("Failed to receive Block: " + blockHash + " " + bitcoinNode);
                bitcoinNode.disconnect();
            }
        };

        final Long headBlockHeight = _blockchain.getHeadBlockHeight();
        final BlockHeader blockHeader = _blockchain.getBlockHeader(headBlockHeight + 1L);
        if (blockHeader != null) {
            downloadTimer.start();
            final Sha256Hash blockHash = blockHeader.getHash();
            Logger.debug("Requested: " + blockHash + " from " + bitcoinNode);
            bitcoinNode.requestBlock(blockHash, downloadBlockCallback, RequestPriority.NORMAL);
        }
    }

    protected void _addNewNodes(final int numberOfNodesToAttemptConnectionsTo) {
        final Integer defaultPort = _bitcoinProperties.getBitcoinPort();
        final MutableList<NodeIpAddress> nodeIpAddresses = new MutableArrayList<>();
        { // Connect to DNS seeded nodes...
            final MutableSet<String> uniqueConnectionStrings = new MutableHashSet<>();

            final List<String> dnsSeeds = _bitcoinProperties.getDnsSeeds();
            for (final String seedHost : dnsSeeds) {
                Logger.info("seedHost=" + seedHost);
                final List<Ip> seedIps = Ip.allFromHostName(seedHost);
                if (seedIps == null) { continue; }

                for (final Ip ip : seedIps) {
                    Logger.info("ip=" + ip);
                    if (Util.areEqual(ip.toString(), "")) { continue; }
                    if (nodeIpAddresses.getCount() >= numberOfNodesToAttemptConnectionsTo) { break; }
                    final NodeIpAddress nodeIpAddress = new NodeIpAddress(ip, defaultPort);

                    if (_previouslyConnectedIps.contains(ip)) { continue; }

                    final String connectionString = _toCanonicalConnectionString(nodeIpAddress);
                    final boolean isUnique = uniqueConnectionStrings.add(connectionString);
                    if (! isUnique) { continue; }

                    nodeIpAddresses.add(nodeIpAddress);
                }
            }
        }

        for (final NodeIpAddress nodeIpAddress : nodeIpAddresses) {
            final Ip ip = nodeIpAddress.getIp();
            if (ip == null) { continue; }

            _previouslyConnectedIps.add(ip);

            final String host = ip.toString();
            final Integer port = nodeIpAddress.getPort();
            final BitcoinNode bitcoinNode = new BitcoinNode(host, port, new LocalNodeFeatures() {
                @Override
                public NodeFeatures getNodeFeatures() {
                    final NodeFeatures nodeFeatures = new NodeFeatures();
                    nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                    nodeFeatures.enableFeature(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                    nodeFeatures.enableFeature(NodeFeatures.Feature.BLOOM_CONNECTIONS_ENABLED);
                    nodeFeatures.enableFeature(NodeFeatures.Feature.MINIMUM_OF_TWO_DAYS_BLOCKCHAIN_ENABLED);
                    return nodeFeatures;
                }
            });
            bitcoinNode.setHandshakeCompleteCallback(new Node.HandshakeCompleteCallback() {
                @Override
                public void onHandshakeComplete() {
                    Logger.info("Handshake complete: " + host + ":" + port);
                }
            });
            bitcoinNode.setNodeConnectedCallback(new Node.NodeConnectedCallback() {
                @Override
                public void onNodeConnected() {
                    Logger.info("Connected to: " + host + ":" + port);
                }

                @Override
                public void onFailure() {
                    _addNewNodes(1);
                }
            });
            bitcoinNode.setDisconnectedCallback(new Node.DisconnectedCallback() {
                @Override
                public void onNodeDisconnected() {
                    Logger.info("Disconnected from: " + host + ":" + port);
                }
            });

            bitcoinNode.setDisconnectedCallback(new Node.DisconnectedCallback() {
                @Override
                public void onNodeDisconnected() {
                    final NodeId nodeId = bitcoinNode.getId();
                    _bitcoinNodes.mutableVisit(new MutableList.MutableVisitor<>() {
                        @Override
                        public boolean run(final Container<BitcoinNode> bitcoinNodeContainer) {
                            if (! Util.areEqual(nodeId, bitcoinNodeContainer.value.getId())) { return true; }

                            bitcoinNodeContainer.value = null;
                            return false;
                        }
                    });

                    if (_bitcoinNodes.isEmpty()) {
                        _addNewNodes(1);
                    }
                }
            });

            bitcoinNode.setHandshakeCompleteCallback(new Node.HandshakeCompleteCallback() {
                @Override
                public void onHandshakeComplete() {
                    final NodeFeatures nodeFeatures = bitcoinNode.getNodeFeatures();
                    if (bitcoinNode.getBlockHeight() < _blockchain.getHeadBlockHeaderHeight()) {
                        bitcoinNode.disconnect();
                    }
                }
            });

            _syncBlockHeaders(bitcoinNode);

            Logger.info("Connecting to: " + host + ":" + port);
            bitcoinNode.connect();

            _bitcoinNodes.add(bitcoinNode);
        }
    }

    public NodeModule(final BitcoinProperties bitcoinProperties) {
        _bitcoinProperties = bitcoinProperties;

        final Thread mainThread = Thread.currentThread();
        mainThread.setName("Bitcoin Verde - Main");
        mainThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable throwable) {
                try {
                    Logger.error(throwable);
                }
                catch (final Throwable ignored) { }
            }
        });

        final File dataDirectory = new File(bitcoinProperties.getDataDirectory());
        _blockchainFile = new File(dataDirectory, "block-headers.dat");

        final NetworkType networkType = bitcoinProperties.getNetworkType();
        BitcoinConstants.configureForNetwork(networkType);

        _blockStore = new BlockStoreCore(dataDirectory, true);
        try {
            final File blocksDirectory = _blockStore.getBlockDataDirectory();
            final File utxoDbDirectory = new File(blocksDirectory, "utxo");
            Logger.info("Loading FileDB");
             _unspentTransactionOutputDatabaseManager = new UnspentTransactionOutputFileDbManager(utxoDbDirectory);
        }
        catch (final Exception exception) {
            throw new RuntimeException(exception);
        }

        _blockchain = new Blockchain(_blockStore, _unspentTransactionOutputDatabaseManager);
        try {
            _blockchain.load(_blockchainFile);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }

        switch (networkType) {
            case TEST_NET: {
                _upgradeSchedule = new TestNetUpgradeSchedule();
                _difficultyCalculator = new TestNetDifficultyCalculator(_blockchain, _upgradeSchedule);
            } break;
            case TEST_NET4: {
                _upgradeSchedule = new TestNet4UpgradeSchedule();
                _difficultyCalculator = new TestNetDifficultyCalculator(_blockchain, _upgradeSchedule);
            } break;
            case CHIP_NET: {
                _upgradeSchedule = new ChipNetUpgradeSchedule();
                _difficultyCalculator = new DifficultyCalculator(_blockchain, _upgradeSchedule);
            } break;
            default: {
                _upgradeSchedule = new CoreUpgradeSchedule();
                _difficultyCalculator = new DifficultyCalculator(_blockchain, _upgradeSchedule);
            } break;
        }

        final AsertReferenceBlock asertReferenceBlock = BitcoinConstants.getAsertReferenceBlock();
        _blockchain.setAsertReferenceBlock(asertReferenceBlock);

        _networkTime = new MutableNetworkTime();

        _addNewNodes(1);

        final Runtime runtime = Runtime.getRuntime();
        runtime.addShutdownHook(new Thread() {
            @Override
            public void run() {
                _isShuttingDown.set(true);

                try {
                    _blockchain.save(_blockchainFile);
                    _unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(null, CommitAsyncMode.BLOCK_UNTIL_COMPLETE);
                }
                catch (final Exception exception) {
                    Logger.debug(exception);
                }
            }
        });
    }

    public void loop() {
        while (true) {
            try {
                Thread.sleep(1000L);
            }
            catch (final Exception exception) {
                break;
            }
            Logger.flush();
        }
    }
}
