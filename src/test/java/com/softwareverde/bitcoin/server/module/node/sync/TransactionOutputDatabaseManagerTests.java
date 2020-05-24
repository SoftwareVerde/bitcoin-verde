package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.slp.validator.FakeTransactionOutputIndexerContext;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.list.mutable.MutableList;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TransactionOutputDatabaseManagerTests extends IntegrationTest {

    @Override @Before
    public void before() {
        super.before();
    }

    @Override @After
    public void after() {
        super.after();
    }

    @Test
    public void should_store_script_addresses() throws Exception {
        // Setup
        final FakeTransactionOutputIndexerContext transactionOutputIndexerContext = new FakeTransactionOutputIndexerContext();
        final TransactionOutputIndexer transactionOutputIndexer = new TransactionOutputIndexer(transactionOutputIndexerContext);

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final MutableList<LockingScript> lockingScripts = new MutableList<LockingScript>();
            lockingScripts.add(ScriptBuilder.payToAddress("1CujTANFTa9YqSd9S6k3yCehoF2BBKs6ht"));
            lockingScripts.add(ScriptBuilder.payToAddress("15iUw9oLzsdQNQreGQZ6aPvM5b73BneGKy"));

            final MutableList<AddressId> addressIds = new MutableList<AddressId>();

            // Action
            for (final LockingScript lockingScript : lockingScripts) {
                final AddressId addressId = transactionOutputIndexer._getAddressId(lockingScript);
                addressIds.add(addressId);
            }

            // Assert
            Assert.assertEquals(2, addressIds.getCount());
            Assert.assertEquals(1L, addressIds.get(0).longValue());
            Assert.assertEquals(2L, addressIds.get(1).longValue());
        }
    }
}
