package com.softwareverde.bitcoin.type.key;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.type.address.Address;
import com.softwareverde.bitcoin.type.address.AddressInflater;
import com.softwareverde.bitcoin.type.address.CompressedAddress;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class PrivateKeyTests {
    @Test
    public void should_create_valid_PublicKey_from_PrivateKey_when_even() {
        // Setup
        final byte[] privateKeyBytes = HexUtil.hexStringToByteArray("2BFF39B5C19936A991508F6A746D543524C1F02DC935374F3CD994CC7927191F");

        final String expectedAddressString = "12Y8pRcJSrXCy1NuTuzTnPVbmM7Q63vTVo";
        final String expectedCompressedAddressString = "152sweTR1yQsihW88hStanc6EN9aiKX87C";
        final byte[] expectedAddress = BitcoinUtil.base58StringToBytes(expectedAddressString);
        final byte[] expectedCompressedAddress = BitcoinUtil.base58StringToBytes(expectedCompressedAddressString);
        final byte[] expectedPublicKey = HexUtil.hexStringToByteArray("04889CC9F2F56A9C8E7BF0437EA41ECEB2C7375780049D6EBB0B8D542CA7AFA46F7C8AFD5C91566ECA2BE4B477BCB808FDF45140D811683A06B36321DE09FDB9E0");
        final byte[] expectedCompressedPublicKey = HexUtil.hexStringToByteArray("02889CC9F2F56A9C8E7BF0437EA41ECEB2C7375780049D6EBB0B8D542CA7AFA46F");

        final PrivateKey privateKey = new PrivateKey(privateKeyBytes);

        final AddressInflater addressInflater = new AddressInflater();

        // Action
        final Address bitcoinAddress = addressInflater.fromPrivateKey(privateKey);
        final CompressedAddress compressedAddress = addressInflater.compressedFromPrivateKey(privateKey);
        final PublicKey publicKey = privateKey.getPublicKey();
        final PublicKey compressedPublicKey = publicKey.compress();

        // Assert
        TestUtil.assertEqual(expectedAddress, bitcoinAddress.getBytesWithChecksum());
        TestUtil.assertEqual(expectedCompressedAddress, compressedAddress.getBytesWithChecksum());
        TestUtil.assertEqual(expectedPublicKey, publicKey.getBytes());
        TestUtil.assertEqual(expectedCompressedPublicKey, compressedPublicKey.getBytes());
        Assert.assertEquals(expectedAddressString, bitcoinAddress.toBase58CheckEncoded());
        Assert.assertEquals(expectedCompressedAddressString, compressedAddress.toBase58CheckEncoded());
    }

    @Test
    public void should_create_valid_PublicKey_from_PrivateKey_when_odd() {
        // Setup
        final byte[] privateKeyBytes = HexUtil.hexStringToByteArray("B0174AFBDF028F81BCD9ED3D7CB40A66CC1AB1C01150BDDE594E9161E5F868A9");

        final String expectedAddressString = "1F9FjEKKHGnocaep99pqQL6oXv33vumjAz";
        final String expectedCompressedAddressString = "1Bp3kke8HczZqWpQw9L5uvufunnYbGwKMZ";
        final byte[] expectedAddress = BitcoinUtil.base58StringToBytes(expectedAddressString);
        final byte[] expectedCompressedAddress = BitcoinUtil.base58StringToBytes(expectedCompressedAddressString);
        final byte[] expectedPublicKey = HexUtil.hexStringToByteArray("040DD64F3EC3AEBBE5FD7E5C7360AD7A02D5E0648BFAA11ABD1BCD8A747D4D72EAB6A65A9D842943C631D2BB1B261179EE801DD09989B8DFFE3A49DE396CB4FB09");
        final byte[] expectedCompressedPublicKey = HexUtil.hexStringToByteArray("030DD64F3EC3AEBBE5FD7E5C7360AD7A02D5E0648BFAA11ABD1BCD8A747D4D72EA");

        final PrivateKey privateKey = new PrivateKey(privateKeyBytes);

        final AddressInflater addressInflater = new AddressInflater();

        // Action
        final Address bitcoinAddress = addressInflater.fromPrivateKey(privateKey);
        final CompressedAddress compressedAddress = addressInflater.compressedFromPrivateKey(privateKey);
        final PublicKey publicKey = privateKey.getPublicKey();
        final PublicKey compressedPublicKey = publicKey.compress();

        // Assert
        TestUtil.assertEqual(expectedAddress, bitcoinAddress.getBytesWithChecksum());
        TestUtil.assertEqual(expectedCompressedAddress, compressedAddress.getBytesWithChecksum());
        TestUtil.assertEqual(expectedPublicKey, publicKey.getBytes());
        TestUtil.assertEqual(expectedCompressedPublicKey, compressedPublicKey.getBytes());
        Assert.assertEquals(expectedAddressString, bitcoinAddress.toBase58CheckEncoded());
        Assert.assertEquals(expectedCompressedAddressString, compressedAddress.toBase58CheckEncoded());
    }

    @Test
    public void should_create_valid_address_from_compressed_public_key() {
        // Setup
        final byte[] privateKeyBytes = HexUtil.hexStringToByteArray("2BFF39B5C19936A991508F6A746D543524C1F02DC935374F3CD994CC7927191F");

        final byte[] expectedPublicKey = HexUtil.hexStringToByteArray("04889CC9F2F56A9C8E7BF0437EA41ECEB2C7375780049D6EBB0B8D542CA7AFA46F7C8AFD5C91566ECA2BE4B477BCB808FDF45140D811683A06B36321DE09FDB9E0");
        final byte[] expectedCompressedPublicKey = HexUtil.hexStringToByteArray("02889CC9F2F56A9C8E7BF0437EA41ECEB2C7375780049D6EBB0B8D542CA7AFA46F");
        final String expectedAddressString = "12Y8pRcJSrXCy1NuTuzTnPVbmM7Q63vTVo";
        final String expectedCompressedAddressString = "152sweTR1yQsihW88hStanc6EN9aiKX87C";

        final PrivateKey privateKey = new PrivateKey(privateKeyBytes);
        final PublicKey publicKey = privateKey.getPublicKey();
        final PublicKey compressedPublicKey = publicKey.compress();
        final PublicKey decompressedPublicKey = compressedPublicKey.decompress();

        final AddressInflater addressInflater = new AddressInflater();

        // Action
        final CompressedAddress compressedAddress = addressInflater.compressedFromPublicKey(compressedPublicKey);
        final Address decompressedBitcoinAddress = addressInflater.fromPublicKey(decompressedPublicKey);

        // Assert
        TestUtil.assertEqual(expectedPublicKey, decompressedPublicKey.getBytes());
        TestUtil.assertEqual(expectedCompressedPublicKey, compressedPublicKey.getBytes());

        Assert.assertEquals(expectedAddressString, decompressedBitcoinAddress.toBase58CheckEncoded());
        Assert.assertEquals(expectedCompressedAddressString, compressedAddress.toBase58CheckEncoded());
    }
}
