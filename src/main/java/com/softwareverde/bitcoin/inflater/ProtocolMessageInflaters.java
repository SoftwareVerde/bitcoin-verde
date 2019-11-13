package com.softwareverde.bitcoin.inflater;

import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeaderInflater;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddressInflater;

public interface ProtocolMessageInflaters {
    BitcoinProtocolMessageHeaderInflater getBitcoinProtocolMessageHeaderInflater();
    NodeIpAddressInflater getNodeIpAddressInflater();
}
