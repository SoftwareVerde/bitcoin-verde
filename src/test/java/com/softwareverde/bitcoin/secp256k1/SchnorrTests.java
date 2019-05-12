package com.softwareverde.bitcoin.secp256k1;

import com.softwareverde.bitcoin.secp256k1.key.PrivateKey;
import com.softwareverde.bitcoin.secp256k1.key.PublicKey;
import com.softwareverde.bitcoin.secp256k1.signature.SchnorrSignature;
import com.softwareverde.bitcoin.secp256k1.signature.Signature;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import org.junit.Assert;
import org.junit.Test;

public class SchnorrTests {
    @Test
    public void should_validate_bitcoin_abc_schnorr_signature() {
        // Setup
        final PrivateKey privateKey = PrivateKey.fromHexString("12B004FFF7F4B69EF8650E767F18F11EDE158148B425660723B9F9A66E61F747");
        final PublicKey publicKey = privateKey.getPublicKey().compress();

        Assert.assertEquals("030B4C866585DD868A9D62348A9CD008D6A312937048FFF31670E7E920CFC7A744", publicKey.toString());

        final String message = "Very deterministic message";
        final MutableByteArray messageHash = MutableByteArray.wrap(BitcoinUtil.sha256(BitcoinUtil.sha256(message.getBytes())));

        Assert.assertEquals("5255683DA567900BFD3E786ED8836A4E7763C221BF1AC20ECE2A5171B9199E8A", messageHash.toString());

        final ByteArray signatureBytes = ByteArray.fromHexString("2C56731AC2F7A7E7F11518FC7722A166B02438924CA9D8B4D111347B81D0717571846DE67AD3D913A8FDF9D8F3F73161A4C48AE81CB183B214765FEB86E255CE");
        final Signature signature = SchnorrSignature.fromBytes(signatureBytes);

        // Action
        final Boolean wasValid = Schnorr.verifySignature(signature, publicKey, messageHash.unwrap());

        // Assert
        Assert.assertTrue(wasValid);
    }
}
