package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;

public class BlockHasher {
    protected final BlockHeaderDeflater _blockHeaderDeflater = new BlockHeaderDeflater();

    protected Sha256Hash _calculateDoubleHash(final byte[] bytes) {
        return MutableSha256Hash.wrap(ByteUtil.reverseEndian(BitcoinUtil.sha256(BitcoinUtil.sha256(bytes))));
    }

    public Sha256Hash calculateBlockHash(final BlockHeader blockHeader) {
        final ByteArray serializedByteData = _blockHeaderDeflater.toBytes(blockHeader);
        return _calculateDoubleHash(serializedByteData.getBytes());
    }

    public Sha256Hash calculateBlockHash(final byte[] blockHeaderBytes) {
        return _calculateDoubleHash(blockHeaderBytes);
    }
}
