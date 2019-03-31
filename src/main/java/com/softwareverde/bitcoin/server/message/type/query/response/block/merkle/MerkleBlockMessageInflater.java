package com.softwareverde.bitcoin.server.message.type.query.response.block.merkle;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.bitcoin.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.io.Logger;
import com.softwareverde.util.bytearray.Endian;

public class MerkleBlockMessageInflater extends BitcoinProtocolMessageInflater {
    public static final Integer MAX_HASHES_COUNT = (1 << 17);

    @Override
    public MerkleBlockMessage fromBytes(final byte[] bytes) {
        final MerkleBlockMessage merkleBlockMessage = new MerkleBlockMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.MERKLE_BLOCK);
        if (protocolMessageHeader == null) { return null; }

        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        final BlockHeader blockHeader = blockHeaderInflater.fromBytes(byteArrayReader);
        if (blockHeader == null) { return null; }

        final Integer transactionCount = byteArrayReader.readInteger(4, Endian.LITTLE);

        merkleBlockMessage.setBlockHeader(new ImmutableBlockHeaderWithTransactionCount(blockHeader, transactionCount));

        final Integer hashesCount = byteArrayReader.readVariableSizedInteger().intValue();
        if (hashesCount > MAX_HASHES_COUNT) {
            Logger.log("MerkleBlock exceeded maximum hashes count: " + hashesCount);
            return null;
        }

        final ImmutableListBuilder<Sha256Hash> hashesBuilder = new ImmutableListBuilder<Sha256Hash>(hashesCount);
        for (int i = 0; i < hashesCount; ++i) {
            final Sha256Hash hash = MutableSha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.LITTLE));
            hashesBuilder.add(hash);
        }

        final Integer flagsByteCount = byteArrayReader.readVariableSizedInteger().intValue();
        if (flagsByteCount > MAX_HASHES_COUNT) {
            Logger.log("MerkleBlock exceeded maximum flag-bytes count: " + flagsByteCount);
            return null;
        }

        final MutableByteArray flags = MutableByteArray.wrap(byteArrayReader.readBytes(flagsByteCount));
        for (int i = 0; i < flagsByteCount; ++i) {
            flags.set(i, ByteUtil.reverseBits(flags.getByte(i)));
        }

        if (byteArrayReader.didOverflow()) { return null; }

        final PartialMerkleTree partialMerkleTree = PartialMerkleTree.build(transactionCount, hashesBuilder.build(), flags);
        merkleBlockMessage.setPartialMerkleTree(partialMerkleTree);

        return merkleBlockMessage;
    }
}
