package com.softwareverde.network.p2p.node;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.query.response.block.BlockMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.concurrent.Pin;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.message.type.SynchronizeVersionMessage;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.network.socket.BinarySocketServer;
import com.softwareverde.util.Container;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.timer.MilliTimer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

public class BitcoinNodeTests extends UnitTest {
    protected static final Integer FAKE_PORT = 9999;
    protected static final Integer UNUSED_PORT = (FAKE_PORT + 1); // Used to ensure there is no socket server actually running on the host.

    public static class ExposedBitcoinNode extends BitcoinNode {
        protected final AtomicBoolean _wasDisconnectCalled = new AtomicBoolean(false);

        @Override
        protected void _disconnect() {
            _wasDisconnectCalled.set(true);
            super._disconnect();
        }

        public ExposedBitcoinNode(final String host, final Integer port, final ThreadPool threadPool, final LocalNodeFeatures localNodeFeatures) {
            super(host, port, threadPool, localNodeFeatures);
        }

        public ExposedBitcoinNode(final BinarySocket binarySocket, final ThreadPool threadPool, final LocalNodeFeatures localNodeFeatures) {
            super(binarySocket, threadPool, localNodeFeatures);
        }

        public Boolean isMonitorThreadRunning() {
            return ( (_requestMonitorThread != null) && _requestMonitorThread.isAlive() );
        }

        public Boolean wasDisconnectCalled() {
            return _wasDisconnectCalled.get();
        }
    }

    @Before
    public void before() throws Exception {
        super.before();
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_timeout_request_after_no_response() throws Exception {
        // Setup
        final CachedThreadPool mainThreadPool = new CachedThreadPool(32, 1000L);
        mainThreadPool.start();

        final long REQUEST_TIMEOUT_MS = 3000L;

        final LocalNodeFeatures nodeFeatures = new LocalNodeFeatures() {
            @Override
            public NodeFeatures getNodeFeatures() {
                final NodeFeatures nodeFeatures = new NodeFeatures();
                nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                nodeFeatures.enableFeature(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                return nodeFeatures;
            }
        };

        final MutableList<ExposedBitcoinNode> receivedNodeConnections = new MutableList<ExposedBitcoinNode>();
        final BinarySocketServer socketServer = new BinarySocketServer(FAKE_PORT, BitcoinProtocolMessage.BINARY_PACKET_FORMAT, mainThreadPool);
        socketServer.setSocketConnectedCallback(new BinarySocketServer.SocketConnectedCallback() {
            @Override
            public void run(final BinarySocket binarySocket) {
                final ExposedBitcoinNode bitcoinNode = new ExposedBitcoinNode(binarySocket, mainThreadPool, nodeFeatures) {
                    @Override
                    protected void _onSynchronizeVersion(final SynchronizeVersionMessage synchronizeVersionMessage) {
                        synchronized (LOCAL_SYNCHRONIZATION_NONCES) { // Disable self-connection detection....
                            LOCAL_SYNCHRONIZATION_NONCES.clear();
                        }

                        super._onSynchronizeVersion(synchronizeVersionMessage);
                    }

                    @Override
                    protected Long _getMaximumTimeoutMs(final BitcoinNodeCallback callback) {
                        return REQUEST_TIMEOUT_MS;
                    }
                };
                bitcoinNode.handshake();
                bitcoinNode.connect();
                receivedNodeConnections.add(bitcoinNode);
            }
        });

        final AtomicBoolean handshakeCompleted = new AtomicBoolean(false);
        final AtomicBoolean blockDownloaded = new AtomicBoolean(false);
        final AtomicBoolean blockDownloadFailed = new AtomicBoolean(false);
        final MilliTimer timeoutTimer = new MilliTimer();
        final Pin pin = new Pin();

        final ExposedBitcoinNode bitcoinNode = new ExposedBitcoinNode("127.0.0.1", FAKE_PORT, mainThreadPool, nodeFeatures) {
            @Override
            protected void _onSynchronizeVersion(final SynchronizeVersionMessage synchronizeVersionMessage) {
                synchronized (BitcoinNode.LOCAL_SYNCHRONIZATION_NONCES) { // Disable self-connection detection....
                    BitcoinNode.LOCAL_SYNCHRONIZATION_NONCES.clear();
                }

                super._onSynchronizeVersion(synchronizeVersionMessage);
            }

            @Override
            protected Long _getMaximumTimeoutMs(final BitcoinNodeCallback callback) {
                return REQUEST_TIMEOUT_MS;
            }
        };
        bitcoinNode.handshake();
        bitcoinNode.setHandshakeCompleteCallback(new Node.HandshakeCompleteCallback() {
            @Override
            public void onHandshakeComplete() {
                handshakeCompleted.set(true);
            }
        });
        bitcoinNode.requestBlock(Sha256Hash.EMPTY_HASH, new BitcoinNode.DownloadBlockCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Block result) {
                blockDownloaded.set(true);
                pin.release();
            }

            @Override
            public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
                blockDownloadFailed.set(true);
                timeoutTimer.stop();
                pin.release();
            }
        });

        socketServer.start();

        // Action
        timeoutTimer.start();
        bitcoinNode.connect();
        pin.waitForRelease(REQUEST_TIMEOUT_MS * 3L);

        bitcoinNode.disconnect();
        socketServer.stop();

        // Assert
        Assert.assertEquals(receivedNodeConnections.getCount(), 1);
        Assert.assertTrue(blockDownloadFailed.get());
        Assert.assertFalse(blockDownloaded.get());
        // Assert.assertTrue(timeoutTimer.getMillisecondsElapsed() > REQUEST_TIMEOUT_MS); // Assert disabled due to BitcoinNode early-timeout detection due to no network progress.
        Assert.assertTrue(timeoutTimer.getMillisecondsElapsed() < (REQUEST_TIMEOUT_MS * 2L));
        Assert.assertFalse(bitcoinNode.isMonitorThreadRunning());
        Assert.assertTrue(bitcoinNode.wasDisconnectCalled());

        Thread.sleep(1000L);

        for (final ExposedBitcoinNode receivedConnectionBitcoinNode : receivedNodeConnections) {
            if (receivedConnectionBitcoinNode.isMonitorThreadRunning()) {
                Thread.sleep(10000L); // Allocate an excessive amount of time to ensure the OS noticed the disconnection.
            }

            Assert.assertFalse(receivedConnectionBitcoinNode.isMonitorThreadRunning());
            Assert.assertTrue(receivedConnectionBitcoinNode.wasDisconnectCalled());
        }

        mainThreadPool.stop();
    }

    @Test
    public void should_close_monitor_thread_after_failed_connection() throws Exception {
        // Setup
        final CachedThreadPool mainThreadPool = new CachedThreadPool(32, 1000L);
        mainThreadPool.start();

        final long REQUEST_TIMEOUT_MS = 3000L;

        final LocalNodeFeatures nodeFeatures = new LocalNodeFeatures() {
            @Override
            public NodeFeatures getNodeFeatures() {
                final NodeFeatures nodeFeatures = new NodeFeatures();
                nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                nodeFeatures.enableFeature(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                return nodeFeatures;
            }
        };

        final AtomicBoolean handshakeCompleted = new AtomicBoolean(false);
        final MilliTimer timeoutTimer = new MilliTimer();
        final Pin pin = new Pin();

        final ExposedBitcoinNode bitcoinNode = new ExposedBitcoinNode("127.0.0.1", UNUSED_PORT, mainThreadPool, nodeFeatures) {
            @Override
            protected Long _getMaximumTimeoutMs(final BitcoinNodeCallback callback) {
                return REQUEST_TIMEOUT_MS;
            }
        };
        bitcoinNode.handshake();
        bitcoinNode.setHandshakeCompleteCallback(new Node.HandshakeCompleteCallback() {
            @Override
            public void onHandshakeComplete() {
                handshakeCompleted.set(true);
                pin.release();
            }
        });

        // Action
        timeoutTimer.start();
        bitcoinNode.connect();

        pin.waitForRelease(REQUEST_TIMEOUT_MS * 3L);

        // Assert
        Assert.assertFalse(handshakeCompleted.get());
        Assert.assertFalse(bitcoinNode.isMonitorThreadRunning());

        bitcoinNode.disconnect(); // Not necessary, but used in case an actual connection occurred.
        mainThreadPool.stop();
    }

    @Test
    public void should_not_timeout_after_successful_response() throws Exception {
        // Setup
        final CachedThreadPool mainThreadPool = new CachedThreadPool(32, 1000L);
        mainThreadPool.start();

        final long REQUEST_TIMEOUT_MS = 3000L;

        final LocalNodeFeatures nodeFeatures = new LocalNodeFeatures() {
            @Override
            public NodeFeatures getNodeFeatures() {
                final NodeFeatures nodeFeatures = new NodeFeatures();
                nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                nodeFeatures.enableFeature(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                return nodeFeatures;
            }
        };

        final BinarySocketServer socketServer = new BinarySocketServer(FAKE_PORT, BitcoinProtocolMessage.BINARY_PACKET_FORMAT, mainThreadPool);
        socketServer.setSocketConnectedCallback(new BinarySocketServer.SocketConnectedCallback() {
            @Override
            public void run(final BinarySocket binarySocket) {
                final ExposedBitcoinNode bitcoinNode = new ExposedBitcoinNode(binarySocket, mainThreadPool, nodeFeatures) {
                    @Override
                    protected void _onSynchronizeVersion(final SynchronizeVersionMessage synchronizeVersionMessage) {
                        synchronized (LOCAL_SYNCHRONIZATION_NONCES) { // Disable self-connection detection....
                            LOCAL_SYNCHRONIZATION_NONCES.clear();
                        }

                        super._onSynchronizeVersion(synchronizeVersionMessage);
                    }
                };
                bitcoinNode.setRequestDataHandler(new BitcoinNode.RequestDataHandler() {
                    @Override
                    public void run(final BitcoinNode bitcoinNode, final List<InventoryItem> dataHashes) {
                        final byte[] genesisBlockData = HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK);
                        final CoreInflater inflaters = new CoreInflater();
                        final Block block = inflaters.getBlockInflater().fromBytes(genesisBlockData);

                        final BlockMessage blockMessage = new BlockMessage(inflaters);
                        blockMessage.setBlock(block);

                        bitcoinNode._connection._writeOrQueueMessage(blockMessage);
                    }
                });
                bitcoinNode.handshake();
                bitcoinNode.connect();
            }
        });
        socketServer.start();

        final Pin pin = new Pin();
        final Container<Boolean> onResultCalled = new Container<>(false);
        final Container<Boolean> onFailureCalled = new Container<>(false);

        // Action
        final BitcoinNode bitcoinNode = new BitcoinNode("127.0.0.1", FAKE_PORT, mainThreadPool, nodeFeatures) {
            @Override
            protected void _onSynchronizeVersion(final SynchronizeVersionMessage synchronizeVersionMessage) {
                synchronized (BitcoinNode.LOCAL_SYNCHRONIZATION_NONCES) { // Disable self-connection detection....
                    BitcoinNode.LOCAL_SYNCHRONIZATION_NONCES.clear();
                }

                super._onSynchronizeVersion(synchronizeVersionMessage);
            }

            @Override
            protected Long _getMaximumTimeoutMs(final BitcoinNodeCallback callback) {
                return REQUEST_TIMEOUT_MS;
            }
        };
        bitcoinNode.handshake();
        bitcoinNode.setHandshakeCompleteCallback(new Node.HandshakeCompleteCallback() {
            @Override
            public void onHandshakeComplete() {
                bitcoinNode.requestBlock(Sha256Hash.fromHexString(BitcoinConstants.getGenesisBlockHash()), new BitcoinNode.DownloadBlockCallback() {
                    @Override
                    public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Block result) {
                        onResultCalled.value = true;
                        pin.release();
                    }

                    @Override
                    public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
                        onFailureCalled.value = true;
                        pin.release();
                    }
                });
            }
        });
        bitcoinNode.connect();

        pin.waitForRelease();

        Thread.sleep((long) (REQUEST_TIMEOUT_MS * 1.5));

        // Assert
        Assert.assertTrue(onResultCalled.value);
        Assert.assertFalse(onFailureCalled.value);

        bitcoinNode.disconnect();
        socketServer.stop();
        mainThreadPool.stop();
    }

    @Test
    public void should_fail_request_after_disconnect() throws Exception {
        // Setup
        final CachedThreadPool mainThreadPool = new CachedThreadPool(32, 1000L);
        mainThreadPool.start();

        final long REQUEST_TIMEOUT_MS = 3000L;
        final long DISCONNECT_AFTER_MS = 500L;

        final LocalNodeFeatures nodeFeatures = new LocalNodeFeatures() {
            @Override
            public NodeFeatures getNodeFeatures() {
                final NodeFeatures nodeFeatures = new NodeFeatures();
                nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                nodeFeatures.enableFeature(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                return nodeFeatures;
            }
        };

        final MutableList<ExposedBitcoinNode> receivedNodeConnections = new MutableList<ExposedBitcoinNode>();
        final BinarySocketServer socketServer = new BinarySocketServer(FAKE_PORT, BitcoinProtocolMessage.BINARY_PACKET_FORMAT, mainThreadPool);
        socketServer.setSocketConnectedCallback(new BinarySocketServer.SocketConnectedCallback() {
            @Override
            public void run(final BinarySocket binarySocket) {
                final ExposedBitcoinNode bitcoinNode = new ExposedBitcoinNode(binarySocket, mainThreadPool, nodeFeatures) {
                    @Override
                    protected void _onSynchronizeVersion(final SynchronizeVersionMessage synchronizeVersionMessage) {
                        synchronized (LOCAL_SYNCHRONIZATION_NONCES) { // Disable self-connection detection....
                            LOCAL_SYNCHRONIZATION_NONCES.clear();
                        }

                        super._onSynchronizeVersion(synchronizeVersionMessage);
                    }

                    @Override
                    protected Long _getMaximumTimeoutMs(final BitcoinNodeCallback callback) {
                        return REQUEST_TIMEOUT_MS;
                    }
                };
                bitcoinNode.handshake();
                bitcoinNode.connect();
                receivedNodeConnections.add(bitcoinNode);

                try { Thread.sleep(DISCONNECT_AFTER_MS); } catch (final Exception exception) { }
                bitcoinNode.disconnect();
            }
        });

        final AtomicBoolean handshakeCompleted = new AtomicBoolean(false);
        final AtomicBoolean blockDownloaded = new AtomicBoolean(false);
        final AtomicBoolean blockDownloadFailed = new AtomicBoolean(false);
        final MilliTimer timeoutTimer = new MilliTimer();
        final Pin pin = new Pin();

        final ExposedBitcoinNode bitcoinNode = new ExposedBitcoinNode("127.0.0.1", FAKE_PORT, mainThreadPool, nodeFeatures) {
            @Override
            protected void _onSynchronizeVersion(final SynchronizeVersionMessage synchronizeVersionMessage) {
                synchronized (BitcoinNode.LOCAL_SYNCHRONIZATION_NONCES) { // Disable self-connection detection....
                    BitcoinNode.LOCAL_SYNCHRONIZATION_NONCES.clear();
                }

                super._onSynchronizeVersion(synchronizeVersionMessage);
            }

            @Override
            protected Long _getMaximumTimeoutMs(final BitcoinNodeCallback callback) {
                return REQUEST_TIMEOUT_MS;
            }
        };
        bitcoinNode.handshake();
        bitcoinNode.setHandshakeCompleteCallback(new Node.HandshakeCompleteCallback() {
            @Override
            public void onHandshakeComplete() {
                handshakeCompleted.set(true);
            }
        });
        bitcoinNode.requestBlock(Sha256Hash.EMPTY_HASH, new BitcoinNode.DownloadBlockCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Block result) {
                blockDownloaded.set(true);
                pin.release();
            }

            @Override
            public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
                blockDownloadFailed.set(true);
                timeoutTimer.stop();
                pin.release();

                Logger.info("Detected timeout after: " + timeoutTimer.getMillisecondsElapsed());
            }
        });

        socketServer.start();

        // Action
        timeoutTimer.start();
        bitcoinNode.connect();
        pin.waitForRelease(REQUEST_TIMEOUT_MS * 3L);

        bitcoinNode.disconnect();
        socketServer.stop();

        // Assert
        Assert.assertEquals(receivedNodeConnections.getCount(), 1);
        Assert.assertTrue(blockDownloadFailed.get());
        Assert.assertFalse(blockDownloaded.get());
        Assert.assertTrue(timeoutTimer.getMillisecondsElapsed() <= (DISCONNECT_AFTER_MS + 500L));
        Assert.assertFalse(bitcoinNode.isMonitorThreadRunning());
        Assert.assertTrue(bitcoinNode.wasDisconnectCalled());

        Thread.sleep(1000L);

        for (final ExposedBitcoinNode receivedConnectionBitcoinNode : receivedNodeConnections) {
            if (receivedConnectionBitcoinNode.isMonitorThreadRunning()) {
                Thread.sleep(10000L); // Allocate an excessive amount of time to ensure the OS noticed the disconnection.
            }

            Assert.assertFalse(receivedConnectionBitcoinNode.isMonitorThreadRunning());
            Assert.assertTrue(receivedConnectionBitcoinNode.wasDisconnectCalled());
        }

        mainThreadPool.stop();
    }
}
