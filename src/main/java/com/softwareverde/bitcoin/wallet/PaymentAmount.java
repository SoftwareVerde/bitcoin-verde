package com.softwareverde.bitcoin.wallet;

import com.softwareverde.bitcoin.address.Address;

public class PaymentAmount {
    public final Address address;
    public final Long amount;

    public PaymentAmount(final Address address, final Long amount) {
        this.address = address;
        this.amount = amount;
    }
}
