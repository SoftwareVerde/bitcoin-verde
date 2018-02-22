package com.softwareverde.bitcoin.transaction.output;

public interface TransactionOutput {
    Long getAmount();
    Integer getIndex();
    byte[] getLockingScript();
    Integer getByteCount();
    byte[] getBytes();
}
