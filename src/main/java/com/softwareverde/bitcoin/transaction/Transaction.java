package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.block.merkleroot.Hashable;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.type.address.Address;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.list.List;

public interface Transaction extends Hashable, Constable<ImmutableTransaction> {
    Long SATOSHIS_PER_BITCOIN = 100_000_000L;

    static Transaction createCoinbaseTransaction(final String coinbaseMessage, final Address address, final Long satoshis) {
        final MutableTransaction coinbaseTransaction = new MutableTransaction();
        coinbaseTransaction.addTransactionInput(TransactionInput.createCoinbaseTransactionInput(coinbaseMessage));
        coinbaseTransaction.addTransactionOutput(TransactionOutput.createCoinbaseTransactionOutput(address, satoshis));
        return coinbaseTransaction;
    }

    Integer getVersion();
    Boolean hasWitnessData();
    List<TransactionInput> getTransactionInputs();
    List<TransactionOutput> getTransactionOutputs();
    LockTime getLockTime();
    Long getTotalOutputValue();

    @Override
    ImmutableTransaction asConst();
}
