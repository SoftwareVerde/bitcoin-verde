package com.softwareverde.bitcoin.server.message;

import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeaderInflater;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.block.BlockMessageInflater;
import com.softwareverde.bitcoin.server.message.type.error.ErrorMessageInflater;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddressMessageInflater;
import com.softwareverde.bitcoin.server.message.type.node.feature.NewBlocksViaHeadersMessageInflater;
import com.softwareverde.bitcoin.server.message.type.node.ping.BitcoinPingMessageInflater;
import com.softwareverde.bitcoin.server.message.type.node.pong.BitcoinPongMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.block.QueryBlocksMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.block.header.QueryBlockHeadersMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.response.QueryResponseMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.response.header.QueryBlockHeadersResponseMessageInflater;
import com.softwareverde.bitcoin.server.message.type.request.RequestDataMessageInflater;
import com.softwareverde.bitcoin.server.message.type.version.acknowledge.BitcoinAcknowledgeVersionMessageInflater;
import com.softwareverde.bitcoin.server.message.type.version.synchronize.BitcoinSynchronizeVersionMessageInflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.message.ProtocolMessageFactory;
import com.softwareverde.util.HexUtil;

import java.util.HashMap;
import java.util.Map;

public class BitcoinProtocolMessageFactory implements ProtocolMessageFactory {
    private final BitcoinProtocolMessageHeaderInflater _protocolMessageHeaderParser = new BitcoinProtocolMessageHeaderInflater();
    private final Map<MessageType, BitcoinProtocolMessageInflater> _commandInflaterMap = new HashMap<MessageType, BitcoinProtocolMessageInflater>();

    public BitcoinProtocolMessageFactory() {
        _commandInflaterMap.put(MessageType.SYNCHRONIZE_VERSION, new BitcoinSynchronizeVersionMessageInflater());
        _commandInflaterMap.put(MessageType.ACKNOWLEDGE_VERSION, new BitcoinAcknowledgeVersionMessageInflater());
        _commandInflaterMap.put(MessageType.PING, new BitcoinPingMessageInflater());
        _commandInflaterMap.put(MessageType.PONG, new BitcoinPongMessageInflater());
        _commandInflaterMap.put(MessageType.NODE_ADDRESSES, new NodeIpAddressMessageInflater());
        _commandInflaterMap.put(MessageType.QUERY_BLOCK_HEADERS, new QueryBlockHeadersMessageInflater());
        _commandInflaterMap.put(MessageType.QUERY_BLOCK_HEADERS_RESPONSE, new QueryBlockHeadersResponseMessageInflater());
        _commandInflaterMap.put(MessageType.QUERY_BLOCKS, new QueryBlocksMessageInflater());
        _commandInflaterMap.put(MessageType.ERROR, new ErrorMessageInflater());
        _commandInflaterMap.put(MessageType.QUERY_RESPONSE, new QueryResponseMessageInflater());
        _commandInflaterMap.put(MessageType.BLOCK, new BlockMessageInflater());
        _commandInflaterMap.put(MessageType.ENABLE_NEW_BLOCKS_VIA_HEADERS, new NewBlocksViaHeadersMessageInflater());
        _commandInflaterMap.put(MessageType.REQUEST_DATA, new RequestDataMessageInflater());
    }

    public BitcoinProtocolMessage fromBytes(final byte[] bytes) {
        final BitcoinProtocolMessageHeader protocolMessageHeader = _protocolMessageHeaderParser.fromBytes(bytes);
        if (protocolMessageHeader == null) { return null; }

        final BitcoinProtocolMessageInflater protocolMessageInflater = _commandInflaterMap.get(protocolMessageHeader.command);
        if (protocolMessageInflater == null) {
            Logger.log("NOTICE: Unsupported message command. 0x"+ HexUtil.toHexString(ByteUtil.copyBytes(bytes, 0, BitcoinProtocolMessageHeaderInflater.HEADER_BYTE_COUNT)));
            return null;
        }

        return protocolMessageInflater.fromBytes(bytes);
    }
}
