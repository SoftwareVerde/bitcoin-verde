package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.token.CashToken;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.Util;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TransactionOutputInflaterTests extends UnitTest {
    @Before
    public void before() throws Exception {
        super.before();
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    protected static ByteArray wrapPrefixDataInOutput(final ByteArray prefixData) {
        final int amountByteCount = 8;
        final LockingScript lockingScript = LockingScript.EMPTY_SCRIPT;

        final ByteArray scriptLengthBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(prefixData.getByteCount()); // TODO: Rename.

        final MutableByteArray byteArray = new MutableByteArray(amountByteCount + scriptLengthBytes.getByteCount() + prefixData.getByteCount() + lockingScript.getByteCount());

        int index = amountByteCount;

        byteArray.setBytes(index, scriptLengthBytes);
        index += scriptLengthBytes.getByteCount();

        byteArray.setBytes(index, prefixData);
        index += prefixData.getByteCount();

        if (lockingScript.getByteCount() > 0) {
            byteArray.setBytes(index, lockingScript.getBytes());
            index += lockingScript.getByteCount();
        }

        return byteArray;
    }

    @Test
    public void should_inflate_valid_cash_tokens() throws Exception {
        final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();
        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();

        final Json testVectors = Json.parse(IoUtil.getResource("/cash-tokens/token-prefix-valid.json"));
        Assert.assertTrue(testVectors.length() > 0);

        for (int i = 0; i < testVectors.length(); ++i) {
            final Json testVector = testVectors.get(i);
            final ByteArray prefixData = ByteArray.fromHexString(testVector.getString("prefix"));
            final Json cashTokenData = testVector.get("data");

            final ByteArray transactionOutputBytes = TransactionOutputInflaterTests.wrapPrefixDataInOutput(prefixData);
            final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(0, transactionOutputBytes);
            final CashToken cashToken = transactionOutput.getCashToken();
            Assert.assertNotNull(cashToken);

            final ByteArray bytes = transactionOutputDeflater.toBytes(transactionOutput);
            Assert.assertEquals(transactionOutputBytes, bytes);

            Assert.assertEquals(cashTokenData.getLong("amount"), Util.coalesce(cashToken.getTokenAmount()));
            Assert.assertEquals(Sha256Hash.fromHexString(cashTokenData.getString("category")), cashToken.getTokenPrefix());

            final ByteArray expectedCommitment = ByteArray.fromHexString(cashTokenData.get("nft").getString("commitment"));
            if (expectedCommitment.isEmpty()) {
                Assert.assertNull(cashToken.getCommitment());
            }
            else {
                Assert.assertEquals(expectedCommitment, cashToken.getCommitment());
            }

            final String expectedCapabilityString = cashTokenData.get("nft").getString("capability").toUpperCase();
            final CashToken.NftCapability nftCapability = cashToken.getNftCapability();
            if (expectedCapabilityString.isEmpty()) {
                Assert.assertNull(nftCapability);
            }
            else {
                Assert.assertEquals(expectedCapabilityString, nftCapability.name());
            }
        }
    }

    @Test
    public void should_not_inflate_invalid_cash_tokens() throws Exception {
        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();

        final Json testVectors = Json.parse(IoUtil.getResource("/cash-tokens/token-prefix-invalid.json"));
        Assert.assertTrue(testVectors.length() > 0);

        for (int i = 0; i < testVectors.length(); ++i) {
            final Json testVector = testVectors.get(i);
            final ByteArray prefixData = ByteArray.fromHexString(testVector.getString("prefix"));

            System.out.println(i + ": " + testVector.getString("error"));

            final ByteArray transactionOutputBytes = TransactionOutputInflaterTests.wrapPrefixDataInOutput(prefixData);
            final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(0, transactionOutputBytes);
            Assert.assertNull(transactionOutput);
        }
    }
}
