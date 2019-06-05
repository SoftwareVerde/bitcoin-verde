package com.softwareverde.bitcoin.wallet.slp;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.wallet.PaymentAmount;

public class SlpPaymentAmount extends PaymentAmount {
    public final Long tokenAmount;

    public SlpPaymentAmount(final Address address, final Long amount, final Long tokenAmount) {
        super(address, amount);
        this.tokenAmount = tokenAmount;
    }
}
