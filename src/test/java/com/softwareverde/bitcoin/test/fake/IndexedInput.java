package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.transaction.TransactionId;

public class IndexedInput {
    public final TransactionId transactionId;
    public final Integer inputIndex;
    public final AddressId addressId;

    public IndexedInput(final TransactionId transactionId, final Integer inputIndex, final AddressId addressId) {
        this.transactionId = transactionId;
        this.inputIndex = inputIndex;
        this.addressId = addressId;
    }
}
