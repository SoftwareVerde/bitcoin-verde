package com.softwareverde.bitcoin.server.socket.message.ping;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class PingMessage extends ProtocolMessage {

    protected Long _nonce;

    public PingMessage() {
        super(MessageType.PING);

        _nonce = (long) (Math.random() * Long.MAX_VALUE);
    }

    public Long getNonce() { return _nonce; }

    @Override
    protected byte[] _getPayload() {

        final byte[] nonce = new byte[8];
        ByteUtil.setBytes(nonce, ByteUtil.longToBytes(_nonce));

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(nonce, Endian.LITTLE);
        return byteArrayBuilder.build();
    }
}
