package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.token.CashToken;
import com.softwareverde.constable.Constable;
import com.softwareverde.json.Jsonable;

public interface TransactionOutput extends Constable<ImmutableTransactionOutput>, Jsonable {
    static TransactionOutput createPayToAddressTransactionOutput(final Address payToAddress, final Long satoshis) {
        final LockingScript lockingScript = ScriptBuilder.payToAddress(payToAddress);
        final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();
        transactionOutput.setLockingScript(lockingScript);
        transactionOutput.setAmount(satoshis);
        return transactionOutput;
    }

    Long getAmount();
    Integer getIndex();
    LockingScript getLockingScript();
    CashToken getCashToken();
    default Boolean hasCashToken() {
        return (this.getCashToken() != null);
    }

    @Override
    ImmutableTransactionOutput asConst();
}
