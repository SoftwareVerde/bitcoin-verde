package com.softwareverde.bitcoin.server.socket;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageFactory;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageHeaderParser;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.util.Util;

import java.util.LinkedList;
import java.util.List;

public class BitcoinProtocolMessagePacketBuffer {
    public static class ByteArray {
        public byte[] bytes;
        public int startIndex;
        public int byteCount;

        public ByteArray(final byte[] bytes, final int startIndex, final int byteCount) {
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

    private int _bufferSize = 1024;
    private final List<ByteArray> _recycledByteArrays = new LinkedList<ByteArray>();
    private final List<ByteArray> _byteArrayList = new LinkedList<ByteArray>();
    private final ProtocolMessageHeaderParser _protocolMessageHeaderParser = new ProtocolMessageHeaderParser();
    private final ProtocolMessageFactory _protocolMessageFactory = new ProtocolMessageFactory();
    private int _byteCount = 0;

    private byte[] _readContiguousBytes(final int desiredByteCount, final boolean shouldConsumeBytes) {
        final byte[] bytes = new byte[desiredByteCount];

        // Make a copy of the list when modifying to prevent concurrent-modification...
        final List<ByteArray> byteArrayList = (shouldConsumeBytes ? Util.copyList(_byteArrayList) : _byteArrayList);

        int byteCount = 0;
        for (final ByteArray byteArray : byteArrayList) {
            final int byteCountFromThisArray = Math.min((desiredByteCount - byteCount), byteArray.byteCount);
            for (int i=0; i<byteCountFromThisArray; ++i) {
                bytes[byteCount + i] = byteArray.bytes[byteArray.startIndex + i];
            }
            byteCount += byteCountFromThisArray;

            if (shouldConsumeBytes) {
                byteArray.markBytesConsumed(byteCountFromThisArray);
                if (! byteArray.hasBytesRemaining()) {
                    _byteArrayList.remove(0);
                    _recycledByteArrays.add(byteArray);
                }
            }

            if (byteCount >= desiredByteCount) { break; }
        }

        if (shouldConsumeBytes) {
            _byteCount -= byteCount;
        }

        return bytes;
    }

    private byte[] _peakContiguousBytes(final int desiredByteCount) {
        return _readContiguousBytes(desiredByteCount, false);
    }

    private byte[] _consumeContiguousBytes(final int desiredByteCount) {
        return _readContiguousBytes(desiredByteCount, true);
    }

    private ProtocolMessageHeader _peakProtocolHeader() {
        if (_byteCount < ProtocolMessageHeaderParser.HEADER_BYTE_COUNT) { return null; }

        final byte[] packetHeader = _peakContiguousBytes(ProtocolMessageHeaderParser.HEADER_BYTE_COUNT);
        return _protocolMessageHeaderParser.fromBytes(packetHeader);
    }

    public BitcoinProtocolMessagePacketBuffer() { }

    public void setBufferSize(final int bufferSize) {
        _bufferSize = bufferSize;
    }

    /**
     * Appends byteBuffer to the PacketBuffer.
     *  - byteBuffer is not copied and is used as a part of the internal representation of this class;
     *      therefore, it is important that any byte[] fed into appendBytes() is not used again outside of this invocation.
     *  - byteBuffer may be kept in memory indefinitely and recycled via getRecycledBuffer().
     *  - byteCount is used to specify the endIndex of byteBuffer.
     */
    public void appendBytes(final byte[] byteBuffer, final int byteCount) {
        // if (byteCount > bytes.length) { throw new RuntimeException("Invalid byteCount. Attempted to add more bytes than was available within byte array."); }
        final int safeByteCount = Math.min(byteBuffer.length, byteCount);

        _byteArrayList.add(new ByteArray(byteBuffer, 0, safeByteCount));
        _byteCount += safeByteCount;
    }

    public byte[] getRecycledBuffer() {
        if (_recycledByteArrays.isEmpty()) {
            return new byte[_bufferSize];
        }

        final ByteArray byteArray = _recycledByteArrays.remove(0);
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
        return (_byteCount >= protocolMessageHeader.payloadByteCount);
    }

    public ProtocolMessage popMessage() {
        final ProtocolMessageHeader protocolMessageHeader = _peakProtocolHeader();
        if (protocolMessageHeader == null) { return null; }
        if (_byteCount < protocolMessageHeader.payloadByteCount) { return null; }

        final int fullPacketLength = (ProtocolMessageHeaderParser.HEADER_BYTE_COUNT + protocolMessageHeader.payloadByteCount);
        final byte[] fullPacket = _consumeContiguousBytes(fullPacketLength);
        return _protocolMessageFactory.inflateMessage(fullPacket);
    }
}