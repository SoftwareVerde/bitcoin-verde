package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class BlockHeaderInflater {
    public static final Integer BLOCK_HEADER_BYTE_COUNT = 81;

    public BlockHeader fromBytes(final byte[] bytes) {
        final BlockHeader blockHeader = new BlockHeader();

        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        blockHeader._version = byteArrayReader.readInteger(4, Endian.LITTLE);
        ByteUtil.setBytes(blockHeader._previousBlockHash, byteArrayReader.readBytes(32, Endian.LITTLE));
        ByteUtil.setBytes(blockHeader._merkleRoot, byteArrayReader.readBytes(32, Endian.LITTLE));
        blockHeader._timestamp = byteArrayReader.readLong(4, Endian.LITTLE);
        blockHeader._difficulty = byteArrayReader.readInteger(4, Endian.LITTLE);
        blockHeader._nonce = byteArrayReader.readLong(4, Endian.LITTLE);
        blockHeader._transactionCount = byteArrayReader.readVariableSizedInteger().intValue();


        return blockHeader;
    }
}
