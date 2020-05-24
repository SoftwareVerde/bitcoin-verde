package com.softwareverde.security.secp256k1;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.jni.NativeSecp256k1;
import com.softwareverde.bitcoin.secp256k1.Secp256k1;
import com.softwareverde.bitcoin.secp256k1.signature.BitcoinMessageSignature;
import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.security.secp256k1.key.PrivateKey;
import com.softwareverde.security.secp256k1.key.PublicKey;
import com.softwareverde.security.secp256k1.signature.Signature;
import com.softwareverde.security.util.HashUtil;
import com.softwareverde.util.StringUtil;
import org.junit.Assert;
import org.junit.Test;

public class Secp256k1Tests {
    @Test
    public void should_create_and_verify_signature_bouncy_castle() {
        long elapsed = 0L;
        for (int i = 0; i < 128; ++i) {
            // Setup
            final PrivateKey privateKey = PrivateKey.createNewKey();
            final PublicKey publicKey = privateKey.getPublicKey();
            final byte[] message = StringUtil.stringToBytes("I am a little teapot." + i);

            // Action
            final Signature signature = Secp256k1.sign(privateKey, message);
            long start = System.currentTimeMillis();
            final Boolean signatureIsValid = Secp256k1._verifySignatureViaBouncyCastle(signature, publicKey, message);
            elapsed += System.currentTimeMillis() - start;

            // Assert
            Assert.assertTrue(signatureIsValid);
        }

        System.out.println("Verify via BC: "+ elapsed);
    }

    @Test
    public void should_create_and_verify_signature_native() throws Exception {
        long elapsed = 0L;
        for (int i = 0; i < 128; ++i) {
            // Setup
            final PrivateKey privateKey = PrivateKey.createNewKey();
            final PublicKey publicKey = privateKey.getPublicKey();
            final PublicKey compressedPublicKey = publicKey.compress();
            TestUtil.assertEqual(compressedPublicKey.getBytes(), publicKey.compress().getBytes());
            final byte[] message = HashUtil.sha256(StringUtil.stringToBytes("I am a little teapot." + i));

            // Action
            final Signature signature = Secp256k1.sign(privateKey, message);
            long start = System.currentTimeMillis();
            final boolean signatureIsValid = NativeSecp256k1.verifySignature(message, signature.encode().getBytes(), publicKey.getBytes());
            elapsed += (System.currentTimeMillis() - start);

            // Assert
            Assert.assertTrue(signatureIsValid);
        }

        System.out.println("Verify via JNI: "+ elapsed);
    }

    @Test
    public void should_create_and_verify_signature() {
        // Setup
        final PrivateKey privateKey = PrivateKey.createNewKey();
        final PublicKey publicKey = privateKey.getPublicKey();
        final byte[] message = HashUtil.sha256(StringUtil.stringToBytes("I am a little teapot."));

        // Action
        final Signature signature = Secp256k1.sign(privateKey, message);
        final Boolean signatureIsValid = Secp256k1.verifySignature(signature, publicKey, message);

        // Assert
        Assert.assertTrue(signatureIsValid);
    }

    @Test
    public void should_sign_and_verify_bitcoin_message() {
        final AddressInflater addressInflater = new AddressInflater();

        final PrivateKey privateKey = PrivateKey.fromHexString("948AB5DBDBF277DD81C6754DCBDE3A9E9BB61D0AA146F94D7B802C34D811E571");
        final String message = "Milo the dog.";
        final Boolean useCompressedAddress = true;

        final BitcoinMessageSignature signature = BitcoinUtil.signBitcoinMessage(privateKey, message, useCompressedAddress);

        final Address address = (useCompressedAddress ? addressInflater.compressedFromPrivateKey(privateKey) : addressInflater.uncompressedFromPrivateKey(privateKey));
        final Boolean isValid = BitcoinUtil.verifyBitcoinMessage(message, address, signature);

        Assert.assertTrue(isValid);
    }

    @Test
    public void should_verify_valid_bitcoin_message() {
        final AddressInflater addressInflater = new AddressInflater();

        final String addressString = "1HZwkjkeaoZfTSaJxDw6aKkxp45agDiEzN";
        final String signatureString = "HGkIGRLabV27C6uO6ePz+rm3Gcbc6urV7KkTWUgjbfcG/nU+qL7kVWi9QFg1rG+0n1UzA0glRNUoqaiRF/Iu85A=";
        final String message = "Mary had a little lamb.";

        final BitcoinMessageSignature signature = BitcoinMessageSignature.fromBase64(signatureString);

        final Address address = addressInflater.uncompressedFromBase58Check(addressString);
        final Boolean isValid = BitcoinUtil.verifyBitcoinMessage(message, address, signature);

        Assert.assertTrue(isValid);
    }
}
