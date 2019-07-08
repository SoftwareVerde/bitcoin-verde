package com.softwareverde.bitcoin;

import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCountInflater;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTreeInflater;
import com.softwareverde.bitcoin.inflater.*;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeaderInflater;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddressInflater;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;

public class CoreInflater implements BlockHeaderInflaters, BlockInflaters, ExtendedBlockHeaderInflaters, TransactionInflaters, ProtocolMessageInflaters, MerkleTreeInflaters {
    protected final BitcoinProtocolMessageHeaderInflater _bitcoinProtocolMessageHeaderInflater;
    protected final NodeIpAddressInflater _nodeIpAddressInflater;

    protected final BlockHeaderInflater _blockHeaderInflater;
    protected final BlockHeaderDeflater _blockHeaderDeflater;

    protected final BlockHeaderWithTransactionCountInflater _blockHeaderWithTransactionCountInflater;

    protected final PartialMerkleTreeInflater _partialMerkleTreeInflater;

    protected final BlockInflater _blockInflater;
    protected final BlockDeflater _blockDeflater;

    protected final TransactionInflater _transactionInflater;
    protected final TransactionDeflater _transactionDeflater;

    public CoreInflater() {
        _bitcoinProtocolMessageHeaderInflater = new BitcoinProtocolMessageHeaderInflater();
        _nodeIpAddressInflater = new NodeIpAddressInflater();

        _blockHeaderInflater = new BlockHeaderInflater();
        _blockHeaderDeflater = new BlockHeaderDeflater();

        _blockHeaderWithTransactionCountInflater = new BlockHeaderWithTransactionCountInflater();

        _partialMerkleTreeInflater = new PartialMerkleTreeInflater();

        _blockInflater = new BlockInflater();
        _blockDeflater = new BlockDeflater();

        _transactionInflater = new TransactionInflater();
        _transactionDeflater = new TransactionDeflater();
    }

    @Override
    public BlockHeaderInflater getBlockHeaderInflater() {
        return _blockHeaderInflater;
    }

    @Override
    public BlockHeaderDeflater getBlockHeaderDeflater() {
        return _blockHeaderDeflater;
    }

    @Override
    public BlockInflater getBlockInflater() {
        return _blockInflater;
    }

    @Override
    public BlockDeflater getBlockDeflater() {
        return _blockDeflater;
    }

    @Override
    public TransactionInflater getTransactionInflater() {
        return _transactionInflater;
    }

    @Override
    public TransactionDeflater getTransactionDeflater() {
        return _transactionDeflater;
    }

    @Override
    public BitcoinProtocolMessageHeaderInflater getBitcoinProtocolMessageHeaderInflater() {
        return _bitcoinProtocolMessageHeaderInflater;
    }

    @Override
    public NodeIpAddressInflater getNodeIpAddressInflater() {
        return _nodeIpAddressInflater;
    }

    @Override
    public BlockHeaderWithTransactionCountInflater getBlockHeaderWithTransactionCountInflater() {
        return _blockHeaderWithTransactionCountInflater;
    }

    @Override
    public PartialMerkleTreeInflater getPartialMerkleTreeInflater() {
        return _partialMerkleTreeInflater;
    }
}
