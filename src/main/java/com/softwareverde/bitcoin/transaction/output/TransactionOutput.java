package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.Constable;

public interface TransactionOutput extends Constable<ImmutableTransactionOutput> {
    Long getAmount();
    Integer getIndex();
    LockingScript getLockingScript();

    @Override
    ImmutableTransactionOutput asConst();
}
