package com.softwareverde.bitcoin.server.message;

import com.softwareverde.bitcoin.server.message.header.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.header.ProtocolMessageHeaderParser;
import com.softwareverde.bitcoin.server.message.type.block.BlockMessageInflater;
import com.softwareverde.bitcoin.server.message.type.error.ErrorMessageInflater;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddressMessageInflater;
import com.softwareverde.bitcoin.server.message.type.node.ping.PingMessageInflater;
import com.softwareverde.bitcoin.server.message.type.node.pong.PongMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.block.QueryBlocksMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.block.header.QueryBlockHeadersMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.response.QueryResponseMessageInflater;
import com.softwareverde.bitcoin.server.message.type.version.acknowledge.AcknowledgeVersionMessageInflater;
import com.softwareverde.bitcoin.server.message.type.version.synchronize.SynchronizeVersionMessageInflater;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.io.Logger;

import java.util.HashMap;
import java.util.Map;

public class ProtocolMessageFactory {
    private final ProtocolMessageHeaderParser _protocolMessageHeaderParser = new ProtocolMessageHeaderParser();
    private final Map<ProtocolMessage.MessageType, ProtocolMessageInflater> _commandInflaterMap = new HashMap<ProtocolMessage.MessageType, ProtocolMessageInflater>();

    public ProtocolMessageFactory() {
        _commandInflaterMap.put(ProtocolMessage.MessageType.SYNCHRONIZE_VERSION, new SynchronizeVersionMessageInflater());
        _commandInflaterMap.put(ProtocolMessage.MessageType.ACKNOWLEDGE_VERSION, new AcknowledgeVersionMessageInflater());
        _commandInflaterMap.put(ProtocolMessage.MessageType.PING, new PingMessageInflater());
        _commandInflaterMap.put(ProtocolMessage.MessageType.PONG, new PongMessageInflater());
        _commandInflaterMap.put(ProtocolMessage.MessageType.NODE_ADDRESSES, new NodeIpAddressMessageInflater());
        _commandInflaterMap.put(ProtocolMessage.MessageType.QUERY_BLOCK_HEADERS, new QueryBlockHeadersMessageInflater());
        _commandInflaterMap.put(ProtocolMessage.MessageType.QUERY_BLOCKS, new QueryBlocksMessageInflater());
        _commandInflaterMap.put(ProtocolMessage.MessageType.ERROR, new ErrorMessageInflater());
        _commandInflaterMap.put(ProtocolMessage.MessageType.QUERY_RESPONSE, new QueryResponseMessageInflater());
        _commandInflaterMap.put(ProtocolMessage.MessageType.BLOCK, new BlockMessageInflater());
    }

    public ProtocolMessage fromBytes(final byte[] bytes) {
        final ProtocolMessageHeader protocolMessageHeader = _protocolMessageHeaderParser.fromBytes(bytes);
        if (protocolMessageHeader == null) { return null; }

        final ProtocolMessageInflater protocolMessageInflater = _commandInflaterMap.get(protocolMessageHeader.command);
        if (protocolMessageInflater == null) {
            Logger.log("NOTICE: Unsupported message command. 0x"+ BitcoinUtil.toHexString(ByteUtil.copyBytes(bytes, 0, ProtocolMessageHeaderParser.HEADER_BYTE_COUNT)));
            return null;
        }

        return protocolMessageInflater.fromBytes(bytes);
    }
}
