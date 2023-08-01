package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.bip.ChipNetUpgradeSchedule;
import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.TestNet4UpgradeSchedule;
import com.softwareverde.bitcoin.bip.TestNetUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.validator.BlockHeaderValidator;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.block.validator.difficulty.TestNetDifficultyCalculator;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.main.NetworkType;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
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
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.network.time.VolatileNetworkTime;

import java.io.File;

public class NodeModule {
    protected String _toCanonicalConnectionString(final NodeIpAddress nodeIpAddress) {
        final Ip ip = nodeIpAddress.getIp();
        final Integer port = nodeIpAddress.getPort();
        return (ip + ":" + port);
    }

    protected final BitcoinProperties _bitcoinProperties;
    protected final MutableList<BitcoinNode> _bitcoinNodes = new MutableArrayList<>();
    protected final Blockchain _blockchain;
    protected final UpgradeSchedule _upgradeSchedule;
    protected final VolatileNetworkTime _networkTime;

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
        final File blockchainFile = new File(dataDirectory, "block-headers.dat");

        final NetworkType networkType = bitcoinProperties.getNetworkType();
        BitcoinConstants.configureForNetwork(networkType);

        _blockchain = new Blockchain();
        try {
            _blockchain.load(blockchainFile);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }

        final DifficultyCalculator difficultyCalculator;
        switch (networkType) {
            case TEST_NET: {
                _upgradeSchedule = new TestNetUpgradeSchedule();
                difficultyCalculator = new TestNetDifficultyCalculator(_blockchain, _upgradeSchedule);
            } break;
            case TEST_NET4: {
                _upgradeSchedule = new TestNet4UpgradeSchedule();
                difficultyCalculator = new TestNetDifficultyCalculator(_blockchain, _upgradeSchedule);
            } break;
            case CHIP_NET: {
                _upgradeSchedule = new ChipNetUpgradeSchedule();
                difficultyCalculator = new DifficultyCalculator(_blockchain, _upgradeSchedule);
            } break;
            default: {
                _upgradeSchedule = new CoreUpgradeSchedule();
                difficultyCalculator = new DifficultyCalculator(_blockchain, _upgradeSchedule);
            } break;
        }

        final AsertReferenceBlock asertReferenceBlock = BitcoinConstants.getAsertReferenceBlock();
        _blockchain.setAsertReferenceBlock(asertReferenceBlock);

        _networkTime = new MutableNetworkTime();

        final Integer defaultPort = bitcoinProperties.getBitcoinPort();
        final int numberOfNodesToAttemptConnectionsTo = 1;
        final MutableList<NodeIpAddress> nodeIpAddresses = new MutableArrayList<>();
        { // Connect to DNS seeded nodes...
            final MutableSet<String> uniqueConnectionStrings = new MutableHashSet<>();

            final List<String> dnsSeeds = bitcoinProperties.getDnsSeeds();
            for (final String seedHost : dnsSeeds) {
                Logger.info("seedHost=" + seedHost);
                final List<Ip> seedIps = Ip.allFromHostName(seedHost);
                if (seedIps == null) { continue; }

                for (final Ip ip : seedIps) {
                    Logger.info("ip=" + ip);
                    if (nodeIpAddresses.getCount() >= numberOfNodesToAttemptConnectionsTo) { break; }
                    final NodeIpAddress nodeIpAddress = new NodeIpAddress(ip, defaultPort);

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
            });
            bitcoinNode.setDisconnectedCallback(new Node.DisconnectedCallback() {
                @Override
                public void onNodeDisconnected() {
                    Logger.info("Disconnected from: " + host + ":" + port);
                }
            });

            final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(_upgradeSchedule, _blockchain, _networkTime, difficultyCalculator);

            final BitcoinNode.DownloadBlockHeadersCallback downloadBlockHeadersCallback = new BitcoinNode.DownloadBlockHeadersCallback() {
                @Override
                public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final List<BlockHeader> response) {
                    int count = 0;
                    boolean hadInvalid = false;
                    for (final BlockHeader blockHeader : response) {
                        final Long blockHeight = _blockchain.getHeadBlockHeight();
                        final Sha256Hash blockHash = blockHeader.getHash();
                        if (blockHeight > 0L) {
                            final BlockHeaderValidator.BlockHeaderValidationResult validationResult = blockHeaderValidator.validateBlockHeader(blockHeader, blockHeight + 1L);
                            if (! validationResult.isValid) {
                                Logger.debug(validationResult.errorMessage + " " + blockHash);
                                hadInvalid = true;
                                break;
                            }
                        }

                        final Boolean result = _blockchain.addBlockHeader(blockHeader);
                        if (! result) {
                            Logger.debug("Rejected: " + blockHash);
                            hadInvalid = true;
                            break;
                        }

                        count += 1;
                    }

                    final Sha256Hash headBlockHash = _blockchain.getHeadBlockHash();
                    Logger.info("Head: " + headBlockHash + " " + _blockchain.getHeadBlockHeight());

                    if (count > 0 && (! hadInvalid)) {
                        bitcoinNode.requestBlockHeadersAfter(new ImmutableList<Sha256Hash>(headBlockHash), this, RequestPriority.NORMAL);
                    }
                }
            };

            final Sha256Hash headBlockHash = _blockchain.getHeadBlockHash();
            bitcoinNode.requestBlockHeadersAfter(new ImmutableList<Sha256Hash>(headBlockHash), downloadBlockHeadersCallback, RequestPriority.NORMAL);

            Logger.info("Connecting to: " + host + ":" + port);
            bitcoinNode.connect();
            _bitcoinNodes.add(bitcoinNode);
        }

        final Runtime runtime = Runtime.getRuntime();
        runtime.addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    _blockchain.save(blockchainFile);
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
