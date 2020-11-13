package com.softwareverde.bitcoin.server.message.type.query.response.block.merkle;

import com.softwareverde.bitcoin.block.MerkleBlock;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTreeDeflater;
import com.softwareverde.bitcoin.inflater.BlockHeaderInflaters;
import com.softwareverde.bitcoin.inflater.MerkleTreeInflaters;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

public class MerkleBlockMessage extends BitcoinProtocolMessage {

    protected final BlockHeaderInflaters _blockHeaderInflaters;
    protected final MerkleTreeInflaters _merkleTreeInflaters;

    protected BlockHeaderWithTransactionCount _blockHeader;
    protected PartialMerkleTree _partialMerkleTree;

    public MerkleBlockMessage(final BlockHeaderInflaters blockHeaderInflaters, final MerkleTreeInflaters merkleTreeInflaters) {
        super(MessageType.MERKLE_BLOCK);
        _blockHeaderInflaters = blockHeaderInflaters;
        _merkleTreeInflaters = merkleTreeInflaters;
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
        final BlockHeaderDeflater blockHeaderDeflater = _blockHeaderInflaters.getBlockHeaderDeflater();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(blockHeaderDeflater.toBytes(_blockHeader));

        // NOTE: TransactionCount is handled by the PartialMerkleTreeDeflater...
        // byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(_blockHeader.getTransactionCount()), Endian.LITTLE);

        final PartialMerkleTreeDeflater partialMerkleTreeDeflater = _merkleTreeInflaters.getPartialMerkleTreeDeflater();
        final ByteArray partialMerkleTreeBytes = partialMerkleTreeDeflater.toBytes(_partialMerkleTree);
        byteArrayBuilder.appendBytes(partialMerkleTreeBytes);

        return byteArrayBuilder;
    }

    @Override
    protected Integer _getPayloadByteCount() {
        final PartialMerkleTreeDeflater partialMerkleTreeDeflater = _merkleTreeInflaters.getPartialMerkleTreeDeflater();
        final Integer partialMerkleTreeByteCount = partialMerkleTreeDeflater.getByteCount(_partialMerkleTree);
        return (BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT + partialMerkleTreeByteCount);
    }
}
