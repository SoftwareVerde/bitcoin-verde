package com.softwareverde.network.socket;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.network.p2p.message.ProtocolMessageFactory;
import com.softwareverde.network.p2p.message.ProtocolMessageHeaderInflater;

public class BinaryPacketFormat implements Const {
    protected final ByteArray _magicNumber;
    protected final ProtocolMessageHeaderInflater _protocolMessageHeaderInflater;
    protected final ProtocolMessageFactory<?> _protocolMessageFactory;

    public BinaryPacketFormat(final ByteArray magicNumber, final ProtocolMessageHeaderInflater protocolMessageHeaderInflater, final ProtocolMessageFactory<?> protocolMessageFactory) {
        _magicNumber = magicNumber;
        _protocolMessageHeaderInflater = protocolMessageHeaderInflater;
        _protocolMessageFactory = protocolMessageFactory;
    }

    public ByteArray getMagicNumber() {
        return _magicNumber;
    }

    public ProtocolMessageHeaderInflater getProtocolMessageHeaderInflater() {
        return _protocolMessageHeaderInflater;
    }

    public ProtocolMessageFactory<?> getProtocolMessageFactory() {
        return _protocolMessageFactory;
    }
}
