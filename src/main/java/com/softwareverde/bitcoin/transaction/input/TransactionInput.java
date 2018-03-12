package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.constable.Constable;

public interface TransactionInput extends Constable<ImmutableTransactionInput> {
    static final Long MAX_SEQUENCE_NUMBER = 0xFFFFFFFFL;

    Hash getPreviousOutputTransactionHash();
    Integer getPreviousOutputIndex();
    Script getUnlockingScript();
    Long getSequenceNumber();

    @Override
    ImmutableTransactionInput asConst();
}
