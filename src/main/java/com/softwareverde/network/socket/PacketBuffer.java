package com.softwareverde.network.socket;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.message.ProtocolMessageFactory;
import com.softwareverde.network.p2p.message.ProtocolMessageHeader;
import com.softwareverde.network.p2p.message.ProtocolMessageHeaderInflater;
import com.softwareverde.util.ByteBuffer;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

public class PacketBuffer extends ByteBuffer {
    protected final ByteArray _reverseEndianMagicNumber;
    protected final ProtocolMessageHeaderInflater _protocolMessageHeaderInflater;
    protected final ProtocolMessageFactory<?> _protocolMessageFactory;

    @Override
    protected boolean _shouldAllowNewBuffer(final byte[] byteBuffer, final int byteCount) {
        final boolean shouldAllowNewBuffer = super._shouldAllowNewBuffer(byteBuffer, byteCount);
        if (! shouldAllowNewBuffer) {
            Logger.warn("Packet buffer exceeded max size, clearing buffer.");
            _resetBuffer();
        }

        return true;
    }

    protected ProtocolMessageHeader _peakProtocolHeader() {
        final int headerByteCount = _protocolMessageHeaderInflater.getHeaderByteCount();

        if (_byteCount < headerByteCount) { return null; }

        final byte[] packetHeader = _peakContiguousBytes(headerByteCount);
        return _protocolMessageHeaderInflater.fromBytes(packetHeader);
    }

    public PacketBuffer(final BinaryPacketFormat binaryPacketFormat) {
        final ByteArray magicNumber = binaryPacketFormat.getMagicNumber();
        _reverseEndianMagicNumber = magicNumber.toReverseEndian();
        _protocolMessageHeaderInflater = binaryPacketFormat.getProtocolMessageHeaderInflater();
        _protocolMessageFactory = binaryPacketFormat.getProtocolMessageFactory();
    }

    public boolean hasMessage() {
        final ProtocolMessageHeader protocolMessageHeader = _peakProtocolHeader();
        if (protocolMessageHeader == null) { return false; }
        final int expectedMessageLength = (protocolMessageHeader.getPayloadByteCount() + _protocolMessageHeaderInflater.getHeaderByteCount());
        return (_byteCount >= expectedMessageLength);
    }

    public void evictCorruptedPackets() {
        final int magicNumberByteCount = _reverseEndianMagicNumber.getByteCount();
        if (magicNumberByteCount <= 0) { return; }

        final int headerByteCount = _protocolMessageHeaderInflater.getHeaderByteCount();
        if (headerByteCount <= 0) { return; }

        while (_byteCount > 0) {
            final byte[] bytes = _peakContiguousBytes(Math.min(magicNumberByteCount, _byteCount));
            boolean matched = true;
            for (int i = 0; i < bytes.length; ++i) {
                final byte requiredByte = _reverseEndianMagicNumber.getByte(i);
                final byte foundByte = bytes[i];

                if (foundByte != requiredByte) {
                    final byte[] discardedBytes = _consumeContiguousBytes(i + 1);
                    Logger.trace("Discarded: " + HexUtil.toHexString(discardedBytes));
                    matched = false;
                    break;
                }
            }
            if (matched) { break; }
        }
    }

    public ProtocolMessage popMessage() {
        final ProtocolMessageHeader protocolMessageHeader = _peakProtocolHeader();
        if (protocolMessageHeader == null) { return null; }

        final int headerByteCount  = _protocolMessageHeaderInflater.getHeaderByteCount();
        final int payloadByteCount = protocolMessageHeader.getPayloadByteCount();

        if (_byteCount < payloadByteCount) {
            Logger.debug("PacketBuffer.popMessage: Insufficient byte count.");
            return null;
        }

        final int fullPacketByteCount = (headerByteCount + payloadByteCount);

        final byte[] fullPacket = _consumeContiguousBytes(fullPacketByteCount);

        if (fullPacketByteCount > Util.coalesce(_protocolMessageHeaderInflater.getMaxPacketByteCount(protocolMessageHeader), Integer.MAX_VALUE)) {
            Logger.debug("Dropping packet. Packet exceeded max byte count: " + fullPacketByteCount);
            return null;
        }

        final ProtocolMessage protocolMessage = _protocolMessageFactory.fromBytes(fullPacket);
        if (protocolMessage == null) {
            Logger.debug("Error inflating message: " + HexUtil.toHexString(ByteUtil.copyBytes(fullPacket, 0, Math.min(fullPacket.length, 128))) + " (+"+ ( (fullPacket.length > 128) ? (fullPacket.length - 128) : 0 ) +" bytes)");
        }

        return protocolMessage;
    }
}