package com.softwareverde.bitcoin.address;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class AddressInflaterTests {
    @Test
    public void should_inflate_base32_address() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32AddressString = "bitcoincash:qqswr73n8gzgsygazzfn9qm3qk46dtescsyrzewzuj";

        // Action
        final ParsedAddress address = addressInflater.fromBase32Check(base32AddressString);

        // Assert
        Assert.assertNotNull(address);
    }

    @Test
    public void should_return_null_for_invalid_base32_address() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32AddressString = "http://bitcoincash:qqswr73n8gzgsygazzfn9qm3qk46dtescsyrzewzuj";

        // Action
        final ParsedAddress address = addressInflater.fromBase32Check(base32AddressString);

        // Assert
        Assert.assertNull(address);
    }

    @Test
    public void should_create_valid_PublicKey_from_PrivateKey_when_even() {
        // Setup
        final byte[] privateKeyBytes = HexUtil.hexStringToByteArray("2BFF39B5C19936A991508F6A746D543524C1F02DC935374F3CD994CC7927191F");

        final String expectedAddressString = "12Y8pRcJSrXCy1NuTuzTnPVbmM7Q63vTVo";
        final String expectedCompressedAddressString = "152sweTR1yQsihW88hStanc6EN9aiKX87C";
        final byte[] expectedPublicKey = HexUtil.hexStringToByteArray("04889CC9F2F56A9C8E7BF0437EA41ECEB2C7375780049D6EBB0B8D542CA7AFA46F7C8AFD5C91566ECA2BE4B477BCB808FDF45140D811683A06B36321DE09FDB9E0");
        final byte[] expectedCompressedPublicKey = HexUtil.hexStringToByteArray("02889CC9F2F56A9C8E7BF0437EA41ECEB2C7375780049D6EBB0B8D542CA7AFA46F");

        final PrivateKey privateKey = PrivateKey.fromBytes(MutableByteArray.wrap(privateKeyBytes));

        final AddressInflater addressInflater = new AddressInflater();

        // Action
        final Address bitcoinAddress = addressInflater.fromPrivateKey(privateKey, false);
        final Address compressedAddress = addressInflater.fromPrivateKey(privateKey, true);
        final PublicKey publicKey = privateKey.getPublicKey();
        final PublicKey compressedPublicKey = publicKey.compress();

        // Assert
        TestUtil.assertEqual(expectedPublicKey, publicKey.getBytes());
        TestUtil.assertEqual(expectedCompressedPublicKey, compressedPublicKey.getBytes());
        Assert.assertEquals(expectedAddressString, ParsedAddress.toBase58CheckEncoded(bitcoinAddress));
        Assert.assertEquals(expectedCompressedAddressString, ParsedAddress.toBase58CheckEncoded(compressedAddress));
    }

    @Test
    public void should_create_valid_PublicKey_from_PrivateKey_when_odd() {
        // Setup
        final byte[] privateKeyBytes = HexUtil.hexStringToByteArray("B0174AFBDF028F81BCD9ED3D7CB40A66CC1AB1C01150BDDE594E9161E5F868A9");

        final String expectedAddressString = "1F9FjEKKHGnocaep99pqQL6oXv33vumjAz";
        final String expectedCompressedAddressString = "1Bp3kke8HczZqWpQw9L5uvufunnYbGwKMZ";
        final byte[] expectedPublicKey = HexUtil.hexStringToByteArray("040DD64F3EC3AEBBE5FD7E5C7360AD7A02D5E0648BFAA11ABD1BCD8A747D4D72EAB6A65A9D842943C631D2BB1B261179EE801DD09989B8DFFE3A49DE396CB4FB09");
        final byte[] expectedCompressedPublicKey = HexUtil.hexStringToByteArray("030DD64F3EC3AEBBE5FD7E5C7360AD7A02D5E0648BFAA11ABD1BCD8A747D4D72EA");

        final PrivateKey privateKey = PrivateKey.fromBytes(MutableByteArray.wrap(privateKeyBytes));

        final AddressInflater addressInflater = new AddressInflater();

        // Action
        final Address bitcoinAddress = addressInflater.fromPrivateKey(privateKey);
        final Address compressedAddress = addressInflater.fromPrivateKey(privateKey, true);
        final PublicKey publicKey = privateKey.getPublicKey();
        final PublicKey compressedPublicKey = publicKey.compress();

        // Assert
        TestUtil.assertEqual(expectedPublicKey, publicKey.getBytes());
        TestUtil.assertEqual(expectedCompressedPublicKey, compressedPublicKey.getBytes());
        Assert.assertEquals(expectedAddressString, ParsedAddress.toBase58CheckEncoded(bitcoinAddress));
        Assert.assertEquals(expectedCompressedAddressString, ParsedAddress.toBase58CheckEncoded(compressedAddress));
    }

    @Test
    public void should_create_valid_address_from_compressed_public_key() {
        // Setup
        final byte[] privateKeyBytes = HexUtil.hexStringToByteArray("2BFF39B5C19936A991508F6A746D543524C1F02DC935374F3CD994CC7927191F");

        final byte[] expectedPublicKey = HexUtil.hexStringToByteArray("04889CC9F2F56A9C8E7BF0437EA41ECEB2C7375780049D6EBB0B8D542CA7AFA46F7C8AFD5C91566ECA2BE4B477BCB808FDF45140D811683A06B36321DE09FDB9E0");
        final byte[] expectedCompressedPublicKey = HexUtil.hexStringToByteArray("02889CC9F2F56A9C8E7BF0437EA41ECEB2C7375780049D6EBB0B8D542CA7AFA46F");
        final String expectedAddressString = "12Y8pRcJSrXCy1NuTuzTnPVbmM7Q63vTVo";
        final String expectedCompressedAddressString = "152sweTR1yQsihW88hStanc6EN9aiKX87C";

        final PrivateKey privateKey = PrivateKey.fromBytes(MutableByteArray.wrap(privateKeyBytes));
        final PublicKey publicKey = privateKey.getPublicKey();
        final PublicKey compressedPublicKey = publicKey.compress();
        final PublicKey decompressedPublicKey = compressedPublicKey.decompress();

        final AddressInflater addressInflater = new AddressInflater();

        // Action
        final Address compressedAddress = addressInflater.fromPublicKey(compressedPublicKey);
        final Address decompressedBitcoinAddress = addressInflater.fromPublicKey(decompressedPublicKey);

        // Assert
        TestUtil.assertEqual(expectedPublicKey, decompressedPublicKey.getBytes());
        TestUtil.assertEqual(expectedCompressedPublicKey, compressedPublicKey.getBytes());

        Assert.assertEquals(expectedAddressString, ParsedAddress.toBase58CheckEncoded(decompressedBitcoinAddress));
        Assert.assertEquals(expectedCompressedAddressString, ParsedAddress.toBase58CheckEncoded(compressedAddress));
    }

    @Test
    public void should_return_null_for_invalid_slp_address() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        //                                     "simpleledger:qpkpqwnht42ne6gzx3qtuuwjge2gpyqnkszwkc9rgz";
        final String invalidSlpAddressString = "simpleledger:qpkpqwnht42ne6gzx3qtuuwjge2gpyqnkszwkc9rg"; // Missing the final letter of the checksum...

        // Action
        final ParsedAddress address = addressInflater.fromBase32Check(invalidSlpAddressString);

        // Assert
        Assert.assertNull(address);
    }

    @Test
    public void should_use_p2sh_prefix() {
        // Setup
        final String transactionHex = "010000000588D648A1E60AF8B6D78235DE10CF3FD4E97178EC2CD333976899C4B22AB63845000000006441029C48DD960D5ABBF05DF0781B03DD97B0EBE278F69443F7A3F3F96E964737203B60497D56B778E523135E63F088D8F05A92C0F4F6C4CDB696B8EF65F7F4978541210378E9FA40D4F4A1D4E0CF20DD1F82F9DADA22EF0A6517B1D756B175EFA2E2FB10FEFFFFFFD2FAC5EFD5F34E0AC38B1B5E8B45D0F92480FF80062F11259F5CCE4DB6452479000000006441DA1B4262163344BF7D7070046784D2DA3EAC95AEF55D2BB2C1804050F3F500B4BDED03353A8F3B5F6216D6AA2E5445FAD1C91BFECF249C34EEA42283510FA66241210378E9FA40D4F4A1D4E0CF20DD1F82F9DADA22EF0A6517B1D756B175EFA2E2FB10FEFFFFFFFB701640BF570EC409D8C4B4310776C193051ADE88CA717137053492A6D88F88000000006441A27698005F77DF2398AF43775E2C0602A37FB59C2732665A343B5685944C88FDFDC313D07C839ED5D7B251736D815E3F304340DB770E71D6BDDFA22C0F7FC35F41210378E9FA40D4F4A1D4E0CF20DD1F82F9DADA22EF0A6517B1D756B175EFA2E2FB10FEFFFFFFF60B7554B2D1D7BC5FC2514074B79BA613D544D0FBA85A7D952613CAFCE815B1000000006441EEFF095C6DAF222B554840EF91ED7325384B705F3A479241C1B54C260B611BD42289E69465506D24EBDB84F7F870977EE70C843B3450256460B6C6738384ABA641210378E9FA40D4F4A1D4E0CF20DD1F82F9DADA22EF0A6517B1D756B175EFA2E2FB10FEFFFFFFD67AFA5F3B391062932465F379AF7BE29465B6B36676F4C178A60827124DCCFF00000000644184BA27AE0BF55606B0009277C809E4081905C56807AE66F52AB557F83C5E58605C0ABBC30BF63F80EE21E67D90C7054ED667822FD6E84CF1E98D6ACD0F08F72041210378E9FA40D4F4A1D4E0CF20DD1F82F9DADA22EF0A6517B1D756B175EFA2E2FB10FEFFFFFF0264DC8200000000001976A914E508810BD68B0740DA449779D4210F94E17B54E188AC809698000000000017A914DC4683722DBDEDB04F3A45886020C573BA05DDC78704140100";
        final TransactionInflater transactionInflater = new TransactionInflater();
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final Transaction transaction = transactionInflater.fromBytes(ByteArray.fromHexString(transactionHex));
        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

        final TransactionOutput transactionOutput0 = transactionOutputs.get(0);
        final TransactionOutput transactionOutput1 = transactionOutputs.get(1);

        final String expectedAddressString0 = "bchtest:qrjs3qgt669swsx6gjthn4ppp72wz765uyesnnydww"; // P2PKH
        final String expectedAddressString1 = "bchtest:prwydqmj9k77mvz08fzcscpqc4em5pwacu42etgjcc"; // P2SH

        // Action
        final Address address0 = scriptPatternMatcher.extractAddress(transactionOutput0.getLockingScript());
        final Address address1 = scriptPatternMatcher.extractAddress(transactionOutput1.getLockingScript());

        // Assert
        Assert.assertEquals(expectedAddressString0, new ParsedAddress(AddressType.P2PKH, false, address0, AddressFormat.BASE_32, "bchtest").toString());
        Assert.assertEquals(expectedAddressString1, new ParsedAddress(AddressType.P2SH, false, address1, AddressFormat.BASE_32, "bchtest").toString());
    }
}
