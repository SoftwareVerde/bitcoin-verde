package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.type.hash.Hash;

public interface TransactionInput {
    static final Long MAX_SEQUENCE_NUMBER = 0xFFFFFFFFL;

    Hash getPreviousTransactionOutput();
    Integer getPreviousTransactionOutputIndex();
    byte[] getSignatureScript();
    Long getSequenceNumber();
    Integer getByteCount();
    byte[] getBytes();
}
