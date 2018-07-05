package com.softwareverde.bitcoin.block.header;

public interface BlockHeaderWithTransactionCount extends BlockHeader {
    Integer getTransactionCount();
}
