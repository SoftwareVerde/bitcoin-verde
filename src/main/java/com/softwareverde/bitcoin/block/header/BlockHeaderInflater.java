package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.merkleroot.MutableMerkleRoot;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.MutableSha256Hash;
import com.softwareverde.util.bytearray.Endian;

public class BlockHeaderInflater {
    public static final Integer BLOCK_HEADER_BYTE_COUNT = 80;

    protected MutableBlockHeader _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final MutableBlockHeader blockHeader = new MutableBlockHeader();

        // 0100 0000                                                                        // Version
        // 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000  // Previous Block Hash
        // 3BA3 EDFD 7A7B 12B2 7AC7 2C3E 6776 8F61 7FC8 1BC3 888A 5132 3A9F B8AA 4B1E 5E4A  // Merkle Root
        // 29AB 5F49                                                                        // Timestamp
        // FFFF 001D                                                                        // Difficulty
        // 1DAC 2B7C                                                                        // Nonce
        // 0101 0000 0001 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 FFFF FFFF 4D04 FFFF 001D 0104 4554 6865 2054 696D 6573 2030 332F 4A61 6E2F 3230 3039 2043 6861 6E63 656C 6C6F 7220 6F6E 2062 7269 6E6B 206F 6620 7365 636F 6E64 2062 6169 6C6F 7574 2066 6F72 2062 616E 6B73 FFFF FFFF 0100 F205 2A01 0000 0043 4104 678A FDB0 FE55 4827 1967 F1A6 7130 B710 5CD6 A828 E039 09A6 7962 E0EA 1F61 DEB6 49F6 BC3F 4CEF 38C4 F355 04E5 1EC1 12DE 5C38 4DF7 BA0B 8D57 8A4C 702B 6BF1 1D5F AC00 0000 00

        blockHeader._version = byteArrayReader.readLong(4, Endian.LITTLE);
        blockHeader._previousBlockHash = MutableSha256Hash.wrap(byteArrayReader.readBytes(32, Endian.LITTLE));
        blockHeader._merkleRoot = MutableMerkleRoot.wrap(byteArrayReader.readBytes(32, Endian.LITTLE));
        blockHeader._timestamp = byteArrayReader.readLong(4, Endian.LITTLE);

        final byte[] difficultyBytes = byteArrayReader.readBytes(4, Endian.LITTLE);
        blockHeader._difficulty = Difficulty.decode(ByteArray.wrap(difficultyBytes));
        blockHeader._nonce = byteArrayReader.readLong(4, Endian.LITTLE);
        // blockHeader._transactionCount = byteArrayReader.readVariableLengthInteger().intValue(); // Always 0 for Block Headers...

        if (byteArrayReader.didOverflow()) { return null; }

        return blockHeader;
    }

    public MutableBlockHeader fromBytes(final ByteArrayReader byteArrayReader) {
        if (byteArrayReader == null) { return null; }

        return _fromByteArrayReader(byteArrayReader);
    }

    public MutableBlockHeader fromBytes(final ByteArray byteArray) {
        if (byteArray == null) { return null; }

        final ByteArrayReader byteArrayReader = new ByteArrayReader(byteArray.getBytes());
        return _fromByteArrayReader(byteArrayReader);
    }

    public MutableBlockHeader fromBytes(final byte[] bytes) {
        if (bytes == null) { return null; }

        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(byteArrayReader);
    }
}
