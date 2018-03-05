package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.constable.Constable;

public interface TransactionOutput extends Constable<ImmutableTransactionOutput> {
    Long getAmount();
    Integer getIndex();
    Script getLockingScript();

    @Override
    ImmutableTransactionOutput asConst();
}
