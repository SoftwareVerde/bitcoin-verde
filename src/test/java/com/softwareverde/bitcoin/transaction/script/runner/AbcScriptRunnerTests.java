package com.softwareverde.bitcoin.transaction.script.runner;

import com.softwareverde.bitcoin.chain.time.ImmutableMedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptInflater;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.CryptographicOperation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.signature.ScriptSignature;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.script.unlocking.MutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.IoUtil;
import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.json.Json;
import com.softwareverde.util.BitcoinReflectionUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import org.junit.Assert;
import org.junit.Test;

public class AbcScriptRunnerTests {
    public static class FakeMedianBlockTime implements MedianBlockTime {
        protected Long _medianBlockTime = MedianBlockTime.GENESIS_BLOCK_TIMESTAMP;

        @Override
        public ImmutableMedianBlockTime asConst() {
            return new ImmutableMedianBlockTime(_medianBlockTime);
        }

        @Override
        public Long getCurrentTimeInSeconds() {
            return _medianBlockTime;
        }

        @Override
        public Long getCurrentTimeInMilliSeconds() {
            return (_medianBlockTime * 1000L);
        }

        public void setMedianBlockTime(final Long medianBlockTime) {
            _medianBlockTime = medianBlockTime;
        }
    }

    protected ByteArray _wrapByte(final byte value) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(value);
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }

    protected ByteArray _inflateOperation(final String opCodeString) {
        if (opCodeString.startsWith("0x")) {
            return ByteArray.fromHexString(opCodeString.substring(2));
        }

        if (opCodeString.matches("\'.*\'")) {
            final String content = opCodeString.substring(1, (opCodeString.length() - 1));
            final ByteArray contentBytes = MutableByteArray.wrap(StringUtil.stringToBytes(content));
            final Integer contentByteCount = contentBytes.getByteCount();

            if (contentByteCount <= PushOperation.VALUE_MAX_BYTE_COUNT) {
                return MutableByteArray.wrap(PushOperation.pushBytes(contentBytes).getBytes());
            }

            final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
            byteArrayBuilder.appendByte((byte) 0x4E); // PUSH_DATA_INTEGER
            byteArrayBuilder.appendBytes(ByteUtil.reverseEndian(ByteUtil.integerToBytes(contentByteCount)));
            byteArrayBuilder.appendBytes(contentBytes);;
            return MutableByteArray.wrap(byteArrayBuilder.build());
        }

        switch (opCodeString) {
            case "PUSHDATA1":   { return _wrapByte((byte) 0x4C); }
            case "PUSHDATA2":   { return _wrapByte((byte) 0x4D); }
            case "PUSHDATA4":   { return _wrapByte((byte) 0x4E); }
            case "1NEGATE":     { return _wrapByte((byte) 0x4F); }
            case "FALSE": // Fallthrough...
            case "0":           { return _wrapByte((byte) 0x00); }
            case "TRUE":  // Fallthrough...
            case "1":           { return _wrapByte((byte) 0x51); }
            case "2":           { return _wrapByte((byte) 0x52); }
            case "3":           { return _wrapByte((byte) 0x53); }
            case "4":           { return _wrapByte((byte) 0x54); }
            case "5":           { return _wrapByte((byte) 0x55); }
            case "6":           { return _wrapByte((byte) 0x56); }
            case "7":           { return _wrapByte((byte) 0x57); }
            case "8":           { return _wrapByte((byte) 0x58); }
            case "9":           { return _wrapByte((byte) 0x59); }
            case "10":          { return _wrapByte((byte) 0x5A); }
            case "11":          { return _wrapByte((byte) 0x5B); }
            case "12":          { return _wrapByte((byte) 0x5C); }
            case "13":          { return _wrapByte((byte) 0x5D); }
            case "14":          { return _wrapByte((byte) 0x5E); }
            case "15":          { return _wrapByte((byte) 0x5F); }
            case "16":          { return _wrapByte((byte) 0x60); }

            case "INVERT":      { return _wrapByte((byte) 0x83); }
            case "AND":         { return _wrapByte((byte) 0x84); }
            case "OR":          { return _wrapByte((byte) 0x85); }
            case "XOR":         { return _wrapByte((byte) 0x86); }
            case "EQUAL":       { return _wrapByte((byte) 0x87); }
            case "EQUALVERIFY": { return _wrapByte((byte) 0x88); }

            case "NOP":         { return _wrapByte((byte) 0x61); }
            case "IF":          { return _wrapByte((byte) 0x63); }
            case "NOTIF":       { return _wrapByte((byte) 0x64); }
            case "ELSE":        { return _wrapByte((byte) 0x67); }
            case "ENDIF":       { return _wrapByte((byte) 0x68); }
            case "VERIFY":      { return _wrapByte((byte) 0x69); }
            case "RETURN":      { return _wrapByte((byte) 0x6A); }

            case "TOALTSTACK":          { return _wrapByte((byte) 0x6B); }
            case "FROMALTSTACK":        { return _wrapByte((byte) 0x6C); }
            case "IFDUP":               { return _wrapByte((byte) 0x73); }
            case "DEPTH":               { return _wrapByte((byte) 0x74); }
            case "DROP":                { return _wrapByte((byte) 0x75); }
            case "DUP":                 { return _wrapByte((byte) 0x76); }
            case "NIP":                 { return _wrapByte((byte) 0x77); }
            case "OVER":                { return _wrapByte((byte) 0x78); }
            case "PICK":                { return _wrapByte((byte) 0x79); }
            case "ROLL":                { return _wrapByte((byte) 0x7A); }
            case "ROT":                 { return _wrapByte((byte) 0x7B); }
            case "SWAP":                { return _wrapByte((byte) 0x7C); }
            case "TUCK":                { return _wrapByte((byte) 0x7D); }
            case "2DROP":               { return _wrapByte((byte) 0x6D); }
            case "2DUP":                { return _wrapByte((byte) 0x6E); }
            case "3DUP":                { return _wrapByte((byte) 0x6F); }
            case "2OVER":               { return _wrapByte((byte) 0x70); }
            case "2ROT":                { return _wrapByte((byte) 0x71); }
            case "2SWAP":               { return _wrapByte((byte) 0x72); }

            case "SPLIT":               { return _wrapByte((byte) 0x7F); }

            case "CAT":                 { return _wrapByte((byte) 0x7E); }
            case "SUBSTR":              { return _wrapByte((byte) 0x7F); }
            case "LEFT":                { return _wrapByte((byte) 0x80); }
            case "RIGHT":               { return _wrapByte((byte) 0x81); }
            case "SIZE":                { return _wrapByte((byte) 0x82); }

            case "NUM2BIN":             { return _wrapByte((byte) 0x80); }
            case "BIN2NUM":             { return _wrapByte((byte) 0x81); }

            case "RIPEMD160":           { return _wrapByte((byte) 0xA6); }
            case "SHA1":                { return _wrapByte((byte) 0xA7); }
            case "SHA256":              { return _wrapByte((byte) 0xA8); }
            case "HASH160":             { return _wrapByte((byte) 0xA9); }
            case "HASH256":             { return _wrapByte((byte) 0xAA); }
            case "CODESEPARATOR":       { return _wrapByte((byte) 0xAB); }
            case "CHECKSIG":            { return _wrapByte((byte) 0xAC); }
            case "CHECKSIGVERIFY":      { return _wrapByte((byte) 0xAD); }
            case "CHECKMULTISIG":       { return _wrapByte((byte) 0xAE); }
            case "CHECKMULTISIGVERIFY": { return _wrapByte((byte) 0xAF); }

            case "CHECKLOCKTIMEVERIFY": { return _wrapByte((byte) 0xB1); }
            case "CHECKSEQUENCEVERIFY": { return _wrapByte((byte) 0xB2); }

            case "CHECKDATASIG":        { return _wrapByte((byte) 0xBA); }
            case "CHECKDATASIGVERIFY":  { return _wrapByte((byte) 0xBB); }

            // ^([^\t]*)\t[0-9]*\t(0x[0-9a-fA-F]*).*$
            case "1ADD":                { return _wrapByte((byte) 0x8B); }
            case "1SUB":                { return _wrapByte((byte) 0x8C); }
            case "2MUL":                { return _wrapByte((byte) 0x8D); }
            case "2DIV":                { return _wrapByte((byte) 0x8E); }
            case "NEGATE":              { return _wrapByte((byte) 0x8F); }
            case "ABS":                 { return _wrapByte((byte) 0x90); }
            case "NOT":                 { return _wrapByte((byte) 0x91); }
            case "0NOTEQUAL":           { return _wrapByte((byte) 0x92); }
            case "ADD":                 { return _wrapByte((byte) 0x93); }
            case "SUB":                 { return _wrapByte((byte) 0x94); }
            case "MUL":                 { return _wrapByte((byte) 0x95); }
            case "DIV":                 { return _wrapByte((byte) 0x96); }
            case "MOD":                 { return _wrapByte((byte) 0x97); }
            case "LSHIFT":              { return _wrapByte((byte) 0x98); }
            case "RSHIFT":              { return _wrapByte((byte) 0x99); }
            case "BOOLAND":             { return _wrapByte((byte) 0x9A); }
            case "BOOLOR":              { return _wrapByte((byte) 0x9B); }
            case "NUMEQUAL":            { return _wrapByte((byte) 0x9C); }
            case "NUMEQUALVERIFY":      { return _wrapByte((byte) 0x9D); }
            case "NUMNOTEQUAL":         { return _wrapByte((byte) 0x9E); }
            case "LESSTHAN":            { return _wrapByte((byte) 0x9F); }
            case "GREATERTHAN":         { return _wrapByte((byte) 0xA0); }
            case "LESSTHANOREQUAL":     { return _wrapByte((byte) 0xA1); }
            case "GREATERTHANOREQUAL":  { return _wrapByte((byte) 0xA2); }
            case "MIN":                 { return _wrapByte((byte) 0xA3); }
            case "MAX":                 { return _wrapByte((byte) 0xA4); }
            case "WITHIN":              { return _wrapByte((byte) 0xA5); }

            case "RESERVED":    { return _wrapByte((byte) 0x50); }
            case "VER":         { return _wrapByte((byte) 0x62); }
            case "VERIF":       { return _wrapByte((byte) 0x65); }
            case "VERNOTIF":    { return _wrapByte((byte) 0x66); }
            case "RESERVED1":   { return _wrapByte((byte) 0x89); }
            case "RESERVED2":   { return _wrapByte((byte) 0x8A); }
            case "NOP1":        { return _wrapByte((byte) 0xB0); }
            case "NOP4":        { return _wrapByte((byte) 0xB3); }
            case "NOP5":        { return _wrapByte((byte) 0xB4); }
            case "NOP6":        { return _wrapByte((byte) 0xB5); }
            case "NOP7":        { return _wrapByte((byte) 0xB6); }
            case "NOP8":        { return _wrapByte((byte) 0xB7); }
            case "NOP9":        { return _wrapByte((byte) 0xB8); }
            case "NOP10":       { return _wrapByte((byte) 0xB9); }

        }

        if (opCodeString.matches("[-]?[0-9]+")) {
            final long longValue = Util.parseLong(opCodeString);
            final boolean isNegative = (longValue < 0);
            final long absoluteValue = Math.abs(longValue);

            final byte[] longBytes = ByteUtil.longToBytes(absoluteValue);
            if (isNegative) {
                longBytes[0] |= ((byte) 0x80);
            }

            final Value value = Value.minimallyEncodeBytes(MutableByteArray.wrap(ByteUtil.reverseEndian(longBytes)));
            return MutableByteArray.wrap(PushOperation.pushValue(value).getBytes());
        }

        throw new RuntimeException("Unknown Opcode: " + opCodeString);
    }

    protected Script _inflateScript(final String scriptString) {
        final String cleanedScriptString = scriptString.trim().replaceAll("[ ]+", " ");
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        if (! cleanedScriptString.isEmpty()) {
            for (final String opCodeString : cleanedScriptString.split(" ")) {
                final ByteArray operationByte = _inflateOperation(opCodeString);
                byteArrayBuilder.appendBytes(operationByte);
            }
        }

        final ScriptInflater scriptInflater = new ScriptInflater();
        return scriptInflater.fromBytes(byteArrayBuilder.build());
    }

    @Test
    public void run() {
        final Json testVectors = Json.parse(IoUtil.getResource("/abc_test_vectors.json"));

        final MutableTransaction transactionBeingSpent = new MutableTransaction();
        transactionBeingSpent.setVersion(Transaction.VERSION);
        transactionBeingSpent.setLockTime(LockTime.MIN_TIMESTAMP);
        { // TransactionInput...
            final MutableTransactionInput transactionInput = new MutableTransactionInput();
            transactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
            transactionInput.setPreviousOutputIndex(0);
            transactionInput.setPreviousOutputTransactionHash(Sha256Hash.EMPTY_HASH);
            { // Unlocking Script...
                final MutableUnlockingScript mutableUnlockingScript = new MutableUnlockingScript();
                mutableUnlockingScript.addOperation(PushOperation.PUSH_ZERO);
                mutableUnlockingScript.addOperation(PushOperation.PUSH_ZERO);
                transactionInput.setUnlockingScript(mutableUnlockingScript);
            }
            transactionBeingSpent.addTransactionInput(transactionInput);
        }
        final MutableTransactionOutput transactionOutputBeingSpent = new MutableTransactionOutput();
        { // TransactionOutput...
            transactionOutputBeingSpent.setIndex(0);
            transactionOutputBeingSpent.setAmount(0L);
            transactionBeingSpent.addTransactionOutput(transactionOutputBeingSpent);
        }

        final MutableTransaction transaction = new MutableTransaction();
        transaction.setVersion(Transaction.VERSION);
        transaction.setLockTime(LockTime.MIN_TIMESTAMP);
        final MutableTransactionInput transactionInput = new MutableTransactionInput();
        {
            transactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
            transactionInput.setPreviousOutputIndex(0);
            transactionInput.setPreviousOutputTransactionHash(Sha256Hash.EMPTY_HASH);
            transaction.addTransactionInput(transactionInput);
        }
        {
            final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();
            transactionOutput.setLockingScript(ByteArray.fromHexString("51"));
            transactionOutput.setAmount(0L);
            transactionOutput.setIndex(0);
            transaction.addTransactionOutput(transactionOutput);
        }

        // Format is: [[wit..., amount]?, scriptSig, scriptPubKey, flags, expected_scripterror, ... comments]

        for (int i = 0; i < testVectors.length(); ++i) {
            final Json testVector = testVectors.get(i);
            if (testVector.length() < 2) { continue; }

            int j = 0;
            if ( (! testVector.getString(i).isEmpty()) && (testVector.get(j).isArray()) ) {
                System.out.println("witness, amount: " + testVector.get(j));
                j += 1;
            }

            final String unlockingScriptString = testVector.getString(j);
            j += 1;

            final String lockingScriptString = testVector.getString(j);
            j += 1;

            final String flagsString = testVector.getString(j);
            j += 1;

            final String expectedResultString = testVector.getString(j);
            j += 1;

            final String comments = testVector.getString(j);
            j += 1;

            boolean skipTest = false;
            {
                final String[] skippedTestFlags = new String[] { "DISCOURAGE_UPGRADABLE_NOPS" };
                for (final String skippedFlag : skippedTestFlags) {
                    if (flagsString.contains(skippedFlag)) {
                        skipTest = true;
                        break;
                    }
                }

                final String[] skippedResultTypes = new String[] { "OP_COUNT", "MINIMALDATA" };
                for (final String resultType : skippedResultTypes) {
                    if (expectedResultString.contains(resultType)) {
                        skipTest = true;
                        break;
                    }
                }

                if (expectedResultString.contains("UNKNOWN_ERROR") && flagsString.contains("MINIMALDATA")) {
                    skipTest = true;
                }

                final int[] skippedTestIndices = new int[] {
                    // 1189, 1190 // Attempts to execute CHECKDATASIG without enabling the opcode...
                    // 1191, 1192, 1193, 1196, 1197, // The test harness has no viable way to turn off NULLFAIL while enabling CHECKDATASIG...
                };
                for (final int skippedTestIndex : skippedTestIndices) {
                    if (i == skippedTestIndex) {
                        skipTest = true;
                        break;
                    }
                }
            }
            if (skipTest) {
                System.out.println("SKIPPING: " + testVector);
                continue;
            }

            final UnlockingScript unlockingScript;
            final LockingScript lockingScript;
            try {
                unlockingScript = UnlockingScript.castFrom(_inflateScript(unlockingScriptString));
                lockingScript = LockingScript.castFrom(_inflateScript(lockingScriptString));
            }
            finally {
                System.out.println(testVector);
            }

            final FakeMedianBlockTime medianBlockTime = new FakeMedianBlockTime();
            final ScriptRunner scriptRunner = new ScriptRunner(medianBlockTime);

            transactionOutputBeingSpent.setLockingScript(lockingScript);
            transactionBeingSpent.setTransactionOutput(0, transactionOutputBeingSpent);

            transactionInput.setUnlockingScript(unlockingScript);
            transactionInput.setPreviousOutputTransactionHash(transactionBeingSpent.getHash());
            transaction.setTransactionInput(0, transactionInput);

            final MutableContext context = new MutableContext();
            context.setTransaction(transaction); // Set the LockTime to zero...
            context.setTransactionOutputBeingSpent(transactionOutputBeingSpent);
            context.setTransactionInput(transactionInput);
            context.setTransactionInputIndex(0);

            context.setBlockHeight(0L);
            if (flagsString.contains("P2SH")) {
                context.setBlockHeight(Math.max(173805L, context.getBlockHeight()));
            }
            if ( (i > 1000) && (flagsString.contains("STRICTENC")) ) {
                context.setBlockHeight(Math.max(478559L, context.getBlockHeight()));
            }
            if (flagsString.contains("SCHNORR")) {
                medianBlockTime.setMedianBlockTime(1557921600L);
            }
            if ( (i >= 1189) && (lockingScriptString.contains("CHECKDATASIG")) ) {
                context.setBlockHeight(Math.max(556767L, context.getBlockHeight()));
            }

            BitcoinReflectionUtil.setStaticValue(ScriptSignature.class, "SCHNORR_IS_ENABLED", flagsString.contains("SCHNORR"));
            BitcoinReflectionUtil.setStaticValue(CryptographicOperation.class, "FAIL_ON_BAD_SIGNATURE_ENABLED", flagsString.contains("NULLFAIL"));

            final boolean wasValid = scriptRunner.runScript(lockingScript, unlockingScript, context);

            final boolean expectedResult = Util.areEqual("OK", expectedResultString);
            if (! Util.areEqual(expectedResult, wasValid)) {
                System.out.println(i);
                System.out.println(testVector);
            }

            Assert.assertEquals(expectedResult, wasValid);
        }
    }
}
