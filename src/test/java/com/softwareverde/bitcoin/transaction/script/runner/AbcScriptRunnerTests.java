package com.softwareverde.bitcoin.transaction.script.runner;

import com.softwareverde.bitcoin.chain.time.ImmutableMedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.jni.NativeSecp256k1;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptInflater;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.script.unlocking.MutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.IoUtil;
import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.json.Json;
import com.softwareverde.util.BitcoinReflectionUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
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

    public static class TestVector {
        public static TestVector fromJson(final Json testVectorJson) {
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
            return Sha256Hash.copyOf(BitcoinUtil.sha256((amount + unlockingScriptString + lockingScriptString + flagsString + expectedResultString).getBytes()));
        }

        @Override
        public String toString() {
            return _rawJsonString;
        }
    }

    protected static final Json TEST_VECTOR_MANIFEST = Json.parse(IoUtil.getResource("/abc_test_vector_manifest.json"));

    public static Boolean isTestVectorEnabled(final TestVector testVector) {
        final Sha256Hash testVectorHash = testVector.getHash();
        final String testVectorHashString = testVectorHash.toString();

        if (! TEST_VECTOR_MANIFEST.hasKey(testVectorHashString)) { return null; }
        final Json testVectorManifestEntry = TEST_VECTOR_MANIFEST.get(testVectorHashString);
        return testVectorManifestEntry.getBoolean("isEnabled");
    }

    public static String getTestVectorComments(final TestVector testVector, final int testIndex) {
        final Sha256Hash testVectorHash = testVector.getHash();
        final String testVectorHashString = testVectorHash.toString();

        switch (testIndex) {
            case 1203: return "Requires CHECKDATASIG with STRICTENC disabled.";
            case 1317: return "Attempts to require that no forkId is set if ForkId is enabled, but ForkId is always enabled in practice.";

            case 1324: return "Test is specific to the ABC feature-flag mechanisms.  Bitcoin Verde does not implement the same feature-flags mechanism as ABC, and therefore STRICTENC cannot be disabled with CHECKDATASIG.  In practice, STRICTENC is always enabled when CHECKDATASIG is enabled.  Example tests include attempting to allow invalid signature (High S), or bad DER signature, in combination with CHECKDATASIG and now LOW_S/STRICTENC/etc flags.";

            case 1326: case 1328: case 1330: case 1336: case 1338: case 1340:
            case 1354: case 1356: case 1358: case 1360: case 1366: case 1368: case 1370:
                return "Test is specific to the ABC feature-flag mechanisms.";

            case 1532: case 1533: case 1534: case 1536: case 1538: return "Test is a schnorr signatures with STRICTENC, but also does not use Bitcoin Cash HashType.  Attempts to mix DERSIG/NULLFAIL/STRICTENC/NULLDUMMY flags in unsupported combinations.";
            case 1541: case 1543: return "Test is a schnorr signatures with STRICTENC, but also does not use Bitcoin Cash HashType.  Cannot disable FORKID without STRICTENC.";
        };

        if (! TEST_VECTOR_MANIFEST.hasKey(testVectorHashString)) { return null; }
        final Json testVectorManifestEntry = TEST_VECTOR_MANIFEST.get(testVectorHashString);
        return testVectorManifestEntry.getString("comments");
    }

    public static Boolean shouldSkipTestVector(final TestVector testVector, final int testIndex) {
        final String[] skippedTestFlags = new String[] { "DISCOURAGE_UPGRADABLE_NOPS", "REPLAY_PROTECTION" };
        for (final String skippedFlag : skippedTestFlags) {
            if (testVector.flagsString.contains(skippedFlag)) {
                return true;
            }
        }

        // SIG_NULLDUMMY is applied to the mempool only.  Verde does not currently intend on supporting this.
        final String[] skippedResultTypes = new String[] { "OP_COUNT", "MINIMALDATA", "SIG_NULLDUMMY" };
        for (final String resultType : skippedResultTypes) {
            if (testVector.expectedResultString.contains(resultType)) {
                return true;
            }
        }

        if (testVector.expectedResultString.contains("UNKNOWN_ERROR") && testVector.flagsString.contains("MINIMALDATA")) {
            return true;
        }

        if (testVector.expectedResultString.contains("NONCOMPRESSED_PUBKEY")) {
            return true;
        }

        if (testVector.flagsString.contains("DISALLOW_SEGWIT_RECOVERY") && (! testVector.expectedResultString.contains("OK"))) {
            return true;
        }

        if (testVector.flagsString.contains("MINIMALIF") && testVector.expectedResultString.contains("MINIMALIF")) {
            return true;
        }

        final int[] skippedTestIndices = new int[] {
                1203, // Requires CHECKDATASIG with STRICTENC disabled...
                1317, // Attempts to require that no forkId is set if ForkId is enabled, but ForkId is always enabled in practice.

                // Tests are specific to the ABC feature-flag mechanisms.
                // Bitcoin Verde does not implement the same feature-flags mechanism as ABC, and therefore STRICTENC cannot be disabled with CHECKDATASIG.
                // In practice, STRICTENC is always enabled when CHECKDATASIG is enabled.
                // Example tests include attempting to allow invalid signature (High S), or bad DER signature, in combination with CHECKDATASIG and now LOW_S/STRICTENC/etc flags.
                1324, 1326, 1328, 1330, 1336, 1338, 1340,
                1354, 1356, 1358, 1360, 1366, 1368, 1370,

                // Tests are schnorr signatures with STRICTENC, but also does not use Bitcoin Cash HashType...
                1532, 1533, 1534, 1536, 1538, // Attempts to mix DERSIG/NULLFAIL/STRICTENC/NULLDUMMY flags in unsupported combinations...
                1541, 1543, // Cannot disable FORKID without STRICTENC...
        };
        for (final int skippedTestIndex : skippedTestIndices) {
            if (testIndex == skippedTestIndex) {
                return true;
            }
        }

        return false; // AbcScriptRunnerTests.isTestVectorEnabled(testVector);
    }

    public static void rebuiltTestVectorManifest() {
        final Json manifestJson = new Json();

        boolean hasUnknownTestVectors = false;
        final Json testVectors = Json.parse(IoUtil.getResource("/abc_test_vectors.json"));
        for (int i = 0; i < testVectors.length(); ++i) {
            final Json testVectorJson = testVectors.get(i);
            final TestVector testVector = TestVector.fromJson(testVectorJson);
            if (testVector == null) { continue; }

            final Boolean shouldSkipTestVector = AbcScriptRunnerTests.shouldSkipTestVector(testVector, i);
            if (shouldSkipTestVector == null) {
                System.out.println("Unknown Test Vector: " + testVector);
                hasUnknownTestVectors = true;
            }
            else {
                final String testVectorComments = AbcScriptRunnerTests.getTestVectorComments(testVector, i);

                final Json testVectorManifestEntry = new Json(false);
                testVectorManifestEntry.put("isEnabled", (! shouldSkipTestVector));
                testVectorManifestEntry.put("comments", testVectorComments);

                final Sha256Hash testVectorHash = testVector.getHash();
                final String testVectorHashString = testVectorHash.toString();
                manifestJson.put(testVectorHashString, testVectorManifestEntry);
            }
        }

        Assert.assertFalse(hasUnknownTestVectors);

        System.out.println(manifestJson.toFormattedString(2));
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

    @Before
    public void setup() {
        if (AbcScriptRunnerTests.originalNativeSecp256k1Value == null) {
            AbcScriptRunnerTests.originalNativeSecp256k1Value = NativeSecp256k1.isEnabled();
        }

        BitcoinReflectionUtil.setVolatile(BitcoinConstants.class, "SCHNORR_IS_ENABLED", true);
        BitcoinReflectionUtil.setVolatile(BitcoinConstants.class, "FAIL_ON_BAD_SIGNATURE", true);
        BitcoinReflectionUtil.setVolatile(BitcoinConstants.class, "REQUIRE_BITCOIN_CASH_FORK_ID", true);
        BitcoinReflectionUtil.setVolatile(NativeSecp256k1.class, "_libraryLoadedCorrectly", true);

        BitcoinReflectionUtil.setStaticValue(BitcoinConstants.class, "SCHNORR_IS_ENABLED", true);
        BitcoinReflectionUtil.setStaticValue(BitcoinConstants.class, "FAIL_ON_BAD_SIGNATURE", true);
        BitcoinReflectionUtil.setStaticValue(BitcoinConstants.class, "REQUIRE_BITCOIN_CASH_FORK_ID", true);
        BitcoinReflectionUtil.setStaticValue(NativeSecp256k1.class, "_libraryLoadedCorrectly", false);
    }

    @After
    public void teardown() {
        BitcoinReflectionUtil.setStaticValue(BitcoinConstants.class, "SCHNORR_IS_ENABLED", true);
        BitcoinReflectionUtil.setStaticValue(BitcoinConstants.class, "FAIL_ON_BAD_SIGNATURE", true);
        BitcoinReflectionUtil.setStaticValue(BitcoinConstants.class, "REQUIRE_BITCOIN_CASH_FORK_ID", true);

        BitcoinReflectionUtil.setStaticValue(NativeSecp256k1.class, "_libraryLoadedCorrectly", AbcScriptRunnerTests.originalNativeSecp256k1Value);
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
            transactionInput.setPreviousOutputIndex(-1);
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

        // Format is: [[wit..., amount]?, scriptSig, scriptPubKey, flags, expected_scripterror, ... comments]

        int executedCount = 0;
        int skippedCount = 0;
        int failCount = 0;
        for (int i = 0; i < testVectors.length(); ++i) {
            final Json testVectorJson = testVectors.get(i);
            final TestVector testVector = TestVector.fromJson(testVectorJson);
            if (testVector == null) { continue; }

            final Boolean skipTest = AbcScriptRunnerTests.shouldSkipTestVector(testVector, i);
            if (skipTest == null) {
                System.out.println("TestVector is not listed in the manifest.  Consider rerunning AbcScriptRunnerTests.rebuildTestVectorManifest().");
                System.out.println(testVector.getHash() + " -> " + testVector);
                Assert.fail();
            }

            if (skipTest) {
                skippedCount += 1;
                System.out.println(i + ": " + "[SKIPPED] " + testVector);
                continue;
            }

            final UnlockingScript unlockingScript;
            final LockingScript lockingScript;
            try {
                unlockingScript = UnlockingScript.castFrom(AbcScriptRunnerTests.inflateScript(testVector.unlockingScriptString));
                lockingScript = LockingScript.castFrom(AbcScriptRunnerTests.inflateScript(testVector.lockingScriptString));
            }
            finally {
                System.out.println(i + ": " + testVector);
            }

            final FakeMedianBlockTime medianBlockTime = new FakeMedianBlockTime();
            final ScriptRunner scriptRunner = new ScriptRunner();

            transactionOutputBeingSpent.setLockingScript(lockingScript);
            transactionOutputBeingSpent.setAmount(testVector.amount);
            transactionBeingSpent.setTransactionOutput(0, transactionOutputBeingSpent);

            transactionOutput.setAmount(testVector.amount);
            transaction.setTransactionOutput(0, transactionOutput);

            transactionInput.setUnlockingScript(unlockingScript);
            transactionInput.setPreviousOutputTransactionHash(transactionBeingSpent.getHash());
            transaction.setTransactionInput(0, transactionInput);

            final MutableContext context = new MutableContext();
            context.setTransaction(transaction); // Set the LockTime to zero...
            context.setTransactionOutputBeingSpent(transactionOutputBeingSpent);
            context.setTransactionInput(transactionInput);
            context.setTransactionInputIndex(0);

            context.setBlockHeight(0L);
            context.setMedianBlockTime(medianBlockTime);
            if (testVector.flagsString.contains("P2SH")) {
                context.setBlockHeight(Math.max(173805L, context.getBlockHeight()));
            }
            if (testVector.flagsString.contains("CHECKSEQUENCEVERIFY")) {
                context.setBlockHeight(Math.max(419328L, context.getBlockHeight())); // Enable Bip112...
            }
            if ( (i > 1000) && (testVector.flagsString.contains("STRICTENC") || testVector.flagsString.contains("DERSIG") || testVector.flagsString.contains("LOW_S")) ) {
                context.setBlockHeight(Math.max(478559L, context.getBlockHeight())); // Enable BIP55...
                context.setBlockHeight(Math.max(504032L, context.getBlockHeight())); // Enable BCH HF...
            }
            if (testVector.flagsString.contains("NULLFAIL") || testVector.flagsString.contains("SIGHASH_FORKID")) {
                context.setBlockHeight(Math.max(504032L, context.getBlockHeight())); // Enable BCH HF...
            }
            if (testVector.flagsString.contains("SCHNORR")) {
                medianBlockTime.setMedianBlockTime(1557921600L);
            }
            if (testVector.flagsString.contains("SIGPUSHONLY") || testVector.flagsString.contains("CLEANSTACK")) {
                context.setBlockHeight(Math.max(556767L, context.getBlockHeight())); // Enable BCH HF20190505...
            }
            if ( (i >= 1189) && (testVector.lockingScriptString.contains("CHECKDATASIG")) ) {
                context.setBlockHeight(Math.max(556767L, context.getBlockHeight()));
            }

            BitcoinReflectionUtil.setStaticValue(BitcoinConstants.class, "SCHNORR_IS_ENABLED", testVector.flagsString.contains("SCHNORR"));
            BitcoinReflectionUtil.setStaticValue(BitcoinConstants.class, "FAIL_ON_BAD_SIGNATURE", testVector.flagsString.contains("NULLFAIL"));
            BitcoinReflectionUtil.setStaticValue(BitcoinConstants.class, "REQUIRE_BITCOIN_CASH_FORK_ID", testVector.flagsString.contains("SIGHASH_FORKID"));

            final boolean wasValid = scriptRunner.runScript(lockingScript, unlockingScript, context);

            executedCount += 1;

            final boolean expectedResult = Util.areEqual("OK", testVector.expectedResultString);

            if (! Util.areEqual(expectedResult, wasValid)) {
                failCount += 1;
                System.out.println("FAILED: " + i);
                System.out.println("Expected: " + expectedResult + " Actual: " + wasValid);
                // break;
            }
        }

        final int totalCount = (executedCount + skippedCount);
        final int successCount = (executedCount - failCount);
        System.out.println("success=" + successCount + " failed=" + failCount + " skipped=" + skippedCount + " total=" + totalCount);

        Assert.assertEquals(0, failCount);
    }

    @Test
    public void rebuild_test_vector_manifest() {
        AbcScriptRunnerTests.rebuiltTestVectorManifest();
    }
}
