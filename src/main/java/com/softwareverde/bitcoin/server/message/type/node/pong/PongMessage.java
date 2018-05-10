package com.softwareverde.bitcoin.server.message.type.node.pong;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class PongMessage extends BitcoinProtocolMessage {

    protected Long _nonce;

    public PongMessage() {
        super(MessageType.PONG);

        _nonce = (long) (Math.random() * Long.MAX_VALUE);
    }

    public void setNonce(final Long nonce) {
        _nonce = nonce;
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
