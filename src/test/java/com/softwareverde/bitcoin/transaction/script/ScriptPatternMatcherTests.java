package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.MutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.MutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class ScriptPatternMatcherTests {
    @Test
    public void should_match_pay_to_public_key_hash_script() {
        // Setup
        final LockingScript lockingScript = new ImmutableLockingScript(MutableByteArray.wrap(HexUtil.hexStringToByteArray("76A914ADEDB2E16DB029CA2482AC2E0CEFEB887DB37AFF88AC")));
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        // Action
        final Boolean isMatch = scriptPatternMatcher.matchesPayToPublicKeyHashFormat(lockingScript);

        // Assert
        Assert.assertTrue(isMatch);
    }

    @Test
    public void should_not_match_invalid_pay_to_public_key_hash_script() {
        // Setup
        final LockingScript lockingScript = new ImmutableLockingScript(MutableByteArray.wrap(HexUtil.hexStringToByteArray("76A9000088AC")));
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        // Action
        final Boolean isMatch = scriptPatternMatcher.matchesPayToPublicKeyHashFormat(lockingScript);

        // Assert
        Assert.assertFalse(isMatch);
    }

    @Test
    public void should_match_pay_to_script_hash_script() {
        // Setup
        final LockingScript lockingScript = new ImmutableLockingScript(MutableByteArray.wrap(HexUtil.hexStringToByteArray("A914E9C3DD0C07AAC76179EBC76A6C78D4D67C6C160A87")));
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        // Action
        final Boolean isMatch = scriptPatternMatcher.matchesPayToScriptHashFormat(lockingScript);

        // Assert
        Assert.assertTrue(isMatch);
    }

    @Test
    public void should_not_match_invalid_pay_to_script_hash_script() {
        // Setup
        final LockingScript lockingScript = new ImmutableLockingScript(MutableByteArray.wrap(HexUtil.hexStringToByteArray("A90087")));
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        // Action
        final Boolean isMatch = scriptPatternMatcher.matchesPayToPublicKeyHashFormat(lockingScript);

        // Assert
        Assert.assertFalse(isMatch);
    }

    @Test
    public void should_match_pay_to_public_key_script() {
        final LockingScript lockingScript = new MutableLockingScript(MutableByteArray.wrap(HexUtil.hexStringToByteArray("41042F462D3245D2F3A015F7F9505F763EE1080CAB36191D07AE9E6509F71BB68818719E6FB41C019BF48AE11C45B024D476E19B6963103CE8647FC15FEE513B15C7AC")));

        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
        Assert.assertEquals(ScriptType.PAY_TO_PUBLIC_KEY, scriptType);

        Assert.assertEquals("1JoiKZz2QRd47ARtcYgvgxC9jhnre9aphv", scriptPatternMatcher.extractAddressFromPayToPublicKey(lockingScript).toBase58CheckEncoded());
        Assert.assertEquals("1JoiKZz2QRd47ARtcYgvgxC9jhnre9aphv", scriptPatternMatcher.extractDecompressedAddressFromPayToPublicKey(lockingScript).toBase58CheckEncoded());
        Assert.assertEquals("19p8dgapw4MktfhcuaPAevLXpBQaY1Xq8J", scriptPatternMatcher.extractCompressedAddressFromPayToPublicKey(lockingScript).toBase58CheckEncoded());
    }

    @Test
    public void should_match_pay_to_compressed_public_key_script() {
        final LockingScript lockingScript = new MutableLockingScript(MutableByteArray.wrap(HexUtil.hexStringToByteArray("21032F462D3245D2F3A015F7F9505F763EE1080CAB36191D07AE9E6509F71BB68818AC")));

        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
        Assert.assertEquals(ScriptType.PAY_TO_PUBLIC_KEY, scriptType);

        Assert.assertEquals("19p8dgapw4MktfhcuaPAevLXpBQaY1Xq8J", scriptPatternMatcher.extractAddressFromPayToPublicKey(lockingScript).toBase58CheckEncoded());
        Assert.assertEquals("1JoiKZz2QRd47ARtcYgvgxC9jhnre9aphv", scriptPatternMatcher.extractDecompressedAddressFromPayToPublicKey(lockingScript).toBase58CheckEncoded());
        Assert.assertEquals("19p8dgapw4MktfhcuaPAevLXpBQaY1Xq8J", scriptPatternMatcher.extractCompressedAddressFromPayToPublicKey(lockingScript).toBase58CheckEncoded());
    }

    @Test
    public void should_match_witness_programs() {
        // Setup
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final String[] unlockingScriptHexStrings = new String[]{
            "16001491B24BF9F5288532960AC687ABB035127B1D28A5",
            "2200205A0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F",
            "2260205A0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F",
            "2A00285A0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F2021222324252627",
            "0400025A01",
            "0400020000",
            "0400020080"
        };

        for (final String unlockingScriptHexString : unlockingScriptHexStrings) {
            final UnlockingScript script = new MutableUnlockingScript(ByteArray.fromHexString(unlockingScriptHexString));

            // Action
            final Boolean isWitnessProgram = scriptPatternMatcher.matchesSegregatedWitnessProgram(script);

            // Assert
            Assert.assertTrue(isWitnessProgram);
        }
    }

    @Test
    public void should_not_match_non_witness_programs() {
        // Setup
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final String[] unlockingScriptHexStrings = new String[]{
            "0016001491B24BF9F5288532960AC687ABB035127B1D28A5",
            "1701001491B24BF9F5288532960AC687ABB035127B1D28A5",
            "05004C0245AA",
            "0300015A",
            "2B00295A0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728",
            "224F205A0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F",
            "230111205A0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F",
            "2250205A0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F",
            "2300205A0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F51"
        };

        for (final String unlockingScriptHexString : unlockingScriptHexStrings) {
            final UnlockingScript script = new MutableUnlockingScript(ByteArray.fromHexString(unlockingScriptHexString));

            // Action
            final Boolean isWitnessProgram = scriptPatternMatcher.matchesSegregatedWitnessProgram(script);

            // Assert
            Assert.assertFalse(isWitnessProgram);
        }
    }
}
