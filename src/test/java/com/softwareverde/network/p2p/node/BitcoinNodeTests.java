package com.softwareverde.network.p2p.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.concurrent.Pin;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.network.p2p.message.type.SynchronizeVersionMessage;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.network.socket.BinarySocketServer;
import com.softwareverde.util.timer.MilliTimer;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

public class BitcoinNodeTests extends UnitTest {
    protected static final Integer PORT = 9999;

    @Test
    public void should_timeout_request_after_no_response() throws Exception {
        // Setup
        final MainThreadPool mainThreadPool = new MainThreadPool(32, 1000L);

        final Long REQUEST_TIMEOUT_MS = 3000L;

        final LocalNodeFeatures nodeFeatures = new LocalNodeFeatures() {
            @Override
            public NodeFeatures getNodeFeatures() {
                final NodeFeatures nodeFeatures = new NodeFeatures();
                nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                nodeFeatures.enableFeature(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                return nodeFeatures;
            }
        };

        final MutableList<BitcoinNode> receivedNodeConnections = new MutableList<BitcoinNode>();
        final BinarySocketServer socketServer = new BinarySocketServer(PORT, BitcoinProtocolMessage.BINARY_PACKET_FORMAT, mainThreadPool);
        socketServer.setSocketConnectedCallback(new BinarySocketServer.SocketConnectedCallback() {
            @Override
            public void run(final BinarySocket binarySocket) {
                final BitcoinNode bitcoinNode = new BitcoinNode(binarySocket, mainThreadPool, nodeFeatures) {
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

        final BitcoinNode bitcoinNode = new BitcoinNode("127.0.0.1", PORT, mainThreadPool, nodeFeatures) {
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
        bitcoinNode.setNodeHandshakeCompleteCallback(new Node.NodeHandshakeCompleteCallback() {
            @Override
            public void onHandshakeComplete() {
                handshakeCompleted.set(true);
            }
        });
        bitcoinNode.requestBlock(Sha256Hash.EMPTY_HASH, new BitcoinNode.DownloadBlockCallback() {
            @Override
            public void onResult(final Block result) {
                blockDownloaded.set(true);
                pin.release();
            }

            @Override
            public void onFailure(final Sha256Hash blockHash) {
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
        Assert.assertTrue(blockDownloadFailed.get());
        Assert.assertFalse(blockDownloaded.get());
        Assert.assertTrue(timeoutTimer.getMillisecondsElapsed() > REQUEST_TIMEOUT_MS);
        Assert.assertTrue(timeoutTimer.getMillisecondsElapsed() < (REQUEST_TIMEOUT_MS * 2L));
    }
}
