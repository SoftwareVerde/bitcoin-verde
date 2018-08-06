package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.MutableLockingScript;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class ScriptPatternMatcherTests {
    @Test
    public void should_match_pay_to_public_key_hash_script() {
        // Setup
        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("76A914ADEDB2E16DB029CA2482AC2E0CEFEB887DB37AFF88AC"));
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        // Action
        final Boolean isMatch = scriptPatternMatcher.matchesPayToPublicKeyHashFormat(lockingScript);

        // Assert
        Assert.assertTrue(isMatch);
    }

    @Test
    public void should_not_match_invalid_pay_to_public_key_hash_script() {
        // Setup
        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("76A9000088AC"));
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        // Action
        final Boolean isMatch = scriptPatternMatcher.matchesPayToPublicKeyHashFormat(lockingScript);

        // Assert
        Assert.assertFalse(isMatch);
    }

    @Test
    public void should_match_pay_to_script_hash_script() {
        // Setup
        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("A914E9C3DD0C07AAC76179EBC76A6C78D4D67C6C160A87"));
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        // Action
        final Boolean isMatch = scriptPatternMatcher.matchesPayToScriptHashFormat(lockingScript);

        // Assert
        Assert.assertTrue(isMatch);
    }

    @Test
    public void should_not_match_invalid_pay_to_script_hash_script() {
        // Setup
        final LockingScript lockingScript = new ImmutableLockingScript(HexUtil.hexStringToByteArray("A90087"));
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        // Action
        final Boolean isMatch = scriptPatternMatcher.matchesPayToPublicKeyHashFormat(lockingScript);

        // Assert
        Assert.assertFalse(isMatch);
    }

    @Test
    public void should_match_pay_to_public_key_script() {
        final LockingScript lockingScript = new MutableLockingScript(HexUtil.hexStringToByteArray("41042F462D3245D2F3A015F7F9505F763EE1080CAB36191D07AE9E6509F71BB68818719E6FB41C019BF48AE11C45B024D476E19B6963103CE8647FC15FEE513B15C7AC"));

        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
        Assert.assertEquals(ScriptType.PAY_TO_PUBLIC_KEY, scriptType);

        Assert.assertEquals("1JoiKZz2QRd47ARtcYgvgxC9jhnre9aphv", scriptPatternMatcher.extractAddressFromPayToPublicKey(lockingScript).toBase58CheckEncoded());
        Assert.assertEquals("1JoiKZz2QRd47ARtcYgvgxC9jhnre9aphv", scriptPatternMatcher.extractDecompressedAddressFromPayToPublicKey(lockingScript).toBase58CheckEncoded());
        Assert.assertEquals("19p8dgapw4MktfhcuaPAevLXpBQaY1Xq8J", scriptPatternMatcher.extractCompressedAddressFromPayToPublicKey(lockingScript).toBase58CheckEncoded());
    }

    @Test
    public void should_match_pay_to_compressed_public_key_script() {
        final LockingScript lockingScript = new MutableLockingScript(HexUtil.hexStringToByteArray("21032F462D3245D2F3A015F7F9505F763EE1080CAB36191D07AE9E6509F71BB68818AC"));

        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
        Assert.assertEquals(ScriptType.PAY_TO_PUBLIC_KEY, scriptType);

        Assert.assertEquals("19p8dgapw4MktfhcuaPAevLXpBQaY1Xq8J", scriptPatternMatcher.extractAddressFromPayToPublicKey(lockingScript).toBase58CheckEncoded());
        Assert.assertEquals("1JoiKZz2QRd47ARtcYgvgxC9jhnre9aphv", scriptPatternMatcher.extractDecompressedAddressFromPayToPublicKey(lockingScript).toBase58CheckEncoded());
        Assert.assertEquals("19p8dgapw4MktfhcuaPAevLXpBQaY1Xq8J", scriptPatternMatcher.extractCompressedAddressFromPayToPublicKey(lockingScript).toBase58CheckEncoded());
    }
}
