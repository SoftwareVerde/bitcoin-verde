package com.softwareverde.bitcoin.server.message.type.compact;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class EnableCompactBlocksMessage extends BitcoinProtocolMessage {
    protected Boolean _isEnabled;
    protected Integer _version;

    public EnableCompactBlocksMessage() {
        super(MessageType.ENABLE_COMPACT_BLOCKS);
    }

    public Boolean isEnabled() {
        return _isEnabled;
    }

    public Integer getVersion() {
        return _version;
    }

    public void setIsEnabled(final Boolean isEnabled) {
        _isEnabled = isEnabled;
    }

    public void setVersion(final Integer version) {
        _version = version;
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(ByteUtil.integerToBytes((_isEnabled ? 1 : 0)), Endian.LITTLE);
        byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(_version), Endian.LITTLE);
        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        return 8;
    }
}
