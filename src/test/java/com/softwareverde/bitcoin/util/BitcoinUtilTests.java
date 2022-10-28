package com.softwareverde.bitcoin.util;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.secp256k1.signature.BitcoinMessageSignature;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import org.junit.Assert;
import org.junit.Test;

public class BitcoinUtilTests {
    @Test
    public void should_sign_and_verify_bitcoin_message_recovery_0() {
        final AddressInflater addressInflater = new AddressInflater();

        final PrivateKey privateKey = PrivateKey.fromHexString("948AB5DBDBF277DD81C6754DCBDE3A9E9BB61D0AA146F94D7B802C34D811E571");
        final String message = "Milo the doh.";
        final Boolean useCompressedAddress = true;

        final BitcoinMessageSignature signature = BitcoinUtil.signBitcoinMessage(privateKey, message, useCompressedAddress);

        final Address address = addressInflater.fromPrivateKey(privateKey, useCompressedAddress);
        final Boolean isValid = BitcoinUtil.verifyBitcoinMessage(message, address, signature);

        Assert.assertTrue(isValid);
        Assert.assertEquals(0, signature.getRecoveryId().intValue());
    }

    @Test
    public void should_sign_and_verify_bitcoin_message_recovery_1() {
        final AddressInflater addressInflater = new AddressInflater();

        final PrivateKey privateKey = PrivateKey.fromHexString("948AB5DBDBF277DD81C6754DCBDE3A9E9BB61D0AA146F94D7B802C34D811E571");
        final String message = "Milo the dog.";
        final Boolean useCompressedAddress = true;

        final BitcoinMessageSignature signature = BitcoinUtil.signBitcoinMessage(privateKey, message, useCompressedAddress);

        final Address address = addressInflater.fromPrivateKey(privateKey, useCompressedAddress);
        final Boolean isValid = BitcoinUtil.verifyBitcoinMessage(message, address, signature);

        Assert.assertTrue(isValid);
        Assert.assertEquals(1, signature.getRecoveryId().intValue());
    }

    @Test
    public void should_verify_valid_bitcoin_message() {
        final AddressInflater addressInflater = new AddressInflater();

        final String addressString = "1HZwkjkeaoZfTSaJxDw6aKkxp45agDiEzN";
        final String signatureString = "HGkIGRLabV27C6uO6ePz+rm3Gcbc6urV7KkTWUgjbfcG/nU+qL7kVWi9QFg1rG+0n1UzA0glRNUoqaiRF/Iu85A=";
        final String message = "Mary had a little lamb.";

        final BitcoinMessageSignature signature = BitcoinMessageSignature.fromBase64(signatureString);
        final Address address = addressInflater.fromBase58Check(addressString).getBytes();
        final Boolean isValid = BitcoinUtil.verifyBitcoinMessage(message, address, signature);

        Assert.assertTrue(isValid);
    }

    @Test
    public void should_verify_valid_bitcoin_message_2() {
        final AddressInflater addressInflater = new AddressInflater();

        final String addressString = "1BqtNgMrDXnCek3cdDVSer4BK7knNTDTSR";
        final String signatureString = "ILoOBJK9kVKsdUOnJPPoDtrDtRSQw2pyMo+2r5bdUlNkSLDZLqMs8h9mfDm/alZo3DK6rKvTO0xRPrl6DPDpEik=";
        final String message = "Test";

        final BitcoinMessageSignature signature = BitcoinMessageSignature.fromBase64(signatureString);
        final Address address = addressInflater.fromBase58Check(addressString).getBytes();
        final Boolean isValid = BitcoinUtil.verifyBitcoinMessage(message, address, signature);

        Assert.assertTrue(isValid);
    }

    @Test
    public void should_not_verify_bitcoin_message_bad_message() {
        final AddressInflater addressInflater = new AddressInflater();

        final String addressString = "1BqtNgMrDXnCek3cdDVSer4BK7knNTDTSR";
        final String signatureString = "ILoOBJK9kVKsdUOnJPPoDtrDtRSQw2pyMo+2r5bdUlNkSLDZLqMs8h9mfDm/alZo3DK6rKvTO0xRPrl6DPDpEik=";
        final String message = "Tess";

        final BitcoinMessageSignature signature = BitcoinMessageSignature.fromBase64(signatureString);
        final Address address = addressInflater.fromBase58Check(addressString).getBytes();
        final Boolean isValid = BitcoinUtil.verifyBitcoinMessage(message, address, signature);

        Assert.assertFalse(isValid);
    }

    @Test
    public void should_not_verify_bitcoin_message_bad_address() {
        final AddressInflater addressInflater = new AddressInflater();

        final String addressString = "1FZHv7fubXkMcgbDBUeehgPf28cHP86f7V";
        final String signatureString = "ILoOBJK9kVKsdUOnJPPoDtrDtRSQw2pyMo+2r5bdUlNkSLDZLqMs8h9mfDm/alZo3DK6rKvTO0xRPrl6DPDpEik=";
        final String message = "Test";

        final BitcoinMessageSignature signature = BitcoinMessageSignature.fromBase64(signatureString);
        final Address address = addressInflater.fromBase58Check(addressString).getBytes();
        final Boolean isValid = BitcoinUtil.verifyBitcoinMessage(message, address, signature);

        Assert.assertFalse(isValid);
    }

    @Test
    public void should_not_verify_bitcoin_message_bad_recovery_id_low() {
        final AddressInflater addressInflater = new AddressInflater();

        // specifies 0 recovery ID instead of 1, renders a valid point but fails the address test
        final String addressString = "1BqtNgMrDXnCek3cdDVSer4BK7knNTDTSR";
        final String signatureString = "H7oOBJK9kVKsdUOnJPPoDtrDtRSQw2pyMo+2r5bdUlNkSLDZLqMs8h9mfDm/alZo3DK6rKvTO0xRPrl6DPDpEik=";
        final String message = "Test";

        final BitcoinMessageSignature signature = BitcoinMessageSignature.fromBase64(signatureString);
        final Address address = addressInflater.fromBase58Check(addressString).getBytes();
        final Boolean isValid = BitcoinUtil.verifyBitcoinMessage(message, address, signature);

        Assert.assertFalse(isValid);
    }

    @Test
    public void should_not_verify_bitcoin_message_bad_recovery_id_high() {
        final AddressInflater addressInflater = new AddressInflater();

        final String addressString = "1BqtNgMrDXnCek3cdDVSer4BK7knNTDTSR";
        final String signatureString = "IboOBJK9kVKsdUOnJPPoDtrDtRSQw2pyMo+2r5bdUlNkSLDZLqMs8h9mfDm/alZo3DK6rKvTO0xRPrl6DPDpEik=";
        final String message = "Test";

        final BitcoinMessageSignature signature = BitcoinMessageSignature.fromBase64(signatureString);
        final Address address = addressInflater.fromBase58Check(addressString).getBytes();
        final Boolean isValid = BitcoinUtil.verifyBitcoinMessage(message, address, signature);

        Assert.assertFalse(isValid);
    }
}
