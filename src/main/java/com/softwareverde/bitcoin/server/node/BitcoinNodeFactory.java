package com.softwareverde.bitcoin.server.node;

import com.softwareverde.bitcoin.server.message.BitcoinBinaryPacketFormat;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.concurrent.threadpool.ThreadPoolFactory;
import com.softwareverde.network.socket.BinarySocket;

public class BitcoinNodeFactory {
    protected final ThreadPoolFactory _threadPoolFactory;
    protected final LocalNodeFeatures _localNodeFeatures;
    protected final BitcoinBinaryPacketFormat _binaryPacketFormat;

    public BitcoinNodeFactory(final BitcoinBinaryPacketFormat binaryPacketFormat, final ThreadPoolFactory threadPoolFactory, final LocalNodeFeatures localNodeFeatures) {
        _threadPoolFactory = threadPoolFactory;
        _localNodeFeatures = localNodeFeatures;
        _binaryPacketFormat = binaryPacketFormat;
    }

    public BitcoinNode newNode(final String host, final Integer port) {
        return new BitcoinNode(host, port, _binaryPacketFormat, _threadPoolFactory.newThreadPool(), _localNodeFeatures);
    }

    public BitcoinNode newNode(final BinarySocket binarySocket) {
        return new BitcoinNode(binarySocket, _threadPoolFactory.newThreadPool(), _localNodeFeatures);
    }

    public BitcoinBinaryPacketFormat getBinaryPacketFormat() {
        return _binaryPacketFormat;
    }
}
