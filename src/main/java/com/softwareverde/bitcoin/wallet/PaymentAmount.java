package com.softwareverde.bitcoin.wallet;

import com.softwareverde.bitcoin.address.TypedAddress;

public class PaymentAmount {
    public final TypedAddress address;
    public final Long amount;

    public PaymentAmount(final TypedAddress address, final Long amount) {
        this.address = address;
        this.amount = amount;
    }
}
