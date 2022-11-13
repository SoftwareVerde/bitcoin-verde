package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.bytearray.ByteArrayReader;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TransactionInflaterTests extends UnitTest {
    @Before
    public void before() throws Exception {
        super.before();
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_cache_correct_byte_count() throws Exception {
        // Setup
        final ByteArrayReader byteArrayReader = new ByteArrayReader(ByteArray.fromHexString("0100000001B7149E391C7695D0E3EF07562119074A153C86B57032279BAB6280F3381304CE000000006A473044022059DCCEB41A3647FD79225E425F787CE2B5D09593A08BF66F6263CF6AB492611C02205D5EABBD5F36748CE87B9D8361E6739FE03E44B9DED8B4134B68921212F8084E412102ABAAD90841057DDB1ED929608B536535B0CD8A18BA0A90DBA66BA7B1C1F7B4EAFEFFFFFF02102700000000000040EFB7149E391C7695D0E3EF07562119074A153C86B57032279BAB6280F3381304CE10FE15CD5B0776A9140A373CAF0AB3C2B46CD05625B8D545C295B93D7A88ACC4C9052A010000001976A914EA873AAAFBDD7A7C74D73EE1174E42F620B0A18C88AC00000000"));
        final Integer expectedByteCount = 264;

        final TransactionInflater transactionInflater = new TransactionInflater();

        // Action
        final Transaction transaction = transactionInflater.fromBytes(byteArrayReader);
        final Transaction constTransaction = transaction.asConst();

        // Assert
        Assert.assertEquals(expectedByteCount, transaction.getByteCount());
        Assert.assertEquals(expectedByteCount, constTransaction.getByteCount());
    }
}
