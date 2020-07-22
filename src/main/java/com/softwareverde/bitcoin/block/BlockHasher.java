package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.MutableSha256Hash;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.util.HashUtil;

public class BlockHasher {
    protected final BlockHeaderDeflater _blockHeaderDeflater = new BlockHeaderDeflater();

    public Sha256Hash calculateBlockHash(final BlockHeader blockHeader) {
        final ByteArray serializedByteData = _blockHeaderDeflater.toBytes(blockHeader);
        final MutableSha256Hash sha256Hash = HashUtil.doubleSha256(serializedByteData);
        return sha256Hash.toReversedEndian();
    }

    public Sha256Hash calculateBlockHash(final byte[] blockHeaderBytes) {
        final byte[] sha256Hash = HashUtil.doubleSha256(blockHeaderBytes);
        return MutableSha256Hash.wrap(ByteUtil.reverseEndian(sha256Hash));
    }
}
