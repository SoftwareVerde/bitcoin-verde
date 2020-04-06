package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.indexer.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TransactionOutputDatabaseManagerTests extends IntegrationTest {

    @Before
    public void setup() {
        _resetDatabase();
    }

    @Test
    public void should_store_script_addresses() throws Exception {
        // Setup
        final TransactionOutputIndexerPartialMock transactionOutputIndexer = new TransactionOutputIndexerPartialMock(_fullNodeDatabaseManagerFactory);

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final MutableList<LockingScript> lockingScripts = new MutableList<LockingScript>();
            lockingScripts.add(ScriptBuilder.payToAddress("1CujTANFTa9YqSd9S6k3yCehoF2BBKs6ht"));
            lockingScripts.add(ScriptBuilder.payToAddress("15iUw9oLzsdQNQreGQZ6aPvM5b73BneGKy"));

            final MutableList<AddressId> addressIds = new MutableList<AddressId>();

            // Action
            for (final LockingScript lockingScript : lockingScripts) {
                final AddressId addressId = transactionOutputIndexer._getAddressId(lockingScript, databaseManager);
                addressIds.add(addressId);
            }

            // Assert
            Assert.assertEquals(2, addressIds.getCount());
            Assert.assertEquals(1L, addressIds.get(0).longValue());
            Assert.assertEquals(2L, addressIds.get(1).longValue());
        }
    }
}

class TransactionOutputIndexerPartialMock extends TransactionOutputIndexer {
    public TransactionOutputIndexerPartialMock(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        super(databaseManagerFactory);
    }

    @Override
    public AddressId _getAddressId(final LockingScript lockingScript, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        return super._getAddressId(lockingScript, databaseManager);
    }
}
