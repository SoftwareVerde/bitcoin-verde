package com.softwareverde.bitcoin.address;

import com.softwareverde.bitcoin.type.key.PrivateKey;
import org.junit.Assert;
import org.junit.Test;

public class AddressTests {
    @Test
    public void should_create_address_and_compressed_address() {
        // Setup
        final PrivateKey privateKey = PrivateKey.parseFromHexString("BCA0FB4DE749BA8E59C5A738B74CEC077A6A5CB7EB08379D0831D6B04F911C27");
        final AddressInflater addressInflater = new AddressInflater();
        final String expectedAddressString = "1BCHJQTjf6pBjkgxdajEEYw2F8kzohGh9Z";
        final String expectedCompressedString = "19wqZKBg2yrNWiqL8nyVZCSfQejh7xfvfk";

        // Action
        final Address address = addressInflater.fromPrivateKey(privateKey);
        final Address compressedAddress = addressInflater.compressedFromPrivateKey(privateKey);

        // Assert
        Assert.assertEquals(expectedAddressString, address.toBase58CheckEncoded());
        Assert.assertEquals(expectedCompressedString, compressedAddress.toBase58CheckEncoded());
    }
}
