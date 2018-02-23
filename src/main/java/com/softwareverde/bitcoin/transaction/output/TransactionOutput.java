package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.Script;

public interface TransactionOutput {
    Long getAmount();
    Integer getIndex();
    Script getLockingScript();
    Integer getByteCount();
    byte[] getBytes();
}
