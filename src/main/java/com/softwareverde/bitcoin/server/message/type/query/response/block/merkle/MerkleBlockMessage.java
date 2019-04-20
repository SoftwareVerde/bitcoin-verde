package com.softwareverde.bitcoin.server.message.type.query.response.block.merkle;

import com.softwareverde.bitcoin.block.MerkleBlock;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class MerkleBlockMessage extends BitcoinProtocolMessage {

    protected BlockHeaderWithTransactionCount _blockHeader;
    protected PartialMerkleTree _partialMerkleTree;

    public MerkleBlockMessage() {
        super(MessageType.MERKLE_BLOCK);
    }

    public BlockHeaderWithTransactionCount getBlockHeader() {
        return _blockHeader;
    }

    public PartialMerkleTree getPartialMerkleTree() { return _partialMerkleTree; }

    public MerkleBlock getMerkleBlock() {
        if ( (_blockHeader == null) || (_partialMerkleTree == null) ) { return null; }
        return new MerkleBlock(_blockHeader, _partialMerkleTree);
    }

    public void setBlockHeader(final BlockHeaderWithTransactionCount blockHeader) {
        _blockHeader = blockHeader;
    }

    public void setPartialMerkleTree(final PartialMerkleTree partialMerkleTree) {
        _partialMerkleTree = partialMerkleTree;
    }

    @Override
    protected ByteArray _getPayload() {
        final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(blockHeaderDeflater.toBytes(_blockHeader));

        byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(_blockHeader.getTransactionCount()), Endian.LITTLE);

        final List<Sha256Hash> hashes = _partialMerkleTree.getHashes();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(hashes.getSize()));
        for (final Sha256Hash hash : hashes) {
            byteArrayBuilder.appendBytes(hash, Endian.LITTLE);
        }

        final ByteArray flags = _partialMerkleTree.getFlags();
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(flags.getByteCount()));

        for (int i = 0; i < flags.getByteCount(); ++i) {
            byteArrayBuilder.appendByte(ByteUtil.reverseBits(flags.getByte(i)));
        }

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
