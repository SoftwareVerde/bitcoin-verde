package com.softwareverde.bitcoin.inflater;

public interface MasterInflater extends
    ProtocolMessageInflaters,
    BlockInflaters,
    ExtendedBlockHeaderInflaters,
    TransactionInflaters,
    MerkleTreeInflaters,
    BloomFilterInflaters,
    InventoryItemInflaters,
    AddressInflaters
{ }
