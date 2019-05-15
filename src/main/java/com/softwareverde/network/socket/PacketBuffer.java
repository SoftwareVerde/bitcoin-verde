package com.softwareverde.network.socket;

import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.message.ProtocolMessageFactory;
import com.softwareverde.network.p2p.message.ProtocolMessageHeader;
import com.softwareverde.network.p2p.message.ProtocolMessageHeaderInflater;
import com.softwareverde.util.ByteBuffer;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

public class PacketBuffer extends ByteBuffer {
    protected final int _mainNetMagicNumberByteCount;
    protected final byte[] _reversedMainNetMagicNumber;

    protected final ProtocolMessageHeaderInflater _protocolMessageHeaderInflater;
    protected final ProtocolMessageFactory _protocolMessageFactory;

    protected final byte[] _packetStartingBytesBuffer;

    protected ProtocolMessageHeader _peakProtocolHeader() {
        final int headerByteCount = _protocolMessageHeaderInflater.getHeaderByteCount();

        if (_byteCount < headerByteCount) { return null; }

        final byte[] packetHeader = _peakContiguousBytes(headerByteCount);
        return _protocolMessageHeaderInflater.fromBytes(packetHeader);
    }

    public PacketBuffer(final BinaryPacketFormat binaryPacketFormat) {
        final int magicNumberByteCount = binaryPacketFormat.magicNumber.getByteCount();
        _mainNetMagicNumberByteCount = magicNumberByteCount;
        _packetStartingBytesBuffer = new byte[magicNumberByteCount];
        _reversedMainNetMagicNumber = ByteUtil.reverseEndian(binaryPacketFormat.magicNumber.getBytes());

        _protocolMessageHeaderInflater = binaryPacketFormat.protocolMessageHeaderInflater;
        _protocolMessageFactory = binaryPacketFormat.protocolMessageFactory;
    }

    public boolean hasMessage() {
        final ProtocolMessageHeader protocolMessageHeader = _peakProtocolHeader();
        if (protocolMessageHeader == null) { return false; }
        final Integer expectedMessageLength = (protocolMessageHeader.getPayloadByteCount() + _protocolMessageHeaderInflater.getHeaderByteCount());
        return (_byteCount >= expectedMessageLength);
    }

    public ProtocolMessage popMessage() {
        final ProtocolMessageHeader protocolMessageHeader = _peakProtocolHeader();
        if (protocolMessageHeader == null) { return null; }

        final int headerByteCount  = _protocolMessageHeaderInflater.getHeaderByteCount();
        final int payloadByteCount = protocolMessageHeader.getPayloadByteCount();

        if (_byteCount < payloadByteCount) {
            Logger.log("NOTICE: PacketBuffer.popMessage: Insufficient byte count.");
            return null;
        }

        final int fullPacketByteCount = (headerByteCount + payloadByteCount);

        final byte[] fullPacket = _consumeContiguousBytes(fullPacketByteCount);

        if (fullPacketByteCount > Util.coalesce(_protocolMessageHeaderInflater.getMaxPacketByteCount(), Integer.MAX_VALUE)) {
            Logger.log("IO: Dropping packet. Packet exceeded max byte count: " + fullPacketByteCount);
            return null;
        }

        final ProtocolMessage protocolMessage = _protocolMessageFactory.fromBytes(fullPacket);
        if (protocolMessage == null) {
            Logger.log("NOTICE: Error inflating message: " + HexUtil.toHexString(ByteUtil.copyBytes(fullPacket, 0, Math.min(fullPacket.length, 128))) + " (+"+ ( (fullPacket.length > 128) ? (fullPacket.length - 128) : 0 ) +" bytes)");
        }

        return protocolMessage;
    }
}