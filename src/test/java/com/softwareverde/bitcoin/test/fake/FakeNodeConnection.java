package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.node.NodeConnection;
import com.softwareverde.network.socket.BinarySocket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class FakeNodeConnection extends NodeConnection {
    public final FakeBinarySocket fakeBinarySocket;

    protected final MutableList<ProtocolMessage> _outboundMessageQueue = new MutableList<ProtocolMessage>();

    public FakeNodeConnection(final FakeBinarySocket fakeBinarySocket, final ThreadPool threadPool) {
        super(fakeBinarySocket, threadPool);
        this.fakeBinarySocket = fakeBinarySocket;
    }

    @Override
    protected void _processOutboundMessageQueue() { }

    @Override
    protected void _writeOrQueueMessage(final ProtocolMessage message) {
        _outboundMessageQueue.add(message);
    }

    public List<ProtocolMessage> getSentMessages() {
        try { Thread.sleep(500L); } catch (final Exception e) { } // Required to wait for messageQueue...

        return _outboundMessageQueue;
    }
}
