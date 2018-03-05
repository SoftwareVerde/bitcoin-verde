package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.block.merkleroot.Hashable;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.list.List;

public interface Transaction extends Hashable, Constable {
    static final Integer SATOSHIS_PER_BITCOIN = 100_000_000;

    Integer getVersion();
    Boolean hasWitnessData();
    List<? extends TransactionInput> getTransactionInputs();
    List<? extends TransactionOutput> getTransactionOutputs();
    LockTime getLockTime();
    Long getTotalOutputValue();

    @Override
    ImmutableTransaction asConst();
}
