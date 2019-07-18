package com.softwareverde.bitcoin.server.message;

import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeaderInflater;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.network.socket.BinaryPacketFormat;

public class BitcoinBinaryPacketFormat extends BinaryPacketFormat {
    public BitcoinBinaryPacketFormat(final ByteArray magicNumber, final BitcoinProtocolMessageHeaderInflater protocolMessageHeaderInflater, final BitcoinProtocolMessageFactory protocolMessageFactory) {
        super(magicNumber, protocolMessageHeaderInflater, protocolMessageFactory);
    }

    @Override
    public BitcoinProtocolMessageHeaderInflater getProtocolMessageHeaderInflater() {
        return (BitcoinProtocolMessageHeaderInflater) _protocolMessageHeaderInflater;
    }

    @Override
    public BitcoinProtocolMessageFactory getProtocolMessageFactory() {
        return (BitcoinProtocolMessageFactory) _protocolMessageFactory;
    }
}
