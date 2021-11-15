package com.softwareverde.bitcoin.secp256k1.privatekey;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import org.junit.Assert;
import org.junit.Test;

public class PrivateKeyInflaterTests extends UnitTest {
    @Test
    public void shouldInflateUncompressedWifKey() {
        // Setup
        final PrivateKeyInflater privateKeyInflater = new PrivateKeyInflater();
        final PrivateKey expectedPrivateKey = PrivateKey.fromHexString("0C28FCA386C7A227600B2FE50B7CAE11EC86D3BF1FBE471BE89827E19D72AA1D");

        // Action
        final PrivateKey privateKey = privateKeyInflater.fromWalletImportFormat("5HueCGU8rMjxEXxiPuD5BDku4MkFqeZyd4dZ1jvhTVqvbTLvyTJ");

        // Assert
        Assert.assertEquals(expectedPrivateKey, privateKey);
    }

    @Test
    public void shouldDeflateUncompressedWifKey() {
        // Setup
        final PrivateKeyDeflater privateKeyDeflater = new PrivateKeyDeflater();
        final PrivateKey privateKey = PrivateKey.fromHexString("0C28FCA386C7A227600B2FE50B7CAE11EC86D3BF1FBE471BE89827E19D72AA1D");
        final String expectedPrivateKeyString = "5HueCGU8rMjxEXxiPuD5BDku4MkFqeZyd4dZ1jvhTVqvbTLvyTJ";

        // Action
        final String wifString = privateKeyDeflater.toWalletImportFormat(privateKey, false);

        // Assert
        Assert.assertEquals(expectedPrivateKeyString, wifString);
    }

    @Test
    public void shouldInflateCompressedWifKey() {
        // Setup
        final PrivateKeyInflater privateKeyInflater = new PrivateKeyInflater();
        final PrivateKey expectedPrivateKey = PrivateKey.fromHexString("8147786C4D15106333BF278D71DADAF1079EF2D2440A4DDE37D747DED5403592");

        // Action
        final PrivateKey privateKey = privateKeyInflater.fromWalletImportFormat("L1Z1h2jcpzw5KKJcPMeV17kWoQVZ15BEHYCFzgpdSNY1c3Vpnk8Z");

        // Assert
        Assert.assertEquals(expectedPrivateKey, privateKey);
    }

    @Test
    public void shouldDeflateCompressedWifKey() {
        // Setup
        final PrivateKeyDeflater privateKeyDeflater = new PrivateKeyDeflater();
        final PrivateKey privateKey = PrivateKey.fromHexString("8147786C4D15106333BF278D71DADAF1079EF2D2440A4DDE37D747DED5403592");
        final String expectedPrivateKeyString = "L1Z1h2jcpzw5KKJcPMeV17kWoQVZ15BEHYCFzgpdSNY1c3Vpnk8Z";

        // Action
        final String wifString = privateKeyDeflater.toWalletImportFormat(privateKey, true);

        // Assert
        Assert.assertEquals(expectedPrivateKeyString, wifString);
    }
}
