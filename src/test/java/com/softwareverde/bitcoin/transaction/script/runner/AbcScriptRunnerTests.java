package com.softwareverde.bitcoin.transaction.script.runner;

import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.jni.NativeSecp256k1;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeMedianBlockTime;
import com.softwareverde.bitcoin.test.fake.FakeUpgradeSchedule;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptInflater;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.Opcode;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.script.unlocking.MutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.BitcoinReflectionUtil;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;

public class AbcScriptRunnerTests extends UnitTest {
    public static class TestVector {
        public static TestVector fromJson(final Json testVectorJson) {
            // Format is: [[wit..., amount]?, scriptSig, scriptPubKey, flags, expected_scripterror, ... comments]

            if (testVectorJson.length() < 2) {
                return null;
            }

            final TestVector testVector = new TestVector(testVectorJson.toString());

            testVector.amount = 0L;
            int j = 0;
            if ( (testVectorJson.length() >= 6) && (! testVectorJson.getString(j).trim().isEmpty()) && (testVectorJson.get(j).isArray()) ) {
                testVector.amount = (long) (testVectorJson.get(j).getDouble(0) * Transaction.SATOSHIS_PER_BITCOIN);
                j += 1;
            }

            testVector.unlockingScriptString = testVectorJson.getString(j);
            j += 1;

            testVector.lockingScriptString = testVectorJson.getString(j);
            j += 1;

            testVector.flagsString = testVectorJson.getString(j);
            j += 1;

            testVector.expectedResultString = testVectorJson.getString(j);
            j += 1;

            testVector.comments = testVectorJson.getString(j);
            j += 1;

            return testVector;
        }

        protected final String _rawJsonString;

        public long amount;
        public String unlockingScriptString;
        public String lockingScriptString;
        public String flagsString;
        public String expectedResultString;
        public String comments;

        public TestVector() {
            _rawJsonString = "";
        }

        protected TestVector(final String rawJsonString) {
            _rawJsonString = rawJsonString;
        }

        public Sha256Hash getHash() {
            return Sha256Hash.copyOf(HashUtil.sha256((amount + unlockingScriptString + lockingScriptString + flagsString + expectedResultString).getBytes()));
        }

        @Override
        public String toString() {
            return _rawJsonString;
        }
    }

    protected static final HashMap<Sha256Hash, String> DISABLED_TESTS;
    protected static final HashSet<Sha256Hash> ENABLED_TESTS;
    static {
        DISABLED_TESTS = new HashMap<Sha256Hash, String>();
        ENABLED_TESTS = new HashSet<Sha256Hash>();

        final Json testVectorManifest = Json.parse(IoUtil.getResource("/abc_test_vector_manifest.json"));

        final Json disabledTestVectors = testVectorManifest.get("disabledTestVectors");
        for (final String testVectorHashString : disabledTestVectors.getKeys()) {
            final Sha256Hash testVectorsHash = Sha256Hash.fromHexString(testVectorHashString);
            final String comments = disabledTestVectors.getString(testVectorHashString);
            DISABLED_TESTS.put(testVectorsHash, comments);
        }

        final Json enabledTestVectors = testVectorManifest.get("enabledTestVectors");
        for (int i = 0; i < enabledTestVectors.length(); ++i) {
            final Sha256Hash testVectorsHash = Sha256Hash.fromHexString(enabledTestVectors.getString(i));
            ENABLED_TESTS.add(testVectorsHash);
        }
    }

    public static Boolean isTestVectorEnabled(final TestVector testVector) {
        final Sha256Hash testVectorHash = testVector.getHash();

        if (DISABLED_TESTS.containsKey(testVectorHash)) { return false; }
        if (ENABLED_TESTS.contains(testVectorHash)) { return true; }

        return null;
    }

    public static String getTestVectorComments(final TestVector testVector) {
        final Sha256Hash testVectorHash = testVector.getHash();

        if (DISABLED_TESTS.containsKey(testVectorHash)) {
            return DISABLED_TESTS.get(testVectorHash);
        }

        return "";
    }

    public static Boolean shouldSkipTestVector(final TestVector testVector) {
        final Boolean isTestVectorEnabled = AbcScriptRunnerTests.isTestVectorEnabled(testVector);
        if (isTestVectorEnabled == null) { return null; }

        return (! isTestVectorEnabled);
    }

    public static void rebuiltTestVectorManifest() {
        final HashSet<Sha256Hash> newDisabledTests = new HashSet<Sha256Hash>();
        final HashSet<Sha256Hash> newEnabledTests = new HashSet<Sha256Hash>();

        final Json testVectors = Json.parse(IoUtil.getResource("/abc_test_vectors.json"));
        for (int i = 0; i < testVectors.length(); ++i) {
            final Json testVectorJson = testVectors.get(i);
            final TestVector testVector = TestVector.fromJson(testVectorJson);
            if (testVector == null) { continue; }

            final Sha256Hash testVectorHash = testVector.getHash();
            if (DISABLED_TESTS.containsKey(testVectorHash)) { continue; }
            if (ENABLED_TESTS.contains(testVectorHash)) { continue; }

            final Boolean shouldSkipTestVector = AbcScriptRunnerTests.shouldSkipTestVector(testVector);
            if (Util.coalesce(shouldSkipTestVector, false)) {
                newDisabledTests.add(testVectorHash);
            }
            else {
                newEnabledTests.add(testVectorHash);
            }
        }

        final Json manifestJson = new Json(false);
        final Json disabledTestVectors = new Json(false);
        final Json enabledTestVectors = new Json(true);

        for (final Sha256Hash testVectorHash : DISABLED_TESTS.keySet()) {
            final String comments = DISABLED_TESTS.get(testVectorHash);
            disabledTestVectors.put(testVectorHash.toString(), comments);
        }

        for (final Sha256Hash testVectorHash : ENABLED_TESTS) {
            enabledTestVectors.add(testVectorHash.toString());
        }

        for (final Sha256Hash testVectorHash : newEnabledTests) {
            enabledTestVectors.add(testVectorHash.toString());
        }

        for (final Sha256Hash testVectorHash : newDisabledTests) {
            enabledTestVectors.add(testVectorHash.toString());
        }

        manifestJson.put("enabledTestVectors", enabledTestVectors);
        manifestJson.put("disabledTestVectors", disabledTestVectors);

        System.out.println(manifestJson.toFormattedString(2));

        System.out.println();
        System.out.println("New Disabled Tests: ");
        for (final Sha256Hash testVectorHash : newDisabledTests) {
            System.out.println(testVectorHash);
        }

        System.out.println();
        System.out.println("New Enabled Tests: ");
        for (final Sha256Hash testVectorHash : newEnabledTests) {
            System.out.println(testVectorHash);
        }
    }

    protected static ByteArray wrapByte(final byte value) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(value);
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }

    protected static ByteArray inflateOperation(final String opCodeString) {
        if (opCodeString.startsWith("0x")) {
            return ByteArray.fromHexString(opCodeString.substring(2));
        }

        if (opCodeString.matches("\'.*\'")) {
            final String content = opCodeString.substring(1, (opCodeString.length() - 1));
            final ByteArray contentBytes = MutableByteArray.wrap(StringUtil.stringToBytes(content));
            final int contentByteCount = contentBytes.getByteCount();

            if (contentByteCount <= PushOperation.VALUE_MAX_BYTE_COUNT) {
                return MutableByteArray.wrap(PushOperation.pushBytes(contentBytes).getBytes());
            }

            final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
            if (contentByteCount < (1 << 16)) {
                byteArrayBuilder.appendByte(Opcode.PUSH_DATA_SHORT.getValue());
                byteArrayBuilder.appendBytes(ByteUtil.reverseEndian(ByteUtil.getTailBytes(ByteUtil.integerToBytes(contentByteCount), 2)));
                byteArrayBuilder.appendBytes(contentBytes);
                return MutableByteArray.wrap(byteArrayBuilder.build());
            }
            else {
                byteArrayBuilder.appendByte(Opcode.PUSH_DATA_INTEGER.getValue());
                byteArrayBuilder.appendBytes(ByteUtil.reverseEndian(ByteUtil.integerToBytes(contentByteCount)));
                byteArrayBuilder.appendBytes(contentBytes);
            }
            return MutableByteArray.wrap(byteArrayBuilder.build());
        }

        switch (opCodeString) {
            case "PUSHDATA1":   { return AbcScriptRunnerTests.wrapByte((byte) 0x4C); }
            case "PUSHDATA2":   { return AbcScriptRunnerTests.wrapByte((byte) 0x4D); }
            case "PUSHDATA4":   { return AbcScriptRunnerTests.wrapByte((byte) 0x4E); }
            case "1NEGATE":     { return AbcScriptRunnerTests.wrapByte((byte) 0x4F); }
            case "FALSE": // Fallthrough...
            case "0":           { return AbcScriptRunnerTests.wrapByte((byte) 0x00); }
            case "TRUE":  // Fallthrough...
            case "1":           { return AbcScriptRunnerTests.wrapByte((byte) 0x51); }
            case "2":           { return AbcScriptRunnerTests.wrapByte((byte) 0x52); }
            case "3":           { return AbcScriptRunnerTests.wrapByte((byte) 0x53); }
            case "4":           { return AbcScriptRunnerTests.wrapByte((byte) 0x54); }
            case "5":           { return AbcScriptRunnerTests.wrapByte((byte) 0x55); }
            case "6":           { return AbcScriptRunnerTests.wrapByte((byte) 0x56); }
            case "7":           { return AbcScriptRunnerTests.wrapByte((byte) 0x57); }
            case "8":           { return AbcScriptRunnerTests.wrapByte((byte) 0x58); }
            case "9":           { return AbcScriptRunnerTests.wrapByte((byte) 0x59); }
            case "10":          { return AbcScriptRunnerTests.wrapByte((byte) 0x5A); }
            case "11":          { return AbcScriptRunnerTests.wrapByte((byte) 0x5B); }
            case "12":          { return AbcScriptRunnerTests.wrapByte((byte) 0x5C); }
            case "13":          { return AbcScriptRunnerTests.wrapByte((byte) 0x5D); }
            case "14":          { return AbcScriptRunnerTests.wrapByte((byte) 0x5E); }
            case "15":          { return AbcScriptRunnerTests.wrapByte((byte) 0x5F); }
            case "16":          { return AbcScriptRunnerTests.wrapByte((byte) 0x60); }

            case "INVERT":      { return AbcScriptRunnerTests.wrapByte((byte) 0x83); }
            case "AND":         { return AbcScriptRunnerTests.wrapByte((byte) 0x84); }
            case "OR":          { return AbcScriptRunnerTests.wrapByte((byte) 0x85); }
            case "XOR":         { return AbcScriptRunnerTests.wrapByte((byte) 0x86); }
            case "OP_EQUAL":
            case "EQUAL":       { return AbcScriptRunnerTests.wrapByte((byte) 0x87); }
            case "EQUALVERIFY": { return AbcScriptRunnerTests.wrapByte((byte) 0x88); }

            case "NOP":         { return AbcScriptRunnerTests.wrapByte((byte) 0x61); }
            case "IF":          { return AbcScriptRunnerTests.wrapByte((byte) 0x63); }
            case "NOTIF":       { return AbcScriptRunnerTests.wrapByte((byte) 0x64); }
            case "ELSE":        { return AbcScriptRunnerTests.wrapByte((byte) 0x67); }
            case "ENDIF":       { return AbcScriptRunnerTests.wrapByte((byte) 0x68); }
            case "VERIFY":      { return AbcScriptRunnerTests.wrapByte((byte) 0x69); }
            case "RETURN":      { return AbcScriptRunnerTests.wrapByte((byte) 0x6A); }

            case "TOALTSTACK":          { return AbcScriptRunnerTests.wrapByte((byte) 0x6B); }
            case "FROMALTSTACK":        { return AbcScriptRunnerTests.wrapByte((byte) 0x6C); }
            case "IFDUP":               { return AbcScriptRunnerTests.wrapByte((byte) 0x73); }
            case "DEPTH":               { return AbcScriptRunnerTests.wrapByte((byte) 0x74); }
            case "DROP":                { return AbcScriptRunnerTests.wrapByte((byte) 0x75); }
            case "DUP":                 { return AbcScriptRunnerTests.wrapByte((byte) 0x76); }
            case "NIP":                 { return AbcScriptRunnerTests.wrapByte((byte) 0x77); }
            case "OVER":                { return AbcScriptRunnerTests.wrapByte((byte) 0x78); }
            case "PICK":                { return AbcScriptRunnerTests.wrapByte((byte) 0x79); }
            case "ROLL":                { return AbcScriptRunnerTests.wrapByte((byte) 0x7A); }
            case "ROT":                 { return AbcScriptRunnerTests.wrapByte((byte) 0x7B); }
            case "SWAP":                { return AbcScriptRunnerTests.wrapByte((byte) 0x7C); }
            case "TUCK":                { return AbcScriptRunnerTests.wrapByte((byte) 0x7D); }
            case "2DROP":               { return AbcScriptRunnerTests.wrapByte((byte) 0x6D); }
            case "2DUP":                { return AbcScriptRunnerTests.wrapByte((byte) 0x6E); }
            case "3DUP":                { return AbcScriptRunnerTests.wrapByte((byte) 0x6F); }
            case "2OVER":               { return AbcScriptRunnerTests.wrapByte((byte) 0x70); }
            case "2ROT":                { return AbcScriptRunnerTests.wrapByte((byte) 0x71); }
            case "2SWAP":               { return AbcScriptRunnerTests.wrapByte((byte) 0x72); }

            case "SPLIT":               { return AbcScriptRunnerTests.wrapByte((byte) 0x7F); }

            case "CAT":                 { return AbcScriptRunnerTests.wrapByte((byte) 0x7E); }
            case "SUBSTR":              { return AbcScriptRunnerTests.wrapByte((byte) 0x7F); }
            case "LEFT":                { return AbcScriptRunnerTests.wrapByte((byte) 0x80); }
            case "RIGHT":               { return AbcScriptRunnerTests.wrapByte((byte) 0x81); }
            case "SIZE":                { return AbcScriptRunnerTests.wrapByte((byte) 0x82); }

            case "NUM2BIN":             { return AbcScriptRunnerTests.wrapByte((byte) 0x80); }
            case "BIN2NUM":             { return AbcScriptRunnerTests.wrapByte((byte) 0x81); }

            case "RIPEMD160":           { return AbcScriptRunnerTests.wrapByte((byte) 0xA6); }
            case "SHA1":                { return AbcScriptRunnerTests.wrapByte((byte) 0xA7); }
            case "SHA256":              { return AbcScriptRunnerTests.wrapByte((byte) 0xA8); }
            case "HASH160":             { return AbcScriptRunnerTests.wrapByte((byte) 0xA9); }
            case "HASH256":             { return AbcScriptRunnerTests.wrapByte((byte) 0xAA); }
            case "CODESEPARATOR":       { return AbcScriptRunnerTests.wrapByte((byte) 0xAB); }
            case "CHECKSIG":            { return AbcScriptRunnerTests.wrapByte((byte) 0xAC); }
            case "CHECKSIGVERIFY":      { return AbcScriptRunnerTests.wrapByte((byte) 0xAD); }
            case "CHECKMULTISIG":       { return AbcScriptRunnerTests.wrapByte((byte) 0xAE); }
            case "CHECKMULTISIGVERIFY": { return AbcScriptRunnerTests.wrapByte((byte) 0xAF); }

            case "CHECKLOCKTIMEVERIFY": { return AbcScriptRunnerTests.wrapByte((byte) 0xB1); }
            case "CHECKSEQUENCEVERIFY": { return AbcScriptRunnerTests.wrapByte((byte) 0xB2); }

            case "CHECKDATASIG":        { return AbcScriptRunnerTests.wrapByte((byte) 0xBA); }
            case "CHECKDATASIGVERIFY":  { return AbcScriptRunnerTests.wrapByte((byte) 0xBB); }

            // ^([^\t]*)\t[0-9]*\t(0x[0-9a-fA-F]*).*$
            case "1ADD":                { return AbcScriptRunnerTests.wrapByte((byte) 0x8B); }
            case "1SUB":                { return AbcScriptRunnerTests.wrapByte((byte) 0x8C); }
            case "2MUL":                { return AbcScriptRunnerTests.wrapByte((byte) 0x8D); }
            case "2DIV":                { return AbcScriptRunnerTests.wrapByte((byte) 0x8E); }
            case "NEGATE":              { return AbcScriptRunnerTests.wrapByte((byte) 0x8F); }
            case "ABS":                 { return AbcScriptRunnerTests.wrapByte((byte) 0x90); }
            case "NOT":                 { return AbcScriptRunnerTests.wrapByte((byte) 0x91); }
            case "0NOTEQUAL":           { return AbcScriptRunnerTests.wrapByte((byte) 0x92); }
            case "ADD":                 { return AbcScriptRunnerTests.wrapByte((byte) 0x93); }
            case "SUB":                 { return AbcScriptRunnerTests.wrapByte((byte) 0x94); }
            case "MUL":                 { return AbcScriptRunnerTests.wrapByte((byte) 0x95); }
            case "DIV":                 { return AbcScriptRunnerTests.wrapByte((byte) 0x96); }
            case "MOD":                 { return AbcScriptRunnerTests.wrapByte((byte) 0x97); }
            case "LSHIFT":              { return AbcScriptRunnerTests.wrapByte((byte) 0x98); }
            case "RSHIFT":              { return AbcScriptRunnerTests.wrapByte((byte) 0x99); }
            case "BOOLAND":             { return AbcScriptRunnerTests.wrapByte((byte) 0x9A); }
            case "BOOLOR":              { return AbcScriptRunnerTests.wrapByte((byte) 0x9B); }
            case "NUMEQUAL":            { return AbcScriptRunnerTests.wrapByte((byte) 0x9C); }
            case "NUMEQUALVERIFY":      { return AbcScriptRunnerTests.wrapByte((byte) 0x9D); }
            case "NUMNOTEQUAL":         { return AbcScriptRunnerTests.wrapByte((byte) 0x9E); }
            case "LESSTHAN":            { return AbcScriptRunnerTests.wrapByte((byte) 0x9F); }
            case "GREATERTHAN":         { return AbcScriptRunnerTests.wrapByte((byte) 0xA0); }
            case "LESSTHANOREQUAL":     { return AbcScriptRunnerTests.wrapByte((byte) 0xA1); }
            case "GREATERTHANOREQUAL":  { return AbcScriptRunnerTests.wrapByte((byte) 0xA2); }
            case "MIN":                 { return AbcScriptRunnerTests.wrapByte((byte) 0xA3); }
            case "MAX":                 { return AbcScriptRunnerTests.wrapByte((byte) 0xA4); }
            case "WITHIN":              { return AbcScriptRunnerTests.wrapByte((byte) 0xA5); }

            case "RESERVED":    { return AbcScriptRunnerTests.wrapByte((byte) 0x50); }
            case "VER":         { return AbcScriptRunnerTests.wrapByte((byte) 0x62); }
            case "VERIF":       { return AbcScriptRunnerTests.wrapByte((byte) 0x65); }
            case "VERNOTIF":    { return AbcScriptRunnerTests.wrapByte((byte) 0x66); }
            case "RESERVED1":   { return AbcScriptRunnerTests.wrapByte((byte) 0x89); }
            case "RESERVED2":   { return AbcScriptRunnerTests.wrapByte((byte) 0x8A); }
            case "NOP1":        { return AbcScriptRunnerTests.wrapByte((byte) 0xB0); }
            case "NOP4":        { return AbcScriptRunnerTests.wrapByte((byte) 0xB3); }
            case "NOP5":        { return AbcScriptRunnerTests.wrapByte((byte) 0xB4); }
            case "NOP6":        { return AbcScriptRunnerTests.wrapByte((byte) 0xB5); }
            case "NOP7":        { return AbcScriptRunnerTests.wrapByte((byte) 0xB6); }
            case "NOP8":        { return AbcScriptRunnerTests.wrapByte((byte) 0xB7); }
            case "NOP9":        { return AbcScriptRunnerTests.wrapByte((byte) 0xB8); }
            case "NOP10":       { return AbcScriptRunnerTests.wrapByte((byte) 0xB9); }

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

    protected static Script inflateScript(final String scriptString) {
        final String cleanedScriptString = scriptString.trim().replaceAll("[ ]+", " ");
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        if (! cleanedScriptString.isEmpty()) {
            for (final String opCodeString : cleanedScriptString.split(" ")) {
                final ByteArray operationByte = AbcScriptRunnerTests.inflateOperation(opCodeString);
                byteArrayBuilder.appendBytes(operationByte);
            }
        }

        final ScriptInflater scriptInflater = new ScriptInflater();
        return scriptInflater.fromBytes(byteArrayBuilder.build());
    }

    protected static Boolean originalNativeSecp256k1Value = null;

    @Override @Before
    public void before() throws Exception {
        super.before();

        if (AbcScriptRunnerTests.originalNativeSecp256k1Value == null) {
            AbcScriptRunnerTests.originalNativeSecp256k1Value = NativeSecp256k1.isEnabled();
        }

        try {
            BitcoinReflectionUtil.setVolatile(NativeSecp256k1.class, "_libraryLoadedCorrectly", true);
        }
        catch (final Exception exception) {
            // Can happen as of Java 11, but ReflectionUtil also removes the need for
            //  setting volatile due to its usage of sun.misc.Unsafe when using Java 11...
            Logger.debug("Unable to set volatile modifier.", exception);
        }

        BitcoinReflectionUtil.setStaticValue(NativeSecp256k1.class, "_libraryLoadedCorrectly", false);
    }

    @After
    public void after() throws Exception {
        BitcoinReflectionUtil.setStaticValue(NativeSecp256k1.class, "_libraryLoadedCorrectly", AbcScriptRunnerTests.originalNativeSecp256k1Value);

        super.after();
    }

    @Test
    public void run() {
        // This test runs the majority of ABC's test vectors.
        // Unfortunately, the test suite relies heavily on ABC's feature-flag mechanisms, which Bitcoin Verde does not support.
        //  This has been partially worked around with setting various blockHeights to enable/disable features.
        //  However, many features cannot be individually disabled, either by because they are included in the same BIP/HF, or
        //  the features span multiple fork activations (that is, if forks are activated as A, B, then C, enabling C automatically enables A and B).
        //  A small subset of tests are disabled because the feature will never be supported (e.g. "MINIMALIF", which is a segwit leftover).
        // Finally, a few feature flags have been "hacked in" via static final Booleans, and then toggled via reflection.
        //  This reflection mutation can cause inconsistent test results, since the "final" modifier has certain effects within the Java memory model.
        //  If these tests fail, consider re-running this suite multiple times.  If the tests still fail, locate the static final Boolean flags used
        //  within the ::teardown function, and temporarily remove their "final" modifier.  If after that, the tests still fail, then there is likely
        //  an actual bug that should be investigated.  Keep in mind that many feature/flags are incompatible, so a failing test may not actually be
        //  indicative of an error.  Conversely, a passing test may not be actually testing the intent behind the test vector, since components of its
        //  feature-flags may not have been successfully disabled.

        final Json testVectors = Json.parse(IoUtil.getResource("/abc_test_vectors.json"));

        final MutableTransaction transactionBeingSpent = new MutableTransaction();
        transactionBeingSpent.setVersion(1L);
        transactionBeingSpent.setLockTime(LockTime.MIN_TIMESTAMP);
        { // TransactionInput...
            final MutableTransactionInput transactionInput = new MutableTransactionInput();
            transactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
            transactionInput.setPreviousOutputTransactionHash(TransactionOutputIdentifier.COINBASE.getTransactionHash());
            transactionInput.setPreviousOutputIndex(TransactionOutputIdentifier.COINBASE.getOutputIndex());
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
        transaction.setVersion(1L);
        transaction.setLockTime(LockTime.MIN_TIMESTAMP);
        final MutableTransactionInput transactionInput = new MutableTransactionInput();
        {
            transactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
            transactionInput.setPreviousOutputIndex(0);
            transactionInput.setPreviousOutputTransactionHash(Sha256Hash.EMPTY_HASH);
            transaction.addTransactionInput(transactionInput);
        }
        final MutableTransactionOutput transactionOutput;
        {
            transactionOutput = new MutableTransactionOutput();
            transactionOutput.setLockingScript(new MutableByteArray(0));
            transactionOutput.setAmount(0L);
            transactionOutput.setIndex(0);
            transaction.addTransactionOutput(transactionOutput);
        }

        int executedCount = 0;
        int skippedCount = 0;
        int failCount = 0;
        for (int i = 0; i < testVectors.length(); ++i) {
            final Json testVectorJson = testVectors.get(i);
            final TestVector testVector = TestVector.fromJson(testVectorJson);
            if (testVector == null) { continue; }

            final Boolean skipTest = AbcScriptRunnerTests.shouldSkipTestVector(testVector);
            if (skipTest == null) {
                System.out.println("TestVector is not listed in the manifest.  Consider rerunning AbcScriptRunnerTests.rebuildTestVectorManifest().");
                System.out.println(testVector.getHash() + " -> " + testVector);
                Assert.fail();
            }

//            if (skipTest) {
//                skippedCount += 1;
//                System.out.println(i + ": " + "[SKIPPED] " + testVector);
//                continue;
//            }

            final UnlockingScript unlockingScript;
            final LockingScript lockingScript;
            try {
                unlockingScript = UnlockingScript.castFrom(AbcScriptRunnerTests.inflateScript(testVector.unlockingScriptString));
                lockingScript = LockingScript.castFrom(AbcScriptRunnerTests.inflateScript(testVector.lockingScriptString));
            }
            finally {
                // System.out.println(i + ": " + testVector + "(" + testVector.getHash() + ")");
            }

            final FakeUpgradeSchedule upgradeSchedule = new FakeUpgradeSchedule(new CoreUpgradeSchedule());

            final FakeMedianBlockTime medianBlockTime = new FakeMedianBlockTime(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP);
            final ScriptRunner scriptRunner = new ScriptRunner(upgradeSchedule);

            transactionOutputBeingSpent.setLockingScript(lockingScript);
            transactionOutputBeingSpent.setAmount(testVector.amount);
            transactionBeingSpent.setTransactionOutput(0, transactionOutputBeingSpent);

            transactionOutput.setAmount(testVector.amount);
            transaction.setTransactionOutput(0, transactionOutput);

            transactionInput.setUnlockingScript(unlockingScript);
            transactionInput.setPreviousOutputTransactionHash(transactionBeingSpent.getHash());
            transaction.setTransactionInput(0, transactionInput);

            final MutableTransactionContext context = new MutableTransactionContext(upgradeSchedule);
            context.setTransaction(transaction); // Set the LockTime to zero...
            context.setTransactionOutputBeingSpent(transactionOutputBeingSpent);
            context.setTransactionInput(transactionInput);
            context.setTransactionInputIndex(0);

            context.setBlockHeight(1L);
            context.setMedianBlockTime(medianBlockTime);

            if (testVector.flagsString.contains("MINIMALDATA")) {
                upgradeSchedule.setMinimalNumberEncodingRequired(true);
            }
            if (testVector.flagsString.contains("P2SH")) {
                upgradeSchedule.setPayToScriptHashEnabled(true);
            }
            if (testVector.flagsString.contains("CHECKSEQUENCEVERIFY")) {
                upgradeSchedule.setCheckSequenceNumberOperationEnabled(true);
            }
            if (testVector.flagsString.contains("CHECKLOCKTIMEVERIFY")) {
                upgradeSchedule.setCheckLockTimeOperationEnabled(true);
            }
            if (testVector.flagsString.contains("STRICTENC")) {
                upgradeSchedule.setSignaturesRequiredToBeStrictlyEncoded(true);
                upgradeSchedule.setDerSignaturesRequiredToBeStrictlyEncoded(true);
                upgradeSchedule.setPublicKeysRequiredToBeStrictlyEncoded(true);
            }
            if (testVector.flagsString.contains("DERSIG")) {
                upgradeSchedule.setDerSignaturesRequiredToBeStrictlyEncoded(true);
            }
            if (testVector.flagsString.contains("LOW_S")) {
                upgradeSchedule.setCanonicalSignatureEncodingsRequired(true);
            }
            if (testVector.flagsString.contains("NULLFAIL")) {
                upgradeSchedule.setAllInvalidSignaturesRequiredToBeEmpty(true);
            }
            if (testVector.flagsString.contains("SIGHASH_FORKID")) {
                upgradeSchedule.setBitcoinCashSignatureHashTypeEnabled(true);
            }
            if (testVector.flagsString.contains("SIGPUSHONLY")) {
                upgradeSchedule.setOnlyPushOperationsAllowedWithinUnlockingScript(true);
            }
            if (testVector.lockingScriptString.contains("CHECKDATASIG")) {
                upgradeSchedule.setCheckDataSignatureOperationEnabled(true);
            }
            if (testVector.flagsString.contains("SCHNORR_MULTISIG")) {
                upgradeSchedule.setAreSchnorrSignaturesEnabledWithinMultiSignature(true);
            }
            if (testVector.flagsString.contains("CLEANSTACK")) {
                upgradeSchedule.setUnusedValuesAfterScriptExecutionDisallowed(true);
                upgradeSchedule.setUnusedValuesAfterSegwitScriptExecutionAllowed(true);
            }
            if (testVector.flagsString.contains("DISALLOW_SEGWIT_RECOVERY")) {
                upgradeSchedule.setUnusedValuesAfterSegwitScriptExecutionAllowed(false);
            }

            final boolean wasValid = scriptRunner.runScript(lockingScript, unlockingScript, context).isValid;

            executedCount += 1;

            final boolean expectedResult = Util.areEqual("OK", testVector.expectedResultString);

            if (Util.areEqual(expectedResult, wasValid)) {
                if (skipTest) {
                    System.out.println("Consider Enabling Test: " + testVector.getHash());
                }
            }
            else {
                // Retry with production values to assess severity...
                context.setBlockHeight(Long.MAX_VALUE);
                medianBlockTime.setMedianBlockTime(Long.MAX_VALUE);
                final boolean isValidInProduction = scriptRunner.runScript(lockingScript, unlockingScript, context).isValid;
                final boolean isPossiblyImportant = ( (! expectedResult) && isValidInProduction);

                if (! skipTest) {
                    failCount += 1;
                    System.out.println("FAILED" + (isPossiblyImportant ? " [WARN]" : "") + ": " + i + " (" + testVector.getHash() + " - " + testVector.flagsString + " - " + testVector.expectedResultString + " - \"" + testVector.comments + "\")");
                    System.out.println("Expected: " + expectedResult + " Actual: " + wasValid + " (Production: " + isValidInProduction + ")");
                    //break;
                }
            }
        }

        final int totalCount = (executedCount + skippedCount);
        final int successCount = (executedCount - failCount);
        System.out.println("success=" + successCount + " failed=" + failCount + " skipped=" + skippedCount + " total=" + totalCount);

        Assert.assertEquals(0, failCount);
    }

    // @Test
    // public void rebuild_test_vector_manifest() {
    //     AbcScriptRunnerTests.rebuiltTestVectorManifest();
    // }
}
