package com.softwareverde.bitcoin.server.message.type.query.response.block.merkle;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTreeInflater;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;

public class MerkleBlockMessageInflater extends BitcoinProtocolMessageInflater {

    @Override
    public MerkleBlockMessage fromBytes(final byte[] bytes) {
        final MerkleBlockMessage merkleBlockMessage = new MerkleBlockMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.MERKLE_BLOCK);
        if (protocolMessageHeader == null) { return null; }

        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        final BlockHeader blockHeader = blockHeaderInflater.fromBytes(byteArrayReader);
        if (blockHeader == null) { return null; }

        final PartialMerkleTreeInflater partialMerkleTreeInflater = new PartialMerkleTreeInflater();
        final PartialMerkleTree partialMerkleTree = partialMerkleTreeInflater.fromBytes(byteArrayReader);

        if (partialMerkleTree == null) { return null; }

        merkleBlockMessage.setBlockHeader(new ImmutableBlockHeaderWithTransactionCount(blockHeader, partialMerkleTree.getItemCount()));
        merkleBlockMessage.setPartialMerkleTree(partialMerkleTree);

        return merkleBlockMessage;
    }
}
