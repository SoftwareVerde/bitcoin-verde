package com.softwareverde.bitcoin.server.message.type.node.feefilter;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class FeeFilterMessage extends BitcoinProtocolMessage {

    protected Long _minimumSatoshisPerByte;

    public FeeFilterMessage() {
        super(MessageType.FEE_FILTER);
        _minimumSatoshisPerByte = 0L;
    }

    public Long getMinimumSatoshisPerByte() { return _minimumSatoshisPerByte; }

    public void setMinimumSatoshisPerByte(final Long minimumSatoshisPerByte) {
        _minimumSatoshisPerByte = minimumSatoshisPerByte;
    }

    @Override
    protected ByteArray _getPayload() {
        final byte[] feeFilterBytes = ByteUtil.longToBytes(_minimumSatoshisPerByte);

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(feeFilterBytes, Endian.LITTLE);
        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        return 4;
    }
}
