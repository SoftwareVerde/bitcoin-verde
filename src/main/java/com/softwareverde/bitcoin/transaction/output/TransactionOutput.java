package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.type.address.Address;
import com.softwareverde.constable.Constable;

public interface TransactionOutput extends Constable<ImmutableTransactionOutput> {
    static TransactionOutput createCoinbaseTransactionOutput(final Address payToAddress, final Long satoshis) {
        final LockingScript lockingScript = ScriptBuilder.payToAddress(payToAddress);
        final MutableTransactionOutput coinbaseTransactionOutput = new MutableTransactionOutput();
        coinbaseTransactionOutput.setLockingScript(lockingScript);
        coinbaseTransactionOutput.setAmount(satoshis);
        return coinbaseTransactionOutput;
    }

    Long getAmount();
    Integer getIndex();
    LockingScript getLockingScript();

    @Override
    ImmutableTransactionOutput asConst();
}
