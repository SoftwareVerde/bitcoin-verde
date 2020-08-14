package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeAtomicTransactionOutputIndexerContext;
import com.softwareverde.bitcoin.test.fake.FakeTransactionOutputIndexerContext;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TransactionOutputDatabaseManagerTests extends UnitTest {

    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_store_script_addresses() throws Exception {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final FakeTransactionOutputIndexerContext transactionOutputIndexerContext = new FakeTransactionOutputIndexerContext();
        final FakeAtomicTransactionOutputIndexerContext atomicTransactionOutputIndexerContext = transactionOutputIndexerContext.getContext();

        final BlockchainIndexer blockchainIndexer = new BlockchainIndexer(transactionOutputIndexerContext);

        final MutableList<LockingScript> lockingScripts = new MutableList<LockingScript>();
        lockingScripts.add(ScriptBuilder.payToAddress("1CujTANFTa9YqSd9S6k3yCehoF2BBKs6ht"));
        lockingScripts.add(ScriptBuilder.payToAddress("15iUw9oLzsdQNQreGQZ6aPvM5b73BneGKy"));

        // Action
        for (final LockingScript lockingScript : lockingScripts) {
            final AddressId addressId = blockchainIndexer._getAddressId(atomicTransactionOutputIndexerContext, lockingScript);
        }

        // Assert
        final List<Address> storedAddresses = atomicTransactionOutputIndexerContext.getStoredAddresses();
        Assert.assertEquals(2, storedAddresses.getCount());
        Assert.assertTrue(storedAddresses.contains(addressInflater.fromBase58Check("1CujTANFTa9YqSd9S6k3yCehoF2BBKs6ht")));
        Assert.assertTrue(storedAddresses.contains(addressInflater.fromBase58Check("15iUw9oLzsdQNQreGQZ6aPvM5b73BneGKy")));
    }
}
