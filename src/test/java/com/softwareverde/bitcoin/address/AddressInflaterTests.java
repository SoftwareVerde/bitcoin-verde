package com.softwareverde.bitcoin.address;

import org.junit.Assert;
import org.junit.Test;

public class AddressInflaterTests {
    @Test
    public void should_inflate_base32_address() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32AddressString = "bitcoincash:qqswr73n8gzgsygazzfn9qm3qk46dtescsyrzewzuj";

        // Action
        final Address address = addressInflater.fromBase32Check(base32AddressString);

        // Assert
        Assert.assertNotNull(address);
    }

    @Test
    public void should_return_null_for_invalid_base32_address() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32AddressString = "http://bitcoincash:qqswr73n8gzgsygazzfn9qm3qk46dtescsyrzewzuj";

        // Action
        final Address address = addressInflater.fromBase32Check(base32AddressString);

        // Assert
        Assert.assertNull(address);
    }
}
