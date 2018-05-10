package com.softwareverde.bitcoin.server.message.header;

import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.network.p2p.message.ProtocolMessageHeader;

public class BitcoinProtocolMessageHeader implements ProtocolMessageHeader {
    public final byte[] magicNumber;
    public final MessageType command;
    public final int payloadByteCount;
    public final byte[] payloadChecksum;

    public BitcoinProtocolMessageHeader(final byte[] magicNumber, final MessageType command, final int payloadByteCount, final byte[] payloadChecksum) {
        this.magicNumber = magicNumber;
        this.command = command;
        this.payloadByteCount = payloadByteCount;
        this.payloadChecksum = payloadChecksum;
    }
}