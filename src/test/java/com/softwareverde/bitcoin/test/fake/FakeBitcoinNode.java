package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.socket.BinarySocket;

public class FakeBitcoinNode extends BitcoinNode {
    protected final MutableList<ProtocolMessage> _outboundMessageQueue = new MutableList<ProtocolMessage>();

    @Override
    protected void _initConnection() {
        // Nothing.
    }

    public FakeBitcoinNode(final String host, final Integer port, final ThreadPool threadPool, final LocalNodeFeatures localNodeFeatures) {
        super(host, port, threadPool, localNodeFeatures);
    }

    public FakeBitcoinNode(final BinarySocket binarySocket, final ThreadPool threadPool, final LocalNodeFeatures localNodeFeatures) {
        super(binarySocket, threadPool, localNodeFeatures);
    }

    @Override
    public void connect() {
        // Nothing.
    }

    @Override
    public void disconnect() {
        // Nothing.
    }

    @Override
    public void queueMessage(final BitcoinProtocolMessage protocolMessage) {
        _outboundMessageQueue.add(protocolMessage);
    }

    public List<ProtocolMessage> getSentMessages() {
        try { Thread.sleep(500L); } catch (final Exception exception) { } // Required to wait for messageQueue...
        return _outboundMessageQueue;
    }
}
