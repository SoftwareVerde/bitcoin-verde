package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;

public class BlockHasher {
    public Hash calculateBlockHash(final BlockHeader blockHeader) {
        final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
        final byte[] serializedByteData = blockHeaderDeflater.toBytes(blockHeader);
        return new ImmutableHash(ByteUtil.reverseEndian(BitcoinUtil.sha256(BitcoinUtil.sha256(serializedByteData))));
    }
}
