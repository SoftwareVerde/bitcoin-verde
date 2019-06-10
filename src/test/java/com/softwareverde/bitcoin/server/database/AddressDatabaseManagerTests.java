package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.server.module.node.database.address.AddressDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.core.CoreDatabaseManager;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AddressDatabaseManagerTests extends IntegrationTest {

    @Before
    public void setup() {
        _resetDatabase();
    }

    @Test
    public void should_store_script_addresses() throws Exception {
        // Setup
        try (final CoreDatabaseManager databaseManager = _coreDatabaseManagerFactory.newDatabaseManager()) {
            final AddressDatabaseManager addressDatabaseManager = databaseManager.getAddressDatabaseManager();

            final MutableList<LockingScript> lockingScripts = new MutableList<LockingScript>();
            lockingScripts.add(ScriptBuilder.payToAddress("1CujTANFTa9YqSd9S6k3yCehoF2BBKs6ht"));
            lockingScripts.add(ScriptBuilder.payToAddress("15iUw9oLzsdQNQreGQZ6aPvM5b73BneGKy"));

            // Action
            final List<AddressId> addressIds = addressDatabaseManager.storeScriptAddresses(lockingScripts);

            // Assert
            Assert.assertEquals(2, addressIds.getSize());
            Assert.assertEquals(1L, addressIds.get(0).longValue());
            Assert.assertEquals(2L, addressIds.get(1).longValue());
        }
    }
}
