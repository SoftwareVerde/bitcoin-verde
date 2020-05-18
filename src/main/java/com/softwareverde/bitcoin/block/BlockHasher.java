package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.security.hash.sha256.MutableSha256Hash;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.security.util.HashUtil;
import org.bouncycastle.crypto.digests.SHA256Digest;

public class BlockHasher {
    protected final BlockHeaderDeflater _blockHeaderDeflater = new BlockHeaderDeflater();

    /**
     * BouncyCastle is about half as fast as JVM's native implementation, however, it can perform better at high concurrency.
     */
    protected static MutableSha256Hash doubleSha256ViaBouncyCastle(final ByteArray data) {
        final SHA256Digest digest = new SHA256Digest();
        final byte[] dataBytes = data.getBytes();
        digest.update(dataBytes, 0, dataBytes.length);
        digest.doFinal(dataBytes, 0);
        digest.update(dataBytes, 0, Sha256Hash.BYTE_COUNT);
        digest.doFinal(dataBytes, 0);
        return MutableSha256Hash.wrap(ByteUtil.copyBytes(dataBytes, 0, Sha256Hash.BYTE_COUNT));
    }

    protected Sha256Hash _calculateBlockHash(final ByteArray serializedByteData, final Boolean useBouncyCastle) {
        final MutableSha256Hash sha256Hash;
        if (useBouncyCastle) {
            sha256Hash = BlockHasher.doubleSha256ViaBouncyCastle(serializedByteData);
        }
        else {
            sha256Hash = HashUtil.doubleSha256(serializedByteData);
        }
        return sha256Hash.toReversedEndian();
    }

    protected Sha256Hash _calculateBlockHash(final BlockHeader blockHeader, final Boolean useBouncyCastle) {
        final ByteArray serializedByteData = _blockHeaderDeflater.toBytes(blockHeader);
        return _calculateBlockHash(serializedByteData, useBouncyCastle);
    }

    public Sha256Hash calculateBlockHash(final BlockHeader blockHeader, final Boolean useBouncyCastle) {
        return _calculateBlockHash(blockHeader, useBouncyCastle);
    }

    public Sha256Hash calculateBlockHash(final BlockHeader blockHeader) {
        return _calculateBlockHash(blockHeader, false);
    }

    public BlockHeaderDeflater getBlockHeaderDeflater() {
        return _blockHeaderDeflater;
    }
}
