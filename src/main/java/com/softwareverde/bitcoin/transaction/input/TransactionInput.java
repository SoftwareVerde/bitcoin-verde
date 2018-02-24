package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.type.hash.Hash;

public interface TransactionInput {
    static final Long MAX_SEQUENCE_NUMBER = 0xFFFFFFFFL;

    Hash getOutputTransactionHash();
    Integer getOutputTransactionIndex();
    Script getUnlockingScript();
    Long getSequenceNumber();
    Integer getByteCount();
    byte[] getBytes();
}
