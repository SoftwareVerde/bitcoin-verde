package com.softwareverde.bitcoin.wallet.slp;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.wallet.PaymentAmount;

import java.math.BigInteger;

public class SlpPaymentAmount extends PaymentAmount {
    public final BigInteger tokenAmount;

    public SlpPaymentAmount(final Address address, final Long amount, final BigInteger tokenAmount) {
        super(address, amount);
        this.tokenAmount = tokenAmount;
    }
}
