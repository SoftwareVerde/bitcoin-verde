package com.softwareverde.bitcoin.server.node;

import com.softwareverde.bitcoin.server.message.BitcoinBinaryPacketFormat;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.network.socket.BinarySocket;

public class BitcoinNodeFactory {
    protected final LocalNodeFeatures _localNodeFeatures;
    protected final BitcoinBinaryPacketFormat _binaryPacketFormat;

    public BitcoinNodeFactory(final LocalNodeFeatures localNodeFeatures) {
        this (BitcoinProtocolMessage.BINARY_PACKET_FORMAT, localNodeFeatures);
    }

    public BitcoinNodeFactory(final BitcoinBinaryPacketFormat binaryPacketFormat, final LocalNodeFeatures localNodeFeatures) {
        _localNodeFeatures = localNodeFeatures;
        _binaryPacketFormat = binaryPacketFormat;
    }

    public BitcoinNode newNode(final String host, final Integer port) {
        return new BitcoinNode(host, port, _binaryPacketFormat, _localNodeFeatures);
    }

    public BitcoinNode newNode(final BinarySocket binarySocket) {
        return new BitcoinNode(binarySocket, _localNodeFeatures);
    }

    public BitcoinBinaryPacketFormat getBinaryPacketFormat() {
        return _binaryPacketFormat;
    }
}
