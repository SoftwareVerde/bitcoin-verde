package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.script.ScriptType;

public class IndexedOutput {
    public final TransactionId transactionId;
    public final Integer outputIndex;
    public final Long amount;
    public final ScriptType scriptType;
    public final AddressId addressId;
    public final TransactionId slpTransactionId;

    public IndexedOutput(final TransactionId transactionId, final Integer outputIndex, final Long amount, final ScriptType scriptType, final AddressId addressId, final TransactionId slpTransactionId) {
        this.transactionId = transactionId;
        this.outputIndex = outputIndex;
        this.amount = amount;
        this.scriptType = scriptType;
        this.addressId = addressId;
        this.slpTransactionId = slpTransactionId;
    }
}
