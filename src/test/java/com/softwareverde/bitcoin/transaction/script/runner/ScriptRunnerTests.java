package com.softwareverde.bitcoin.transaction.script.runner;

import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.test.fake.FakeUpgradeSchedule;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.ScriptInflater;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.MutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.unlocking.MutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class ScriptRunnerTests {
    @Test
    public void should_execute_checksig_transaction() {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final TransactionDeflater transactionDeflater = new TransactionDeflater();

        final Transaction transactionBeingSpent = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("01000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D0134FFFFFFFF0100F2052A0100000043410411DB93E1DCDB8A016B49840F8C53BC1EB68A382E97B1482ECAD7B148A6909A5CB2E0EADDFB84CCF9744464F82E160BFA9B8B64F9D4C03F999B8643F656B412A3AC00000000"));

        final Transaction transaction0 = transactionInflater.fromBytes(HexUtil.hexStringToByteArray(
            // Transaction 1
            "01000000" +    // Transaction Version
            "01" +          // Transaction-Input Count
            // Transaction 1, Transaction-Input 1
            "0000000000000000000000000000000000000000000000000000000000000000" + // Previous Transaction Hash
            "FFFFFFFF" +    // Previous Transaction Output Index
            "07" +          // Script Byte Count
            "04FFFF001D0102" + // Locking Script
            "FFFFFFFF" +    // Sequence Number
            "01" +          // Transaction-Output Count
            "00F2052A01000000" + // Amount
            "43" +          // Script Byte Count
            "4104D46C4968BDE02899D2AA0963367C7A6CE34EEC332B32E42E5F3407E052D64AC625DA6F0718E7B302140434BD725706957C092DB53805B821A85B23A7AC61725BAC" + // Script
            "00000000"      // Locktime
        ));

        final String transactionBytesString01 =
            "01000000" +    // Transaction Version
            "01" +          // Transaction Input Count
            "C997A5E56E104102FA209C6A852DD90660A20B2D9C352423EDCE25857FCD3704" + // Previous Transaction Hash
            "00000000" +    // Previous Transaction Output Index
            "48" +          // Script Byte Count
            "47304402204E45E16932B8AF514961A1D3A1A25FDF3F4F7732E9D624C6C61548AB5FB8CD410220181522EC8ECA07DE4860A4ACDD12909D831CC56CBBAC4622082221A8768D1D0901" + // Script
            "FFFFFFFF" +    // Sequence Number
            "02" +          // Transaction-Output Count
            "00CA9A3B00000000" + // Amount
            "43" +          // Script Byte Count
            "4104AE1A62FE09C5F51B13905F07F06B99A2F7159B2225F374CD378D71302FA28414E7AAB37397F554A7DF5F142C21C1B7303B8A0626F1BADED5C72A704F7E6CD84CAC" +
            "00286BEE00000000" + // Amount
            "43" +          // Script Byte Count
            "410411DB93E1DCDB8A016B49840F8C53BC1EB68A382E97B1482ECAD7B148A6909A5CB2E0EADDFB84CCF9744464F82E160BFA9B8B64F9D4C03F999B8643F656B412A3AC" + // Script
            "00000000"      // Locktime
        ;
        final Transaction transaction1 = transactionInflater.fromBytes(HexUtil.hexStringToByteArray(transactionBytesString01));

        Assert.assertEquals(transactionBytesString01, HexUtil.toHexString(transactionDeflater.toBytes(transaction1).getBytes()));

        final UpgradeSchedule upgradeSchedule = new FakeUpgradeSchedule(new CoreUpgradeSchedule());
        final MutableTransactionContext context = new MutableTransactionContext(upgradeSchedule);
        final ScriptRunner scriptRunner = new ScriptRunner(upgradeSchedule);

        final TransactionInput transactionInput = transaction1.getTransactionInputs().get(0);
        final TransactionOutput transactionOutput = transactionBeingSpent.getTransactionOutputs().get(0);

        context.setTransaction(transaction1);
        context.setTransactionInputIndex(0);
        context.setTransactionInput(transactionInput);
        context.setTransactionOutputBeingSpent(transactionOutput);
        context.setBlockHeight(0L);

        final LockingScript lockingScript = transactionOutput.getLockingScript();
        final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context).isValid;

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_execute_checksig_transaction01() {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        final UpgradeSchedule upgradeSchedule = new FakeUpgradeSchedule(new CoreUpgradeSchedule());
        final ScriptRunner scriptRunner = new ScriptRunner(upgradeSchedule);

        final Transaction transactionBeingSpent = transactionInflater.fromBytes(HexUtil.hexStringToByteArray(
            "01000000015AEFC06AF14A9216350A1F549971E0C8381D69B00B492CA20663CAEB5F191825010000006B4830450220210947BCC472D558BED1A36A573BC3C5E11914BE685E868639A46B330AE1879B022100964512E526759EE915A3178F43520CF53D2C38E18A229062EEAB8E2D544A91990121021B36AF5FEDC577DFBF74D75060B20305F1D9127A3C7A7373EF91BF684F6A0491FFFFFFFF0246FBBB84000000001976A914F6A9D96485D1D45D28E38662F617BA39A6B151BB88AC00093D00000000001976A914D948D7A14685B7B5B528034137AA4C590F84F62988AC00000000"
        ));

        final String transactionHexString = "0100000001BF9705FAE2004CC9072D7C6D73BC8F38A0A7C67DACEED5FC42E0D20AC8D898C0000000006B483045022100CB0093D91F09644065AC05424DE3DE709C90A9BC963945EE149EAA1CF7B13DA802200EFE508E68A5E2F9C3CBD851B66EB597803ACCDC2F45F07BFD5488DA476727FE0121039500311F6688A8C16A570853AC22230F4B1E0A551D8846550FE4AE56F9799E80FFFFFFFF0200E1F505000000001976A914C23E891A29D290DDB454EBF3456EEAEC56412AB988AC36F3C57E000000001976A914DB89750F929FBD94A8018767A49EF6FC6AC7E46888AC00000000";
        final Transaction transaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray(transactionHexString));

        Assert.assertEquals(transactionHexString, HexUtil.toHexString(transactionDeflater.toBytes(transaction).getBytes()));

        final MutableTransactionContext context = new MutableTransactionContext(upgradeSchedule);
        context.setTransaction(transaction);

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (int inputIndex=0; inputIndex<transactionInputs.getCount(); ++inputIndex) {
            final TransactionInput transactionInput = transactionInputs.get(inputIndex);
            final TransactionOutput transactionOutputBeingSpent = transactionBeingSpent.getTransactionOutputs().get(0);

            context.setTransactionInputIndex(inputIndex);
            context.setTransactionInput(transactionInput);
            context.setTransactionOutputBeingSpent(transactionOutputBeingSpent);
            context.setBlockHeight(0L);

            final LockingScript lockingScript = transactionOutputBeingSpent.getLockingScript();
            final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();

            // Action
            final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context).isValid;

            // Assert
            Assert.assertTrue(inputIsUnlocked);
        }
    }

    @Test
    public void should_allow_segwit_recovery_after_20190515HF() {
        // Setup
        final UpgradeSchedule upgradeSchedule = new FakeUpgradeSchedule(new CoreUpgradeSchedule());
        final ScriptRunner scriptRunner = new ScriptRunner(upgradeSchedule);

        final MutableTransactionContext context = new MutableTransactionContext(upgradeSchedule);
        context.setBlockHeight(590000L);

        final String[] lockingScriptStrings = new String[7];
        final String[] unlockingScriptStrings = new String[7];

        // Recovering v0 P2SH-P2WPKH:
        lockingScriptStrings[0] = "A91417743BEB429C55C942D2EC703B98C4D57C2DF5C687";
        unlockingScriptStrings[0] = "16001491B24BF9F5288532960AC687ABB035127B1D28A5";

        // Recovering v0 P2SH-P2WSH:
        lockingScriptStrings[1] = "A91417A6BE2F8FE8E94F033E53D17BEEFDA0F3AC440987";
        unlockingScriptStrings[1] = "2200205A0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";

        // Max allowed version, v16:
        lockingScriptStrings[2] = "A9149B0C7017004D3818B7C833DDB3CB5547A22034D087";
        unlockingScriptStrings[2] = "2260205A0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";

        // Max allowed length, 42 bytes:
        lockingScriptStrings[3] = "A914DF7B93F88E83471B479FB219AE90E5B633D6B75087";
        unlockingScriptStrings[3] = "2A00285A0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F2021222324252627";

        // Min allowed length, 4 bytes:
        lockingScriptStrings[4] = "A91486123D8E050333A605E434ECF73128D83815B36F87";
        unlockingScriptStrings[4] = "0400025A01";

        // Valid in spite of a false boolean value being left on stack, 0:
        lockingScriptStrings[5] = "A9140E01BCFE7C6F3FD2FD8F8109229936974468473387";
        unlockingScriptStrings[5] = "0400020000";

        // Valid in spite of a false boolean value being left on stack, minus 0:
        lockingScriptStrings[6] = "A91410DDC638CB26615F867DAD80EFACCED9E73766BC87";
        unlockingScriptStrings[6] = "0400020080";

        for (int i = 0; i < lockingScriptStrings.length; ++i) {
            final String lockingScriptString = lockingScriptStrings[i];
            final String unlockingScriptString = unlockingScriptStrings[i];

            final LockingScript lockingScript = new MutableLockingScript(ByteArray.fromHexString(lockingScriptString));
            final UnlockingScript unlockingScript = new MutableUnlockingScript(ByteArray.fromHexString(unlockingScriptString));

            // Action
            final Boolean outputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context).isValid;

            // Assert
            Assert.assertTrue(outputIsUnlocked);
        }
    }

    @Test
    public void should_not_allow_invalid_segwit_recovery_after_20190515HF() {
        // Setup
        final UpgradeSchedule upgradeSchedule = new FakeUpgradeSchedule(new CoreUpgradeSchedule());
        final ScriptRunner scriptRunner = new ScriptRunner(upgradeSchedule);

        final MutableTransactionContext context = new MutableTransactionContext(upgradeSchedule);
        context.setBlockHeight(590000L);

        final String[] lockingScriptStrings = new String[11];
        final String[] unlockingScriptStrings = new String[11];

        // Non-P2SH output:
        lockingScriptStrings[0] = "81";
        unlockingScriptStrings[0] = "16001491B24BF9F5288532960AC687ABB035127B1D28A5";

        // Redeem script hash does not match P2SH output:
        lockingScriptStrings[1] = "A91417A6BE2F8FE8E94F033E53D17BEEFDA0F3AC440987";
        unlockingScriptStrings[1] = "16001491B24BF9F5288532960AC687ABB035127B1D28A5";

        // scriptSig pushes two items onto the stack:
        lockingScriptStrings[2] = "A91417743BEB429C55C942D2EC703B98C4D57C2DF5C687";
        unlockingScriptStrings[2] = "0016001491B24BF9F5288532960AC687ABB035127B1D28A5";

        // Invalid witness program, non-minimal push in version field:
        lockingScriptStrings[3] = "A9140718743E67C1EF4911E0421F206C5FF81755718E87";
        unlockingScriptStrings[3] = "1701001491B24BF9F5288532960AC687ABB035127B1D28A5";

        // Invalid witness program, non-minimal push in program field:
        lockingScriptStrings[4] = "A914D3EC673296C7FD7E1A9E53BFC36F414DE303E90587";
        unlockingScriptStrings[4] = "05004C0245AA";

        // Invalid witness program, too short, 3 bytes:
        lockingScriptStrings[5] = "A91440B6941895022D458DE8F4BBFE27F3AAA4FB9A7487";
        unlockingScriptStrings[5] = "0300015A";

        // Invalid witness program, too long, 43 bytes:
        lockingScriptStrings[6] = "A91413AA4FCFD630508E0794DCA320CAC172C5790AEA87";
        unlockingScriptStrings[6] = "2B00295A0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728";

        // Invalid witness program, version -1:
        lockingScriptStrings[7] = "A91497AA1E96E49CA6D744D7344F649DD9F94BCC35EB87";
        unlockingScriptStrings[7] = "224F205A0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";

        // Invalid witness program, version 17:
        lockingScriptStrings[8] = "A9144B5321BEB1C09F593FF3C02BE4AF21C7F949E10187";
        unlockingScriptStrings[8] = "230111205A0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";

        // Invalid witness program, OP_RESERVED in version field:
        lockingScriptStrings[9] = "A914BE02794CEEDE051DA41B420E88A86FFF2802AF0687";
        unlockingScriptStrings[9] = "2250205A0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";

        // Invalid witness program, more than 2 stack items:
        lockingScriptStrings[10] = "A9148EB812176C9E71732584123DD06D3246E659B19987";
        unlockingScriptStrings[10] = "2300205A0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F51";

        for (int i = 0; i < lockingScriptStrings.length; ++i) {
            final String lockingScriptString = lockingScriptStrings[i];
            final String unlockingScriptString = unlockingScriptStrings[i];

            final LockingScript lockingScript = new MutableLockingScript(ByteArray.fromHexString(lockingScriptString));
            final UnlockingScript unlockingScript = new MutableUnlockingScript(ByteArray.fromHexString(unlockingScriptString));

            // Action
            final Boolean outputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context).isValid;

            // Assert
            Assert.assertFalse(outputIsUnlocked);
        }
    }

    @Test
    public void should_validate_large_value_for_decode_number() {
        // Setup
        //  "0x10 0x0102030405060708090A0B0C0D0E0F10 DUP CAT DUP CAT DUP CAT DUP CAT DUP CAT 0x08 0x0102030405060708 CAT 520"
        //  "NUM2BIN 0x10 0x0102030405060708090A0B0C0D0E0F10 DUP CAT DUP CAT DUP CAT DUP CAT DUP CAT 0x08 0x0102030405060708 CAT EQUAL"

        final ScriptInflater scriptInflater = new ScriptInflater();
        final UnlockingScript unlockingScript = UnlockingScript.castFrom(scriptInflater.fromBytes(ByteArray.fromHexString("100102030405060708090A0B0C0D0E0F10767E767E767E767E767E0801020304050607087E020802")));
        final LockingScript lockingScript = LockingScript.castFrom(scriptInflater.fromBytes(ByteArray.fromHexString("80100102030405060708090A0B0C0D0E0F10767E767E767E767E767E0801020304050607087E87")));

        final UpgradeSchedule upgradeSchedule = new FakeUpgradeSchedule(new CoreUpgradeSchedule());
        final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();
        final MutableTransactionContext context = new MutableTransactionContext(upgradeSchedule);
        context.setBlockHeight(0L);
        context.setMedianBlockTime(medianBlockTime);
        final ScriptRunner scriptRunner = new ScriptRunner(upgradeSchedule);

        // Action
        final Boolean isValid = scriptRunner.runScript(lockingScript, unlockingScript, context).isValid;

        // Assert
        Assert.assertTrue(isValid);
    }
}
