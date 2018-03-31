package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.util.HexUtil;

public class BlockHasher {
    protected final BlockHeaderDeflater _blockHeaderDeflater = new BlockHeaderDeflater();

    protected Hash _calculateDoubleHash(final byte[] bytes) {
        return new ImmutableHash(ByteUtil.reverseEndian(BitcoinUtil.sha256(BitcoinUtil.sha256(bytes))));
    }

    public Hash calculateBlockHash(final BlockHeader blockHeader) {
        final byte[] serializedByteData = _blockHeaderDeflater.toBytes(blockHeader);
        return _calculateDoubleHash(serializedByteData);
    }

    public Hash calculateBlockHash(final byte[] blockHeaderBytes) {
        return _calculateDoubleHash(blockHeaderBytes);
    }
}
