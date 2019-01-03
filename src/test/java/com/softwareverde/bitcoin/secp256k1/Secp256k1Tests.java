package com.softwareverde.bitcoin.secp256k1;

import com.softwareverde.bitcoin.jni.NativeSecp256k1;
import com.softwareverde.bitcoin.secp256k1.key.PrivateKey;
import com.softwareverde.bitcoin.secp256k1.key.PublicKey;
import com.softwareverde.bitcoin.secp256k1.signature.Signature;
import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.util.StringUtil;
import org.junit.Assert;
import org.junit.Test;

public class Secp256k1Tests {
    @Test
    public void should_create_and_verify_signature_bouncy_castle() {
        long elapsed = 0L;
        for (int i=0; i<128; ++i) {
            // Setup
            final PrivateKey privateKey = PrivateKey.createNewKey();
            final PublicKey publicKey = privateKey.getPublicKey();
            final byte[] message = StringUtil.stringToBytes("I am a little teapot." + i);

            // Action
            final Signature signature = Secp256k1.sign(privateKey.getBytes(), message);
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
        for (int i=0; i<128; ++i) {
            // Setup
            final PrivateKey privateKey = PrivateKey.createNewKey();
            final PublicKey publicKey = privateKey.getPublicKey();
            final PublicKey compressedPublicKey = publicKey.compress();
            TestUtil.assertEqual(compressedPublicKey.getBytes(), publicKey.compress().getBytes());
            final byte[] message = BitcoinUtil.sha256(StringUtil.stringToBytes("I am a little teapot." + i));

            // Action
            final Signature signature = Secp256k1.sign(privateKey.getBytes(), message);
            long start = System.currentTimeMillis();
            final Boolean signatureIsValid = NativeSecp256k1.verify(message, signature.encodeAsDer().getBytes(), publicKey.getBytes());
            elapsed += System.currentTimeMillis() - start;

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
        final byte[] message = BitcoinUtil.sha256(StringUtil.stringToBytes("I am a little teapot."));

        // Action
        final Signature signature = Secp256k1.sign(privateKey.getBytes(), message);
        final Boolean signatureIsValid = Secp256k1.verifySignature(signature, publicKey, message);

        // Assert
        Assert.assertTrue(signatureIsValid);
    }
}
