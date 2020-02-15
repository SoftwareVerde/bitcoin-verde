package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.block.Block;

public interface MutableUnspentTransactionOutputSet extends UnspentTransactionOutputSet {
    void addBlock(Block block);
}
