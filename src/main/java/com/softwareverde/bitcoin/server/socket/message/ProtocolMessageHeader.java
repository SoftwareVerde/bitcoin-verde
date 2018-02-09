package com.softwareverde.bitcoin.server.socket.message;

public class ProtocolMessageHeader {
    public final byte[] magicNumber;
    public final ProtocolMessage.Command command;
    public final int payloadByteCount;
    public final byte[] payloadChecksum;

    public ProtocolMessageHeader(final byte[] magicNumber, final ProtocolMessage.Command command, final int payloadByteCount, final byte[] payloadChecksum) {
        this.magicNumber = magicNumber;
        this.command = command;
        this.payloadByteCount = payloadByteCount;
        this.payloadChecksum = payloadChecksum;
    }
}