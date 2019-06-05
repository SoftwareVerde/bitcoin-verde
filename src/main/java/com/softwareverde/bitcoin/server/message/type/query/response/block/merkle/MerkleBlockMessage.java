package com.softwareverde.bitcoin.server.message.type.query.response.block.merkle;

import com.softwareverde.bitcoin.block.MerkleBlock;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTreeDeflater;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

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

        // NOTE: TransactionCount is handled by the PartialMerkleTreeDeflater...
        // byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(_blockHeader.getTransactionCount()), Endian.LITTLE);

        final PartialMerkleTreeDeflater partialMerkleTreeDeflater = new PartialMerkleTreeDeflater();
        final ByteArray partialMerkleTreeBytes = partialMerkleTreeDeflater.toBytes(_partialMerkleTree);
        byteArrayBuilder.appendBytes(partialMerkleTreeBytes);

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
