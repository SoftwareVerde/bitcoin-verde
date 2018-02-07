package com.softwareverde.bitcoin.server.socket.message.networkaddress.version.synchronize;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.networkaddress.NetworkAddress;

public class SynchronizeVersionMessage extends ProtocolMessage {
    private VersionPayload _versionPayload;

    public SynchronizeVersionMessage() {
        super(Command.SYNCHRONIZE_VERSION);

        _versionPayload = new VersionPayload();
        _setPayload(_versionPayload.getBytes());
    }

    public void setRemoteAddress(final NetworkAddress remoteNetworkAddress) {
        _versionPayload.setRemoteAddress(remoteNetworkAddress);

        _setPayload(_versionPayload.getBytes());
    }
}
