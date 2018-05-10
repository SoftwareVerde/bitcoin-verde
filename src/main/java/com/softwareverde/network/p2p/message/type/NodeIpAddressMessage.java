package com.softwareverde.network.p2p.message.type;

import com.softwareverde.constable.list.List;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;

public interface NodeIpAddressMessage<T> extends ProtocolMessage<T> {
    List<? extends NodeIpAddress> getNodeIpAddresses();
    void addAddress(NodeIpAddress nodeIpAddress);
}
