package com.softwareverde.network.p2p.message.type;

import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;

public interface SynchronizeVersionMessage extends ProtocolMessage {
    Long getNonce();
    NodeIpAddress getLocalNodeIpAddress();
    Long getTimestamp();
    String getUserAgent();
}
