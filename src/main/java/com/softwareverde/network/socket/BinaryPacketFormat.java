package com.softwareverde.network.socket;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.network.p2p.message.ProtocolMessageFactory;
import com.softwareverde.network.p2p.message.ProtocolMessageHeaderInflater;

public class BinaryPacketFormat implements Const {
    public final ByteArray magicNumber;
    public final ProtocolMessageHeaderInflater protocolMessageHeaderInflater;
    public final ProtocolMessageFactory _protocolMessageFactory;

    public BinaryPacketFormat(final ByteArray magicNumber, final ProtocolMessageHeaderInflater protocolMessageHeaderInflater, final ProtocolMessageFactory protocolMessageFactory) {
        this.magicNumber = magicNumber;
        this.protocolMessageHeaderInflater = protocolMessageHeaderInflater;
        this._protocolMessageFactory = protocolMessageFactory;
    }
}
