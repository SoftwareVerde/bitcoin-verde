package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.MutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.MutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class ScriptPatternMatcherTests {
    protected static final ByteArray BITCOIN_VERDE_TEST_TOKEN_GENESIS_TX_BYTES = ByteArray.fromHexString("0200000002AF49D1D6A11214D3F36283D39DBAF2D6D597022D9CFEEC2ECDA9C056976A8860010000008A47304402200F78E36957818613EAAC9D2AEC24ED241BE073369342DB459F531363F3D634D6022068142982090BDAD9D7F58B746B0302073DCDC2D0888B7718D16957FAA7B52CB24141048D96DBA2459207CE3E07680AC326A1908763D3C22E1635EFBDEC851DCA52C23CB4AA21A1EE9AC97B53769935A2C33D10006768813C6039F38BBBA0DA3BB10CE9FFFFFFFFAF49D1D6A11214D3F36283D39DBAF2D6D597022D9CFEEC2ECDA9C056976A886000000000694630430220221DEECB0DA419DB079C2EA2A5B3859BCEBD92B41DC073467E22C0B03A914C16021F4EBB42FC2E719185BBDD6D9583A14D492882FD7A3784921DA9C780D5E535F04121029AEABFB7D24D3360C965EC4C0732357657EE4183660BB0BC050C0DE22086F53CFFFFFFFF0400000000000000004F6A04534C500001010747454E455349530342565412426974636F696E20566572646520546573741868747470733A2F2F626974636F696E76657264652E6F72674C000108010208000775F05A07400010270000000000001976A9142297636D6AF0116B6467DCF7C22DC2CAFBC3B3F188AC10270000000000001976A9142297636D6AF0116B6467DCF7C22DC2CAFBC3B3F188AC2A230000000000001976A9145A3DEC87180CC8994907D3B16F1232E8B4EB9E1988AC00000000");

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
        // Assert.assertEquals("19p8dgapw4MktfhcuaPAevLXpBQaY1Xq8J", scriptPatternMatcher.extractAddressFromPayToPublicKey(lockingScript).toBase58CheckEncoded());
    }

    @Test
    public void should_match_pay_to_compressed_public_key_script() {
        final LockingScript lockingScript = new MutableLockingScript(MutableByteArray.wrap(HexUtil.hexStringToByteArray("21032F462D3245D2F3A015F7F9505F763EE1080CAB36191D07AE9E6509F71BB68818AC")));

        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
        Assert.assertEquals(ScriptType.PAY_TO_PUBLIC_KEY, scriptType);

        Assert.assertEquals("19p8dgapw4MktfhcuaPAevLXpBQaY1Xq8J", scriptPatternMatcher.extractAddressFromPayToPublicKey(lockingScript).toBase58CheckEncoded());
        // Assert.assertEquals("1JoiKZz2QRd47ARtcYgvgxC9jhnre9aphv", scriptPatternMatcher.extractDecompressedAddressFromPayToPublicKey(lockingScript).toBase58CheckEncoded());
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

    @Test
    public void should_match_provably_unspendable_script() {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final LockingScript lockingScript;
        { // Bitcoin Verde Test Token Genesis Transaction...
            final Transaction transaction = transactionInflater.fromBytes(BITCOIN_VERDE_TEST_TOKEN_GENESIS_TX_BYTES);
            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            final TransactionOutput transactionOutput = transactionOutputs.get(0);
            lockingScript = transactionOutput.getLockingScript();
        }

        // Action
        final Boolean isUnspendable = scriptPatternMatcher.isProvablyUnspendable(lockingScript);

        // Assert
        Assert.assertTrue(isUnspendable);
    }

    @Test
    public void should_not_match_provably_unspendable_script_with_spendable_output() {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final LockingScript lockingScript;
        { // Bitcoin Verde Test Token Genesis Transaction...
            final Transaction transaction = transactionInflater.fromBytes(BITCOIN_VERDE_TEST_TOKEN_GENESIS_TX_BYTES);
            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            final TransactionOutput transactionOutput = transactionOutputs.get(1);
            lockingScript = transactionOutput.getLockingScript();
        }

        // Action
        final Boolean isUnspendable = scriptPatternMatcher.isProvablyUnspendable(lockingScript);

        // Assert
        Assert.assertFalse(isUnspendable);
    }
}
