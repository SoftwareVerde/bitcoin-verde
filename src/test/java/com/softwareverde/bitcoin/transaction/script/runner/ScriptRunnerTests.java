package com.softwareverde.bitcoin.transaction.script.runner;

import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.MutableLockTime;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.ImmutableScript;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.stack.ScriptSignature;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ScriptRunnerTests {
    @Test
    public void should_execute_checksig_transaction() {
        // Setup

        final MutableLockTime mutableLockTime = new MutableLockTime();
        mutableLockTime.setLockTime(LockTime.MIN_TIMESTAMP);


        final TransactionInflater transactionInflater = new TransactionInflater();

        final Transaction transactionBeingSpent = transactionInflater.fromBytes(BitcoinUtil.hexStringToByteArray("01000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D0134FFFFFFFF0100F2052A0100000043410411DB93E1DCDB8A016B49840F8C53BC1EB68A382E97B1482ECAD7B148A6909A5CB2E0EADDFB84CCF9744464F82E160BFA9B8B64F9D4C03F999B8643F656B412A3AC00000000"));

        final Transaction transaction0 = transactionInflater.fromBytes(BitcoinUtil.hexStringToByteArray(
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
        final Transaction transaction1 = transactionInflater.fromBytes(BitcoinUtil.hexStringToByteArray(transactionBytesString01));

        Assert.assertEquals(transactionBytesString01, BitcoinUtil.toHexString(transaction1.getBytes()));

        final Context context = new Context();
        final ScriptRunner scriptRunner = new ScriptRunner();

        final TransactionInput transactionInput = transaction1.getTransactionInputs().get(0);
        final TransactionOutput transactionOutput = transactionBeingSpent.getTransactionOutputs().get(0);

        context.setTransaction(transaction1);
        context.setTransactionInputIndex(0);
        context.setTransactionInput(transactionInput);
        context.setTransactionOutput(transactionOutput);

        final Script lockingScript = transactionOutput.getLockingScript();
        final Script unlockingScript = transactionInput.getUnlockingScript();

        // Action
        final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

        // Assert
        Assert.assertTrue(inputIsUnlocked);
    }

    @Test
    public void should_execute_checksig_transaction01() {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final ScriptRunner scriptRunner = new ScriptRunner();

        final Transaction transactionBeingSpent = transactionInflater.fromBytes(BitcoinUtil.hexStringToByteArray(
            "01000000015AEFC06AF14A9216350A1F549971E0C8381D69B00B492CA20663CAEB5F191825010000006B4830450220210947BCC472D558BED1A36A573BC3C5E11914BE685E868639A46B330AE1879B022100964512E526759EE915A3178F43520CF53D2C38E18A229062EEAB8E2D544A91990121021B36AF5FEDC577DFBF74D75060B20305F1D9127A3C7A7373EF91BF684F6A0491FFFFFFFF0246FBBB84000000001976A914F6A9D96485D1D45D28E38662F617BA39A6B151BB88AC00093D00000000001976A914D948D7A14685B7B5B528034137AA4C590F84F62988AC00000000"
        ));

        final String transactionHexString = "0100000001BF9705FAE2004CC9072D7C6D73BC8F38A0A7C67DACEED5FC42E0D20AC8D898C0000000006B483045022100CB0093D91F09644065AC05424DE3DE709C90A9BC963945EE149EAA1CF7B13DA802200EFE508E68A5E2F9C3CBD851B66EB597803ACCDC2F45F07BFD5488DA476727FE0121039500311F6688A8C16A570853AC22230F4B1E0A551D8846550FE4AE56F9799E80FFFFFFFF0200E1F505000000001976A914C23E891A29D290DDB454EBF3456EEAEC56412AB988AC36F3C57E000000001976A914DB89750F929FBD94A8018767A49EF6FC6AC7E46888AC00000000";
        final Transaction transaction = transactionInflater.fromBytes(BitcoinUtil.hexStringToByteArray(transactionHexString));

        Assert.assertEquals(transactionHexString, BitcoinUtil.toHexString(transaction.getBytes()));

        final Context context = new Context();
        context.setTransaction(transaction);

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (int inputIndex=0; inputIndex<transactionInputs.size(); ++inputIndex) {
            final TransactionInput transactionInput = transactionInputs.get(inputIndex);
            final TransactionOutput transactionOutputBeingSpent = transactionBeingSpent.getTransactionOutputs().get(0);

            context.setTransactionInputIndex(inputIndex);
            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutputBeingSpent);

            final Script lockingScript = transactionOutputBeingSpent.getLockingScript();
            final Script unlockingScript = transactionInput.getUnlockingScript();

            // Action
            final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

            // Assert
            Assert.assertTrue(inputIsUnlocked);
        }
    }

    @Test
    public void should_create_hash_for_signing() {
        // Example taken from: https://bitcoin.stackexchange.com/questions/3374/how-to-redeem-a-basic-tx

        // Setup
        final String expectedHashToSign = "9302BDA273A887CB40C13E02A50B4071A31FD3AAE3AE04021B0B843DD61AD18E";

        final Script outputBeingSpentLockingScript = new ImmutableScript(BitcoinUtil.hexStringToByteArray("76A914010966776006953D5567439E5E39F86A0D273BEE88AC"));
        final Script newOutputLockingScript = new ImmutableScript(BitcoinUtil.hexStringToByteArray("76A914097072524438D003D23A2F23EDB65AAE1BB3E46988AC"));

        final MutableTransactionOutput transactionOutputBeingSpent = new MutableTransactionOutput();
        transactionOutputBeingSpent.setIndex(1);
        transactionOutputBeingSpent.setAmount(100000000L);
        transactionOutputBeingSpent.setLockingScript(outputBeingSpentLockingScript);

        final MutableTransactionInput transactionInput = new MutableTransactionInput();
        transactionInput.setPreviousTransactionOutputHash(new ImmutableHash(BitcoinUtil.hexStringToByteArray("F2B3EB2DEB76566E7324307CD47C35EEB88413F971D88519859B1834307ECFEC")));
        transactionInput.setPreviousTransactionOutputIndex(1);
        transactionInput.setSequenceNumber(TransactionInput.MAX_SEQUENCE_NUMBER);
        transactionInput.setUnlockingScript(Script.EMPTY_SCRIPT);
        final MutableTransaction transaction = new MutableTransaction();
        transaction.setVersion(1);
        transaction.addTransactionInput(transactionInput);
        final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();
        transactionOutput.setLockingScript(newOutputLockingScript);
        transactionOutput.setAmount(99900000L);
        transactionOutput.setIndex(0);
        transaction.addTransactionOutput(transactionOutput);
        transaction.setLockTime(new ImmutableLockTime(LockTime.MIN_TIMESTAMP));

        // Action
        final Hash hashForSigning = transaction.calculateSha256HashForSigning(0, transactionOutputBeingSpent, ScriptSignature.HashType.SIGNATURE_HASH_ALL);

        // Assert
        TestUtil.assertEqual(BitcoinUtil.hexStringToByteArray(expectedHashToSign), hashForSigning.getBytes());
    }

    @Test
    public void should_verify_signed_transaction() {
        // Example taken from: https://bitcoin.stackexchange.com/questions/32628/redeeming-a-raw-transaction-step-by-step-example-required

        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transactionBeingSpent = transactionInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000000175DB462B20DD144DD143F5314270569C0A61191F1378C164CE4262E9BFF1B079000000008B4830450221008F906B9FE728CB17C81DECCD6704F664ED1AC920223BB2ECA918F066269C703302203B1C496FD4C3FA5071262B98447FBCA5E3ED7A52EFE3DA26AA58F738BD342D31014104BCA69C59DC7A6D8EF4D3043BDCB626E9E29837B9BEB143168938AE8165848BFC788D6FF4CDF1EF843E6A9CCDA988B323D12A367DD758261DD27A63F18F56CE77FFFFFFFF0133F50100000000001976A914DD6CCE9F255A8CC17BDA8BA0373DF8E861CB866E88AC00000000"));
        final Transaction transaction = transactionInflater.fromBytes(BitcoinUtil.hexStringToByteArray("0100000001BE66E10DA854E7AEA9338C1F91CD489768D1D6D7189F586D7A3613F2A24D5396000000008B483045022100DA43201760BDA697222002F56266BF65023FEF2094519E13077F777BAED553B102205CE35D05EABDA58CD50A67977A65706347CC25EF43153E309FF210A134722E9E0141042DAA93315EEBBE2CB9B5C3505DF4C6FB6CACA8B756786098567550D4820C09DB988FE9997D049D687292F815CCD6E7FB5C1B1A91137999818D17C73D0F80AEF9FFFFFFFF0123CE0100000000001976A9142BC89C2702E0E618DB7D59EB5CE2F0F147B4075488AC00000000"));

        final ScriptRunner scriptRunner = new ScriptRunner();
        final Context context = new Context();
        context.setTransaction(transaction);

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        for (int inputIndex=0; inputIndex<transactionInputs.size(); ++inputIndex) {
            final TransactionInput transactionInput = transactionInputs.get(inputIndex);
            final TransactionOutput transactionOutputBeingSpent = transactionBeingSpent.getTransactionOutputs().get(inputIndex);

            context.setTransactionInputIndex(inputIndex);
            context.setTransactionInput(transactionInput);
            context.setTransactionOutput(transactionOutputBeingSpent);

            final Script lockingScript = transactionOutputBeingSpent.getLockingScript();
            final Script unlockingScript = transactionInput.getUnlockingScript();

            // Action
            final Boolean inputIsUnlocked = scriptRunner.runScript(lockingScript, unlockingScript, context);

            // Assert
            Assert.assertTrue(inputIsUnlocked);
        }
    }
}
