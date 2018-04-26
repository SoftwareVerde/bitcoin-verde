package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
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
}
