package com.softwareverde.bitcoin.transaction.output;

public interface UnspentTransactionOutput extends TransactionOutput {
    Long UNKNOWN_BLOCK_HEIGHT = -1L;

    Long getBlockHeight();
}
