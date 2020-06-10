package com.softwareverde.security.secp256k1;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.security.secp256k1.key.PrivateKey;
import com.softwareverde.security.secp256k1.key.PublicKey;
import com.softwareverde.security.secp256k1.signature.SchnorrSignature;
import com.softwareverde.security.secp256k1.signature.Signature;
import com.softwareverde.security.util.HashUtil;
import com.softwareverde.util.HexUtil;
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
        final MutableByteArray messageHash = MutableByteArray.wrap(HashUtil.doubleSha256(message.getBytes()));

        Assert.assertEquals("5255683DA567900BFD3E786ED8836A4E7763C221BF1AC20ECE2A5171B9199E8A", messageHash.toString());

        final ByteArray signatureBytes = ByteArray.fromHexString("2C56731AC2F7A7E7F11518FC7722A166B02438924CA9D8B4D111347B81D0717571846DE67AD3D913A8FDF9D8F3F73161A4C48AE81CB183B214765FEB86E255CE");
        final Signature signature = SchnorrSignature.fromBytes(signatureBytes);

        // Action
        final Boolean wasValid = Schnorr.verifySignature(signature, publicKey, messageHash.unwrap());

        // Assert
        Assert.assertTrue(wasValid);
    }

    @Test
    public void should_validate_bitcoin_abc_test_vector_1() {
        // Setup
        final PublicKey publicKey = PublicKey.fromBytes(ByteArray.fromHexString("0279BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798"));
        final byte[] messageHash = HexUtil.hexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000");
        final Signature signature = SchnorrSignature.fromBytes(ByteArray.fromHexString("787A848E71043D280C50470E8E1532B2DD5D20EE912A45DBDD2BD1DFBF187EF67031A98831859DC34DFFEEDDA86831842CCD0079E1F92AF177F7F22CC1DCED05"));

        // Action
        final Boolean wasValid = Schnorr.verifySignature(signature, publicKey, messageHash);

        // Assert
        Assert.assertTrue(wasValid);
    }

    @Test
    public void should_validate_bitcoin_abc_test_vector_2() {
        // Setup
        final PublicKey publicKey = PublicKey.fromBytes(ByteArray.fromHexString("02DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659"));
        final byte[] messageHash = HexUtil.hexStringToByteArray("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        final Signature signature = SchnorrSignature.fromBytes(ByteArray.fromHexString("2A298DACAE57395A15D0795DDBFD1DCB564DA82B0F269BC70A74F8220429BA1D1E51A22CCEC35599B8F266912281F8365FFC2D035A230434A1A64DC59F7013FD"));

        // Action
        final Boolean wasValid = Schnorr.verifySignature(signature, publicKey, messageHash);

        // Assert
        Assert.assertTrue(wasValid);
    }

    @Test
    public void should_validate_bitcoin_abc_test_vector_3() {
        // Setup
        final PublicKey publicKey = PublicKey.fromBytes(ByteArray.fromHexString("03FAC2114C2FBB091527EB7C64ECB11F8021CB45E8E7809D3C0938E4B8C0E5F84B"));
        final byte[] messageHash = HexUtil.hexStringToByteArray("5E2D58D8B3BCDF1ABADEC7829054F90DDA9805AAB56C77333024B9D0A508B75C");
        final Signature signature = SchnorrSignature.fromBytes(ByteArray.fromHexString("00DA9B08172A9B6F0466A2DEFD817F2D7AB437E0D253CB5395A963866B3574BE00880371D01766935B92D2AB4CD5C8A2A5837EC57FED7660773A05F0DE142380"));

        // Action
        final Boolean wasValid = Schnorr.verifySignature(signature, publicKey, messageHash);

        // Assert
        Assert.assertTrue(wasValid);
    }

    @Test
    public void should_validate_bitcoin_abc_test_vector_4a() {
        // Setup
        final PublicKey publicKey = PublicKey.fromBytes(ByteArray.fromHexString("03DEFDEA4CDB677750A420FEE807EACF21EB9898AE79B9768766E4FAA04A2D4A34"));
        final byte[] messageHash = HexUtil.hexStringToByteArray("4DF3C3F68FCC83B27E9D42C90431A72499F17875C81A599B566C9889B9696703");
        final Signature signature = SchnorrSignature.fromBytes(ByteArray.fromHexString("00000000000000000000003B78CE563F89A0ED9414F5AA28AD0D96D6795F9C6302A8DC32E64E86A333F20EF56EAC9BA30B7246D6D25E22ADB8C6BE1AEB08D49D"));

        // Action
        final Boolean wasValid = Schnorr.verifySignature(signature, publicKey, messageHash);

        // Assert
        Assert.assertTrue(wasValid);
    }

    @Test
    public void should_validate_bitcoin_abc_test_vector_4b() {
        // Setup
        final PublicKey publicKey = PublicKey.fromBytes(ByteArray.fromHexString("031B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F"));
        final byte[] messageHash = HexUtil.hexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000");
        final Signature signature = SchnorrSignature.fromBytes(ByteArray.fromHexString("52818579ACA59767E3291D91B76B637BEF062083284992F2D95F564CA6CB4E3530B1DA849C8E8304ADC0CFE870660334B3CFC18E825EF1DB34CFAE3DFC5D8187"));

        // Action
        final Boolean wasValid = Schnorr.verifySignature(signature, publicKey, messageHash);

        // Assert
        Assert.assertTrue(wasValid);
    }


    @Test
    public void should_not_validate_bitcoin_abc_test_vector_6() {
        // R.y is not a quadratic residue.

        // Setup
        final PublicKey publicKey = PublicKey.fromBytes(ByteArray.fromHexString("02DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659"));
        final byte[] messageHash = HexUtil.hexStringToByteArray("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        final Signature signature = SchnorrSignature.fromBytes(ByteArray.fromHexString("2A298DACAE57395A15D0795DDBFD1DCB564DA82B0F269BC70A74F8220429BA1DFA16AEE06609280A19B67A24E1977E4697712B5FD2943914ECD5F730901B4AB7"));

        // Action
        final Boolean wasValid = Schnorr.verifySignature(signature, publicKey, messageHash);

        // Assert
        Assert.assertFalse(wasValid);
    }

    @Test
    public void should_not_validate_bitcoin_abc_test_vector_7() {
        // Negated message hash; R.x mismatch.

        // Setup
        final PublicKey publicKey = PublicKey.fromBytes(ByteArray.fromHexString("03FAC2114C2FBB091527EB7C64ECB11F8021CB45E8E7809D3C0938E4B8C0E5F84B"));
        final byte[] messageHash = HexUtil.hexStringToByteArray("5E2D58D8B3BCDF1ABADEC7829054F90DDA9805AAB56C77333024B9D0A508B75C");
        final Signature signature = SchnorrSignature.fromBytes(ByteArray.fromHexString("00DA9B08172A9B6F0466A2DEFD817F2D7AB437E0D253CB5395A963866B3574BED092F9D860F1776A1F7412AD8A1EB50DACCC222BC8C0E26B2056DF2F273EFDEC"));

        // Action
        final Boolean wasValid = Schnorr.verifySignature(signature, publicKey, messageHash);

        // Assert
        Assert.assertFalse(wasValid);
    }

    @Test
    public void should_not_validate_bitcoin_abc_test_vector_8() {
        // Negated s, R.x mismatch.

        // Setup
        final PublicKey publicKey = PublicKey.fromBytes(ByteArray.fromHexString("0279BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798"));
        final byte[] messageHash = HexUtil.hexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000");
        final Signature signature = SchnorrSignature.fromBytes(ByteArray.fromHexString("787A848E71043D280C50470E8E1532B2DD5D20EE912A45DBDD2BD1DFBF187EF68FCE5677CE7A623CB20011225797CE7A8DE1DC6CCD4F754A47DA6C600E59543C"));

        // Action
        final Boolean wasValid = Schnorr.verifySignature(signature, publicKey, messageHash);

        // Assert
        Assert.assertFalse(wasValid);
    }

    @Test
    public void should_not_validate_bitcoin_abc_test_vector_9() {
        // Negated P, R.x mismatch.

        // Setup
        final PublicKey publicKey = PublicKey.fromBytes(ByteArray.fromHexString("03DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659"));
        final byte[] messageHash = HexUtil.hexStringToByteArray("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        final Signature signature = SchnorrSignature.fromBytes(ByteArray.fromHexString("2A298DACAE57395A15D0795DDBFD1DCB564DA82B0F269BC70A74F8220429BA1D1E51A22CCEC35599B8F266912281F8365FFC2D035A230434A1A64DC59F7013FD"));

        // Action
        final Boolean wasValid = Schnorr.verifySignature(signature, publicKey, messageHash);

        // Assert
        Assert.assertFalse(wasValid);
    }

    @Test
    public void should_not_validate_bitcoin_abc_test_vector_10() {
        // s * G = e * P, R = 0

        // Setup
        final PublicKey publicKey = PublicKey.fromBytes(ByteArray.fromHexString("02DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659"));
        final byte[] messageHash = HexUtil.hexStringToByteArray("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        final Signature signature = SchnorrSignature.fromBytes(ByteArray.fromHexString("2A298DACAE57395A15D0795DDBFD1DCB564DA82B0F269BC70A74F8220429BA1D8C3428869A663ED1E954705B020CBB3E7BB6AC31965B9EA4C73E227B17C5AF5A"));

        // Action
        final Boolean wasValid = Schnorr.verifySignature(signature, publicKey, messageHash);

        // Assert
        Assert.assertFalse(wasValid);
    }

    @Test
    public void should_not_validate_bitcoin_abc_test_vector_11() {
        // R.x not on the curve, R.x mismatch.

        // Setup
        final PublicKey publicKey = PublicKey.fromBytes(ByteArray.fromHexString("02DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659"));
        final byte[] messageHash = HexUtil.hexStringToByteArray("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        final Signature signature = SchnorrSignature.fromBytes(ByteArray.fromHexString("4A298DACAE57395A15D0795DDBFD1DCB564DA82B0F269BC70A74F8220429BA1D1E51A22CCEC35599B8F266912281F8365FFC2D035A230434A1A64DC59F7013FD"));

        // Action
        final Boolean wasValid = Schnorr.verifySignature(signature, publicKey, messageHash);

        // Assert
        Assert.assertFalse(wasValid);
    }

    @Test
    public void should_not_validate_bitcoin_abc_test_vector_12() {
        // r = p

        // Setup
        final PublicKey publicKey = PublicKey.fromBytes(ByteArray.fromHexString("02DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659"));
        final byte[] messageHash = HexUtil.hexStringToByteArray("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        final Signature signature = SchnorrSignature.fromBytes(ByteArray.fromHexString("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFC2F1E51A22CCEC35599B8F266912281F8365FFC2D035A230434A1A64DC59F7013FD"));

        // Action
        final Boolean wasValid = Schnorr.verifySignature(signature, publicKey, messageHash);

        // Assert
        Assert.assertFalse(wasValid);
    }

    @Test
    public void should_not_validate_bitcoin_abc_test_vector_13() {
        // s = n

        // Setup
        final PublicKey publicKey = PublicKey.fromBytes(ByteArray.fromHexString("02DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659"));
        final byte[] messageHash = HexUtil.hexStringToByteArray("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        final Signature signature = SchnorrSignature.fromBytes(ByteArray.fromHexString("2A298DACAE57395A15D0795DDBFD1DCB564DA82B0F269BC70A74F8220429BA1DFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141"));

        // Action
        final Boolean wasValid = Schnorr.verifySignature(signature, publicKey, messageHash);

        // Assert
        Assert.assertFalse(wasValid);
    }
}
