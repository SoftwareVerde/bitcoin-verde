package com.softwareverde.bitcoin;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCountInflater;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTreeDeflater;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTreeInflater;
import com.softwareverde.bitcoin.bloomfilter.BloomFilterDeflater;
import com.softwareverde.bitcoin.bloomfilter.BloomFilterInflater;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeaderInflater;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddressInflater;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemInflater;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProofInflater;

public class CoreInflater implements MasterInflater {
    protected final BitcoinProtocolMessageHeaderInflater _bitcoinProtocolMessageHeaderInflater;
    protected final NodeIpAddressInflater _nodeIpAddressInflater;

    protected final BlockHeaderInflater _blockHeaderInflater;
    protected final BlockHeaderDeflater _blockHeaderDeflater;

    protected final BlockHeaderWithTransactionCountInflater _blockHeaderWithTransactionCountInflater;

    protected final PartialMerkleTreeInflater _partialMerkleTreeInflater;
    protected final PartialMerkleTreeDeflater _partialMerkleTreeDeflater;

    protected final BloomFilterInflater _bloomFilterInflater;
    protected final BloomFilterDeflater _bloomFilterDeflater;

    protected final InventoryItemInflater _inventoryItemInflater;

    protected final BlockInflater _blockInflater;
    protected final BlockDeflater _blockDeflater;

    protected final TransactionInflater _transactionInflater;
    protected final TransactionDeflater _transactionDeflater;

    protected final AddressInflater _addressInflater;

    protected final DoubleSpendProofInflater _doubleSpendProofInflater;

    public CoreInflater() {
        _bitcoinProtocolMessageHeaderInflater = new BitcoinProtocolMessageHeaderInflater();
        _nodeIpAddressInflater = new NodeIpAddressInflater();

        _blockHeaderInflater = new BlockHeaderInflater();
        _blockHeaderDeflater = new BlockHeaderDeflater();

        _blockHeaderWithTransactionCountInflater = new BlockHeaderWithTransactionCountInflater();

        _partialMerkleTreeInflater = new PartialMerkleTreeInflater();
        _partialMerkleTreeDeflater = new PartialMerkleTreeDeflater();

        _bloomFilterInflater = new BloomFilterInflater();
        _bloomFilterDeflater = new BloomFilterDeflater();

        _inventoryItemInflater = new InventoryItemInflater();

        _blockInflater = new BlockInflater();
        _blockDeflater = new BlockDeflater();

        _transactionInflater = new TransactionInflater();
        _transactionDeflater = new TransactionDeflater();

        _addressInflater = new AddressInflater();

        _doubleSpendProofInflater = new DoubleSpendProofInflater();
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

    @Override
    public PartialMerkleTreeDeflater getPartialMerkleTreeDeflater() {
        return _partialMerkleTreeDeflater;
    }

    @Override
    public AddressInflater getAddressInflater() {
        return _addressInflater;
    }

    @Override
    public BloomFilterInflater getBloomFilterInflater() {
        return _bloomFilterInflater;
    }

    @Override
    public BloomFilterDeflater getBloomFilterDeflater() {
        return _bloomFilterDeflater;
    }

    @Override
    public InventoryItemInflater getInventoryItemInflater() {
        return _inventoryItemInflater;
    }

    @Override
    public DoubleSpendProofInflater getDoubleSpendProofInflater() {
        return _doubleSpendProofInflater;
    }
}
