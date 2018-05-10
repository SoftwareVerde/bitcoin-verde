package com.softwareverde.network.socket;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.network.p2p.message.ProtocolMessageHeaderInflater;
import com.softwareverde.network.p2p.message.ProtocolMessageInflater;

public class BinaryPacketFormat implements Const {
    public final ByteArray magicNumber;
    public final ProtocolMessageHeaderInflater protocolMessageHeaderInflater;
    public final ProtocolMessageInflater protocolMessageInflater;

    public BinaryPacketFormat(final ByteArray magicNumber, final ProtocolMessageHeaderInflater protocolMessageHeaderInflater, final ProtocolMessageInflater protocolMessageInflater) {
        this.magicNumber = magicNumber;
        this.protocolMessageHeaderInflater = protocolMessageHeaderInflater;
        this.protocolMessageInflater = protocolMessageInflater;
    }
}
