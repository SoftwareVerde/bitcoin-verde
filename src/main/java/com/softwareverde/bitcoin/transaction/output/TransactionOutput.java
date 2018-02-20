package com.softwareverde.bitcoin.transaction.output;

public interface TransactionOutput {
    Long getAmount();
    Integer getIndex();
    byte[] getScript();
    Integer getByteCount();
    byte[] getBytes();
}
