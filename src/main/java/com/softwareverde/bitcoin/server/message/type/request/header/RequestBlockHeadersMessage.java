package com.softwareverde.bitcoin.server.message.type.request.header;

import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class RequestBlockHeadersMessage extends BitcoinProtocolMessage {
    public static Integer MAX_BLOCK_HEADER_HASH_COUNT = 2000; // NOTE: This value is a "not-to-exceed"...

    protected Integer _version;
    protected final MutableList<Sha256Hash> _blockHeaderHashes = new MutableList<Sha256Hash>();
    protected MutableSha256Hash _stopBeforeBlockHash = new MutableSha256Hash();

    public RequestBlockHeadersMessage() {
        super(MessageType.REQUEST_BLOCK_HEADERS);
        _version = Constants.PROTOCOL_VERSION;
    }

    public Integer getVersion() { return _version; }

    public void addBlockHeaderHash(final Sha256Hash blockHeaderHash) {
        if (_blockHeaderHashes.getSize() >= MAX_BLOCK_HEADER_HASH_COUNT) { return; }
        _blockHeaderHashes.add(blockHeaderHash);
    }

    public void clearBlockHeaderHashes() {
        _blockHeaderHashes.clear();
    }

    public List<Sha256Hash> getBlockHeaderHashes() {
        return _blockHeaderHashes;
    }

    public Sha256Hash getStopBeforeBlockHash() {
        return _stopBeforeBlockHash;
    }

    public void setStopBeforeBlockHash(final Sha256Hash blockHeaderHash) {
        _stopBeforeBlockHash.setBytes(blockHeaderHash);
    }

    @Override
    protected ByteArray _getPayload() {
        final int blockHeaderCount = _blockHeaderHashes.getSize();
        final int blockHeaderHashByteCount = 32;

        final byte[] versionBytes = ByteUtil.integerToBytes(_version);
        final byte[] blockHeaderCountBytes = ByteUtil.variableLengthIntegerToBytes(blockHeaderCount);
        final byte[] blockHeaderHashesBytes = new byte[blockHeaderHashByteCount * blockHeaderCount];

        for (int i=0; i<blockHeaderCount; ++i) {
            final Sha256Hash blockHeaderHash = _blockHeaderHashes.get(i);
            final int startIndex = (blockHeaderHashByteCount * i);
            ByteUtil.setBytes(blockHeaderHashesBytes, blockHeaderHash.getBytes(), startIndex);
        }

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(versionBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(blockHeaderCountBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(blockHeaderHashesBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(_stopBeforeBlockHash, Endian.LITTLE);
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
