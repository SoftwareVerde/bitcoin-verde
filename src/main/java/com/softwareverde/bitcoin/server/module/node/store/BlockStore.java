package com.softwareverde.bitcoin.server.module.node.store;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface BlockStore {
    Boolean storeBlock(Block block, Long blockHeight);
    void removeBlock(Sha256Hash blockHash, Long blockHeight);
    MutableBlockHeader getBlockHeader(Sha256Hash blockHash, Long blockHeight);
    MutableBlock getBlock(Sha256Hash blockHash, Long blockHeight);
    ByteArray readFromBlock(Sha256Hash blockHash, Long blockHeight, Long diskOffset, Integer byteCount);
}
