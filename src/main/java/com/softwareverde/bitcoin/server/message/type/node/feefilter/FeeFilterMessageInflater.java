package com.softwareverde.bitcoin.server.message.type.node.feefilter;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class FeeFilterMessageInflater extends BitcoinProtocolMessageInflater {

    @Override
    public FeeFilterMessage fromBytes(final byte[] bytes) {
        final FeeFilterMessage feeFilterMessageMessage = new FeeFilterMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.FEE_FILTER);
        if (protocolMessageHeader == null) { return null; }

        feeFilterMessageMessage._minimumSatoshisPerByte = byteArrayReader.readLong(8, Endian.LITTLE);

        if (byteArrayReader.didOverflow()) { return null; }

        return feeFilterMessageMessage;
    }
}
