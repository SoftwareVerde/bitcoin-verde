package com.softwareverde.bitcoin.server.message.type.query.block;

import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.Util;

import java.util.ArrayList;
import java.util.List;

public class QueryBlocksMessage extends ProtocolMessage {
    public static Integer MAX_BLOCK_HEADER_HASH_COUNT = 500;

    protected Integer _version;
    protected final List<Sha256Hash> _blockHeaderHashes = new ArrayList<Sha256Hash>();
    protected Sha256Hash _desiredBlockHeaderHash = new MutableSha256Hash();

    public QueryBlocksMessage() {
        super(MessageType.QUERY_BLOCKS);
        _version = Constants.PROTOCOL_VERSION;
    }

    public Integer getVersion() { return _version; }

    public void addBlockHeaderHash(final Sha256Hash blockHeaderHash) {
        if (_blockHeaderHashes.size() >= MAX_BLOCK_HEADER_HASH_COUNT) { return; }
        _blockHeaderHashes.add(blockHeaderHash);
    }

    public void clearBlockHeaderHashes() {
        _blockHeaderHashes.clear();
    }

    public List<Sha256Hash> getBlockHeaderHashes() {
        return Util.copyList(_blockHeaderHashes);
    }

    public Sha256Hash getDesiredBlockHeaderHash() {
        return new ImmutableSha256Hash(_desiredBlockHeaderHash);
    }

    public void setDesiredBlockHeaderHash(final Sha256Hash blockHeaderHash) {
        _desiredBlockHeaderHash = blockHeaderHash;
    }

    @Override
    protected ByteArray _getPayload() {
        final int blockHeaderCount = _blockHeaderHashes.size();
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
        byteArrayBuilder.appendBytes(_desiredBlockHeaderHash.getBytes(), Endian.LITTLE);
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
