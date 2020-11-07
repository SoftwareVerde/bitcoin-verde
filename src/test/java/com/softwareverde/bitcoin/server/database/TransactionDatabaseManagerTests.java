package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TransactionDatabaseManagerTests extends IntegrationTest {

    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void storing_the_same_transaction_should_return_the_same_id() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final TransactionInflater transactionInflater = new TransactionInflater();
            final Transaction transaction = transactionInflater.fromBytes(ByteArray.fromHexString("01000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D0104FFFFFFFF0100F2052A0100000043410496B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC00000000"));

            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            // Action
            final TransactionId transactionId0 = transactionDatabaseManager.storeTransactionHash(transaction);
            final TransactionId transactionId1 = transactionDatabaseManager.storeTransactionHash(transaction);
            final List<TransactionId> transactionIds = transactionDatabaseManager.storeTransactionHashes(new ImmutableList<Transaction>(transaction, transaction));

            // Assert
            Assert.assertEquals(transactionId0, transactionId1);
            Assert.assertEquals(2, transactionIds.getCount());
            Assert.assertEquals(transactionId0, transactionIds.get(0));
            Assert.assertEquals(transactionId0, transactionIds.get(1));
        }
    }
}
