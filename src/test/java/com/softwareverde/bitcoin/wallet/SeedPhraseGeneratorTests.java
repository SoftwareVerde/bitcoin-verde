package com.softwareverde.bitcoin.wallet;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.util.IoUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

public class SeedPhraseGeneratorTests extends UnitTest {

    protected static SeedPhraseGenerator _seedPhraseGenerator;

    @BeforeClass
    public static void beforeClass() throws IOException {
        final String seedWords = IoUtil.getResource("/seed_words/seed_words_english.txt");
        final ImmutableListBuilder<String> seedWordsBuilder = new ImmutableListBuilder<>(2048);
        for (final String seedWord : seedWords.split("\n")) {
            seedWordsBuilder.add(seedWord.trim());
        }

        Assert.assertEquals(2048, seedWordsBuilder.getCount());

        _seedPhraseGenerator = new SeedPhraseGenerator(seedWordsBuilder.build());
    }

    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    /**
     * Used in cases where the sequence is not a valid Secp256k1 key.
     *
     * @param seedKey
     * @param expectedSeedPhrase
     */
    private void _validateSeedPhrase(final String seedKey, final String expectedSeedPhrase) {
        // validate we can generate the correct seed key from the seed phrase
        final ByteArray extractedSeedKeyBytes = _seedPhraseGenerator.fromSeedPhrase(expectedSeedPhrase);
        if (extractedSeedKeyBytes == null) { throw new IllegalArgumentException(); }

        final String extractedSeedKey = extractedSeedKeyBytes.toString();

        Assert.assertEquals(seedKey.toUpperCase(), extractedSeedKey.toUpperCase());

        // validate we can generate the correct seed phrase from the seed key
        final ByteArray seedKeyBytes = ByteArray.fromHexString(seedKey);

        final String phrase = _seedPhraseGenerator.toSeedPhrase(seedKeyBytes);

        Assert.assertEquals(expectedSeedPhrase.toLowerCase(), phrase);
    }

    @Test
    public void should_create_seed_for_min_key() {
        _validateSeedPhrase(
            "0000000000000000000000000000000000000000000000000000000000000001",
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon diesel"
        );
    }

    @Test
    public void should_create_seed_for_max_key() {
        _validateSeedPhrase(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364140",
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo word priority hover one trouble parent target virus rug snack brass agree alpha"
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void should_find_invalid_checksum() {
        _validateSeedPhrase(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364140",
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo word priority hover one trouble parent target virus rug snack brass agree zoo"
        );
    }

    @Test
    public void should_create_seed_for_small_all_zeros_key() {
        _validateSeedPhrase(
            "00000000000000000000000000000000",
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        );
    }

    @Test
    public void should_create_seed_for_small_7Fs_key() {
        _validateSeedPhrase(
            "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f",
            "legal winner thank year wave sausage worth useful legal winner thank yellow"
        );
    }

    @Test
    public void should_create_seed_for_small_80s_key() {
        _validateSeedPhrase(
            "80808080808080808080808080808080",
            "letter advice cage absurd amount doctor acoustic avoid letter advice cage above"
        );
    }

    @Test
    public void should_create_seed_for_small_FFs_key() {
        _validateSeedPhrase(
            "ffffffffffffffffffffffffffffffff",
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong"
        );
    }

    @Test
    public void should_create_seed_for_medium_all_zeros_key() {
        _validateSeedPhrase(
            "000000000000000000000000000000000000000000000000",
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon agent"
        );
    }

    @Test
    public void should_create_seed_for_medium_7Fs_key() {
        _validateSeedPhrase(
            "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f",
            "legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth useful legal will"
        );
    }

    @Test
    public void should_create_seed_for_medium_80s_key() {
        _validateSeedPhrase(
            "808080808080808080808080808080808080808080808080",
            "letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd amount doctor acoustic avoid letter always"
        );
    }

    @Test
    public void should_create_seed_for_medium_FFs_key() {
        _validateSeedPhrase(
            "ffffffffffffffffffffffffffffffffffffffffffffffff",
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo when"
        );
    }

    @Test
    public void should_create_seed_for_long_all_zeros_key() {
        _validateSeedPhrase(
            "0000000000000000000000000000000000000000000000000000000000000000",
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
        );
    }

    @Test
    public void should_create_seed_for_long_7Fs_key() {
        _validateSeedPhrase(
            "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f",
            "legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth title"
        );
    }

    @Test
    public void should_create_seed_for_long_80s_key() {
        _validateSeedPhrase(
            "8080808080808080808080808080808080808080808080808080808080808080",
            "letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd amount doctor acoustic bless"
        );
    }

    @Test
    public void should_create_seed_for_long_FFs_key() {
        _validateSeedPhrase(
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo vote"
        );
    }

    @Test
    public void should_create_seed_for_short_random_key1() {
        _validateSeedPhrase(
            "9e885d952ad362caeb4efe34a8e91bd2",
            "ozone drill grab fiber curtain grace pudding thank cruise elder eight picnic"
        );
    }

    @Test
    public void should_create_seed_for_medium_random_key2() {
        _validateSeedPhrase(
            "6610b25967cdcca9d59875f5cb50b0ea75433311869e930b",
            "gravity machine north sort system female filter attitude volume fold club stay feature office ecology stable narrow fog"
        );
    }

    @Test
    public void should_create_seed_for_long_random_key3() {
        _validateSeedPhrase(
            "68a79eaca2324873eacc50cb9c6eca8cc68ea5d936f98787c60c7ebc74e6ce7c",
            "hamster diagram private dutch cause delay private meat slide toddler razor book happy fancy gospel tennis maple dilemma loan word shrug inflict delay length"
        );
    }

    @Test
    public void should_create_seed_for_short_random_key4() {
        _validateSeedPhrase(
            "c0ba5a8e914111210f2bd131f3d5e08d",
            "scheme spot photo card baby mountain device kick cradle pact join borrow"
        );
    }

    @Test
    public void should_create_seed_for_medium_random_key5() {
        _validateSeedPhrase(
            "6d9be1ee6ebd27a258115aad99b7317b9c8d28b6d76431c3",
            "horn tenant knee talent sponsor spell gate clip pulse soap slush warm silver nephew swap uncle crack brave"
        );
    }

    @Test
    public void should_create_seed_for_long_random_key6() {
        _validateSeedPhrase(
            "9f6a2878b2520799a44ef18bc7df394e7061a224d2c33cd015b157d746869863",
            "panda eyebrow bullet gorilla call smoke muffin taste mesh discover soft ostrich alcohol speed nation flash devote level hobby quick inner drive ghost inside"
        );
    }

    @Test
    public void should_create_seed_for_short_random_key7() {
        _validateSeedPhrase(
            "23db8160a31d3e0dca3688ed941adbf3",
            "cat swing flag economy stadium alone churn speed unique patch report train"
        );
    }

    @Test
    public void should_create_seed_for_medium_random_key8() {
        _validateSeedPhrase(
            "8197a4a47f0425faeaa69deebc05ca29c0a5b5cc76ceacc0",
            "light rule cinnamon wrap drastic word pride squirrel upgrade then income fatal apart sustain crack supply proud access"
        );
    }

    @Test
    public void should_create_seed_for_long_random_key9() {
        _validateSeedPhrase(
            "066dca1a2bb7e8a1db2832148ce9933eea0f3ac9548d793112d9a95c9407efad",
            "all hour make first leader extend hole alien behind guard gospel lava path output census museum junior mass reopen famous sing advance salt reform"
        );
    }

    @Test
    public void should_create_seed_for_short_random_key10() {
        _validateSeedPhrase(
            "f30f8c1da665478f49b001d94c5fc452",
            "vessel ladder alter error federal sibling chat ability sun glass valve picture"
        );
    }

    @Test
    public void should_create_seed_for_medium_random_key11() {
        _validateSeedPhrase(
            "c10ec20dc3cd9f652c7fac2f1230f7a3c828389a14392f05",
            "scissors invite lock maple supreme raw rapid void congress muscle digital elegant little brisk hair mango congress clump"
        );
    }

    @Test
    public void should_create_seed_for_long_random_key12() {
        _validateSeedPhrase(
            "f585c11aec520db57dd353c69554b21a89b20fb0650966fa0a9d6f74fd989d8f",
            "void come effort suffer camp survey warrior heavy shoot primary clutch crush open amazing screen patrol group space point ten exist slush involve unfold"
        );
    }

    @Test
    public void should_create_seed_for_long_random_key12_case_insensitive() {
        _validateSeedPhrase(
                "f585c11aec520db57dd353c69554b21a89b20fb0650966fa0a9d6f74fd989d8f",
                "Void come effort suffer camp survey warrior Heavy shoOt primary clutch crush opEn amazing screen patrol group space point ten exist slush involve unfold"
        );
    }

    @Test
    public void should_return_validation_errors_for_invalid_seed_words() {
        // Action
        final List<String> errors = _seedPhraseGenerator.validateSeedPhrase("abcd efgh ijkl mnop");

        // Assert
        Assert.assertEquals(4, errors.getCount());
        Assert.assertTrue(errors.get(0).contains("abcd"));
    }

    @Test
    public void should_return_validation_error_for_invalid_checksum() {
        // Action
        final List<String> errors = _seedPhraseGenerator.validateSeedPhrase("scissors invite lock maple supreme raw rapid void congress muscle digital elegant little brisk hair mango congress unfold");

        // Assert
        Assert.assertEquals(1, errors.getCount());
        Assert.assertTrue(errors.get(0).contains("checksum"));
    }
}
