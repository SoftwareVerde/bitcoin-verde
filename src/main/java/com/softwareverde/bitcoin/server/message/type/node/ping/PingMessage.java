package com.softwareverde.bitcoin.server.message.type.node.ping;

import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.type.bytearray.ByteArray;
import com.softwareverde.bitcoin.type.bytearray.MutableByteArray;
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
    protected ByteArray _getPayload() {

        final byte[] nonce = new byte[8];
        ByteUtil.setBytes(nonce, ByteUtil.longToBytes(_nonce));

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(nonce, Endian.LITTLE);
        return new MutableByteArray(byteArrayBuilder.build());
    }
}
