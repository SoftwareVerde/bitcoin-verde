package com.softwareverde.bitcoin.server.message.type.slp;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class EnableSlpTransactionsMessage extends BitcoinProtocolMessage {
    protected Boolean _isEnabled;

    public EnableSlpTransactionsMessage() {
        super(MessageType.ENABLE_SLP_TRANSACTIONS);
    }

    public Boolean isEnabled() {
        return _isEnabled;
    }

    public void setIsEnabled(final Boolean enabled) {
        _isEnabled = enabled;
    }

    @Override
    protected ByteArray _getPayload() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(ByteUtil.integerToBytes((_isEnabled ? 1 : 0)), Endian.LITTLE);
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
