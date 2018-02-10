package com.softwareverde.bitcoin.server.socket.message.block.header;

import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;
import com.softwareverde.util.Util;

import java.util.ArrayList;
import java.util.List;

public class BlockHeadersMessage extends ProtocolMessage {
    protected Integer _version;
    protected List<byte[]> _blockHeaderHashes = new ArrayList<byte[]>();
    protected byte[] _desiredBlockHeaderHash;

    public BlockHeadersMessage() {
        super(MessageType.BLOCK_HEADERS);
        _version = Constants.PROTOCOL_VERSION;
    }

    public Integer getVersion() { return _version; }

    public void addBlockHeaderHash(final byte[] blockHeaderHash) {
        _blockHeaderHashes.add(blockHeaderHash);
    }

    public void clearBlockHeaderHashes() {
        _blockHeaderHashes.clear();
    }

    public List<byte[]> getBlockHeaderHashes() {
        return Util.copyList(_blockHeaderHashes);
    }

    public byte[] getDesiredBlockHeaderHash() {
        return _desiredBlockHeaderHash;
    }

    @Override
    protected byte[] _getPayload() {
        final int blockHeaderCount = _blockHeaderHashes.size();
        final int blockHeaderHashByteCount = 32;

        final byte[] versionBytes = ByteUtil.integerToBytes(_version);
        final byte[] blockHeaderCountBytes = ByteUtil.variableLengthIntegerToBytes(blockHeaderCount);
        final byte[] blockHeaderHashesBytes = new byte[blockHeaderHashByteCount * blockHeaderCount];

        for (int i=0; i<blockHeaderCount; ++i) {
            final byte[] blockHeaderHash = _blockHeaderHashes.get(i);
            final int startIndex = (blockHeaderHashByteCount * i);
            ByteUtil.setBytes(blockHeaderHashesBytes, blockHeaderHash, startIndex);
        }

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(versionBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(blockHeaderCountBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(blockHeaderHashesBytes, Endian.BIG);
        return byteArrayBuilder.build();
    }
}
