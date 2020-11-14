package com.softwareverde.bitcoin.server.message.type.request.header;

import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.MutableSha256Hash;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class RequestBlockHeadersMessage extends BitcoinProtocolMessage {
    public static Integer MAX_BLOCK_HEADER_HASH_COUNT = 2000; // NOTE: This value is a "not-to-exceed"...

    protected Integer _version;
    protected final MutableList<Sha256Hash> _blockHashes = new MutableList<Sha256Hash>();
    protected final MutableSha256Hash _stopBeforeBlockHash = new MutableSha256Hash();

    public RequestBlockHeadersMessage() {
        super(MessageType.REQUEST_BLOCK_HEADERS);
        _version = BitcoinConstants.getProtocolVersion();
    }

    public Integer getVersion() { return _version; }

    public void addBlockHash(final Sha256Hash blockHash) {
        if (_blockHashes.getCount() >= MAX_BLOCK_HEADER_HASH_COUNT) { return; }
        if (blockHash == null) { return; }
        _blockHashes.add(blockHash);
    }

    public void clearBlockHashes() {
        _blockHashes.clear();
    }

    public List<Sha256Hash> getBlockHashes() {
        return _blockHashes;
    }

    public Sha256Hash getStopBeforeBlockHash() {
        return _stopBeforeBlockHash;
    }

    public void setStopBeforeBlockHash(final Sha256Hash blockHash) {
        if (blockHash == null) {
            _stopBeforeBlockHash.setBytes(Sha256Hash.EMPTY_HASH);
            return;
        }

        _stopBeforeBlockHash.setBytes(blockHash);
    }

    @Override
    protected ByteArray _getPayload() {
        final int blockHeaderCount = _blockHashes.getCount();
        final int blockHashByteCount = Sha256Hash.BYTE_COUNT;

        final byte[] versionBytes = ByteUtil.integerToBytes(_version);
        final byte[] blockHeaderCountBytes = ByteUtil.variableLengthIntegerToBytes(blockHeaderCount);
        final byte[] blockHashesBytes = new byte[blockHashByteCount * blockHeaderCount];

        for (int i = 0; i < blockHeaderCount; ++i) {
            final Sha256Hash blockHash = _blockHashes.get(i);
            final int startIndex = (blockHashByteCount * i);
            final ByteArray littleEndianBlockHash = blockHash.toReversedEndian();
            ByteUtil.setBytes(blockHashesBytes, littleEndianBlockHash.getBytes(), startIndex);
        }

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(versionBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(blockHeaderCountBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(blockHashesBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(_stopBeforeBlockHash, Endian.LITTLE);
        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        final int blockHeaderCount = _blockHashes.getCount();

        final byte[] blockHeaderCountBytes = ByteUtil.variableLengthIntegerToBytes(blockHeaderCount);
        return (4 + blockHeaderCountBytes.length + (Sha256Hash.BYTE_COUNT * (blockHeaderCount + 1)));
    }
}
