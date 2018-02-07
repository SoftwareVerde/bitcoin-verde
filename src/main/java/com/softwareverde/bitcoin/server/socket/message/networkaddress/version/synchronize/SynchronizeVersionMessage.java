package com.softwareverde.bitcoin.server.socket.message.networkaddress.version.synchronize;

import com.softwareverde.bitcoin.server.socket.message.NodeFeatures;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.NetworkAddress;

public class SynchronizeVersionMessage extends ProtocolMessage {
    private VersionPayload _versionPayload;

    public SynchronizeVersionMessage() {
        super(Command.SYNCHRONIZE_VERSION);

        final NodeFeatures nodeFeatures = new NodeFeatures();
        nodeFeatures.enableFeatureFlag(NodeFeatures.Flags.BLOCKCHAIN_ENABLED);
        nodeFeatures.enableFeatureFlag(NodeFeatures.Flags.BITCOIN_CASH_ENABLED);

        _versionPayload = new VersionPayload();
        _versionPayload.setNodeFeatures(nodeFeatures);

        _setPayload(_versionPayload.getBytes());
    }

    public void setRemoteAddress(final NetworkAddress remoteNetworkAddress) {
        _versionPayload.setRemoteAddress(remoteNetworkAddress);

        _setPayload(_versionPayload.getBytes());
    }
}
