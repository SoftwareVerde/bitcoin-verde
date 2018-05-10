package com.softwareverde.bitcoin.server.message.type.node.ping;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.network.p2p.message.type.PingMessage;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class BitcoinPingMessage extends BitcoinProtocolMessage implements PingMessage {

    protected Long _nonce;

    public BitcoinPingMessage() {
        super(MessageType.PING);

        _nonce = (long) (Math.random() * Long.MAX_VALUE);
    }

    public Long getNonce() { return _nonce; }

    @Override
    protected ByteArray _getPayload() {

        final byte[] nonce = new byte[8];
        ByteUtil.setBytes(nonce, ByteUtil.longToBytes(_nonce));

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(nonce, Endian.LITTLE);
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
