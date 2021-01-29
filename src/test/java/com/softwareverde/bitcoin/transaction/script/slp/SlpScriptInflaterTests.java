package com.softwareverde.bitcoin.transaction.script.slp;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptInflater;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.Util;
import org.junit.Assert;
import org.junit.Test;

public class SlpScriptInflaterTests extends UnitTest {

    @Test
    public void run_slp_test_vectors() {
        // Setup
        final Json testVectors = Json.parse(IoUtil.getResource("/slp_test_vectors.json"));

        final ScriptInflater scriptInflater = new ScriptInflater();
        final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();

        int failCount = 0;

        // Action
        for (int i = 0; i < testVectors.length(); ++i) {
            final Json testVector = testVectors.get(i);
            final String message = testVector.getString("msg");
            final String scriptHex = testVector.getString("script");
            final Integer code = testVector.getInteger("code");

            final Boolean expectedValidity = message.startsWith("OK");
            final ByteArray script = ByteArray.fromHexString(scriptHex);
            final Script bitcoinScript = scriptInflater.fromBytes(script);
            final LockingScript lockingScript = LockingScript.castFrom(bitcoinScript);

            Exception exception = null;
            Boolean isValid = null;
            try {
                final SlpScript slpScript = slpScriptInflater.fromLockingScript(lockingScript);
                isValid = (slpScript != null);
            }
            catch (final Exception inflationException) {
                exception = inflationException;
            }

            Logger.info("Test: " + i + " " + (Util.areEqual(expectedValidity, isValid) ? "PASSED" : "FAILED") + " Expected: " + expectedValidity + " Result: " + isValid + " Message: " + message);
            if (exception != null) {
                Logger.info(exception);
            }

            if (! Util.areEqual(expectedValidity, isValid)) {
                failCount += 1;
            }
        }

        // Assert
        Assert.assertEquals(0, failCount);
    }
}
