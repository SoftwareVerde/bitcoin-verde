package com.softwareverde.bitcoin.server.socket.message.block.header;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
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
    protected List<BlockHeader> _blockHeaders = new ArrayList<BlockHeader>();
    protected BlockHeader _desiredBlockHeader;

    public BlockHeadersMessage() {
        super(MessageType.BLOCK_HEADERS);
        _version = Constants.PROTOCOL_VERSION;
    }

    public Integer getVersion() { return _version; }

    public void addBlockHeader(final BlockHeader blockHeader) {
        _blockHeaders.add(blockHeader);
    }

    public void clearBlockHeaders() {
        _blockHeaders.clear();
    }

    public List<BlockHeader> getBlockHeaders() {
        return Util.copyList(_blockHeaders);
    }

    @Override
    protected byte[] _getPayload() {
        final int blockHeaderCount = _blockHeaders.size();
        final int blockHeaderByteCount = BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT;

        final byte[] versionBytes = ByteUtil.integerToBytes(_version);
        final byte[] blockHeaderCountBytes = ByteUtil.variableLengthIntegerToBytes(blockHeaderCount);
        final byte[] blockHeadersBytes = new byte[blockHeaderByteCount * blockHeaderCount];

        for (int i=0; i<blockHeaderCount; ++i) {
            final BlockHeader blockHeader = _blockHeaders.get(i);
            final byte[] blockHeaderBytes = blockHeader.getBytes();
            final int startIndex = (blockHeaderByteCount * i);
            ByteUtil.setBytes(blockHeaderBytes, blockHeaderBytes, startIndex);
        }

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(versionBytes, Endian.LITTLE);
        byteArrayBuilder.appendBytes(blockHeaderCountBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(blockHeadersBytes, Endian.BIG);
        return byteArrayBuilder.build();
    }
}
