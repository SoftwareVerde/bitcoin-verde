package com.softwareverde.network.socket;

import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.message.ProtocolMessageFactory;
import com.softwareverde.network.p2p.message.ProtocolMessageHeader;
import com.softwareverde.network.p2p.message.ProtocolMessageHeaderInflater;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.util.LinkedList;
import java.util.List;

public class PacketBuffer {
    protected static class ByteBuffer {
        public byte[] bytes;
        public int startIndex;
        public int byteCount;

        public ByteBuffer(final byte[] bytes, final int startIndex, final int byteCount) {
            this.bytes = bytes;
            this.startIndex = startIndex;
            this.byteCount = byteCount;
        }

        public void markBytesConsumed(final int byteCount) {
            this.byteCount -= byteCount;
            this.startIndex += byteCount;
        }

        public boolean hasBytesRemaining() {
            return (this.byteCount > 0);
        }
    }

    protected final int _mainNetMagicNumberByteCount;
    protected final byte[] _reversedMainNetMagicNumber;

    protected int _bufferSize = 1024;
    protected final LinkedList<ByteBuffer> _recycledByteArrays = new LinkedList<ByteBuffer>();
    protected final LinkedList<ByteBuffer> _byteArrayList = new LinkedList<ByteBuffer>();
    protected final ProtocolMessageHeaderInflater _protocolMessageHeaderInflater;
    protected final ProtocolMessageFactory _protocolMessageFactory;
    protected int _byteCount = 0;

    protected final byte[] _packetStartingBytesBuffer;

    protected byte[] _readContiguousBytes(final int desiredByteCount, final boolean shouldConsumeBytes) {
        final byte[] bytes = new byte[desiredByteCount];

        // Make a copy of the list when modifying to prevent concurrent-modification...
        final List<ByteBuffer> byteArrayList = (shouldConsumeBytes ? Util.copyList(_byteArrayList) : _byteArrayList);

        int byteCount = 0;
        for (final ByteBuffer byteArray : byteArrayList) {
            final int byteCountFromThisArray = Math.min((desiredByteCount - byteCount), byteArray.byteCount);
            for (int i=0; i<byteCountFromThisArray; ++i) {
                bytes[byteCount + i] = byteArray.bytes[byteArray.startIndex + i];
            }
            byteCount += byteCountFromThisArray;

            if (shouldConsumeBytes) {
                byteArray.markBytesConsumed(byteCountFromThisArray);
                if (! byteArray.hasBytesRemaining()) {
                    _byteArrayList.removeFirst();
                    _recycledByteArrays.addLast(byteArray);
                }
            }

            if (byteCount >= desiredByteCount) { break; }
        }

        if (shouldConsumeBytes) {
            _byteCount -= byteCount;
        }

        return bytes;
    }

    protected byte[] _peakContiguousBytes(final int desiredByteCount) {
        return _readContiguousBytes(desiredByteCount, false);
    }

    protected byte[] _consumeContiguousBytes(final int desiredByteCount) {
        return _readContiguousBytes(desiredByteCount, true);
    }

    protected ProtocolMessageHeader _peakProtocolHeader() {
        final int headerByteCount = _protocolMessageHeaderInflater.getHeaderByteCount();

        if (_byteCount < headerByteCount) { return null; }

        final byte[] packetHeader = _peakContiguousBytes(headerByteCount);
        return _protocolMessageHeaderInflater.fromBytes(packetHeader);
    }

    protected void _resetBuffer() {
        final byte[] discardedPacket = _readContiguousBytes(_byteCount, true);
        Logger.log("IO: DISCARDED PACKET: "+ HexUtil.toHexString(discardedPacket));
    }

    public PacketBuffer(final BinaryPacketFormat binaryPacketFormat) {
        final int magicNumberByteCount = binaryPacketFormat.magicNumber.getByteCount();
        _mainNetMagicNumberByteCount = magicNumberByteCount;
        _packetStartingBytesBuffer = new byte[magicNumberByteCount];
        _reversedMainNetMagicNumber = ByteUtil.reverseEndian(binaryPacketFormat.magicNumber.getBytes());

        _protocolMessageHeaderInflater = binaryPacketFormat.protocolMessageHeaderInflater;
        _protocolMessageFactory = binaryPacketFormat.protocolMessageFactory;
    }

    public void setBufferSize(final int bufferSize) {
        _bufferSize = bufferSize;
    }

    public Integer getBufferSize() {
        return _bufferSize;
    }

    /**
     * Appends byteBuffer to the PacketBuffer.
     *  - byteBuffer is not copied and is used as a part of the internal representation of this class;
     *      therefore, it is important that any byte[] fed into appendBytes() is not used again outside of this invocation.
     *  - byteBuffer may be kept in memory indefinitely and recycled via getRecycledBuffer().
     *  - byteCount is used to specify the endIndex of byteBuffer.
     *  - if byteBuffer's bytes begin with ProtocolMessage.MAIN_NET_MAGIC_NUMBER, then the previous non-message packets
     *      are assumed to be corrupted, and are discarded.
     */
    public void appendBytes(final byte[] byteBuffer, final int byteCount) {
        // if (byteCount > bytes.length) { throw new RuntimeException("Invalid byteCount. Attempted to add more bytes than was available within byte array."); }
        final int safeByteCount = Math.min(byteBuffer.length, byteCount);

        if (_byteCount > 0) {
            if (safeByteCount >= _mainNetMagicNumberByteCount) {
                ByteUtil.setBytes(_packetStartingBytesBuffer, byteBuffer);
                final Boolean startsWithMagicNumber = (ByteUtil.areEqual(_packetStartingBytesBuffer, _reversedMainNetMagicNumber));
                if (startsWithMagicNumber) {
                    _resetBuffer();
                }
            }
        }

        _byteArrayList.addLast(new ByteBuffer(byteBuffer, 0, safeByteCount));
        _byteCount += safeByteCount;
    }

    public byte[] getRecycledBuffer() {
        if (_recycledByteArrays.isEmpty()) {
            return new byte[_bufferSize];
        }

        final ByteBuffer byteArray = _recycledByteArrays.removeFirst();
        return byteArray.bytes;
    }

    public int getByteCount() {
        return _byteCount;
    }

    public int getBufferCount() {
        return (_recycledByteArrays.size() + _byteArrayList.size());
    }

    public byte[] readBytes(final int byteCount) {
        return _consumeContiguousBytes(byteCount);
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

        if (_byteCount < payloadByteCount) { return null; }

        final int fullPacketByteCount = (headerByteCount + payloadByteCount);

        final byte[] fullPacket = _consumeContiguousBytes(fullPacketByteCount);

        if (fullPacketByteCount > _protocolMessageHeaderInflater.getMaxPacketByteCount()) {
            Logger.log("IO: Dropping packet. Packet exceeded max byte count: " + fullPacketByteCount);
            return null;
        }

        return _protocolMessageFactory.fromBytes(fullPacket);
    }
}