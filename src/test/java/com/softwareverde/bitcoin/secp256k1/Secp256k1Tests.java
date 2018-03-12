package com.softwareverde.bitcoin.secp256k1;

import com.softwareverde.bitcoin.secp256k1.signature.Signature;
import com.softwareverde.bitcoin.type.key.PrivateKey;
import com.softwareverde.bitcoin.type.key.PublicKey;
import com.softwareverde.util.StringUtil;
import org.junit.Assert;
import org.junit.Test;

public class Secp256k1Tests {
    @Test
    public void should_create_and_verify_signature() {
        for (int i=0; i<128; ++i) {
            // Setup
            final PrivateKey privateKey = PrivateKey.createNewKey();
            final PublicKey publicKey = privateKey.getPublicKey();
            final byte[] message = StringUtil.stringToBytes("I am a little teapot.");

            // Action
            final Signature signature = Secp256k1.sign(privateKey.getBytes(), message);
            final Boolean signatureIsValid = Secp256k1.verifySignature(signature, publicKey.getBytes(), message);

            // Assert
            Assert.assertTrue(signatureIsValid);
        }
    }
}
