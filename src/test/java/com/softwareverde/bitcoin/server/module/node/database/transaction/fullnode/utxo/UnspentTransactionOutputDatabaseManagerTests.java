package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class UnspentTransactionOutputDatabaseManagerTests extends IntegrationTest {
    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    protected static final Long MAX_UTXO_COUNT = 32L;

    protected Long _getUtxoCountInMemory() throws DatabaseException {
        return (long) UnspentTransactionOutputJvmManager.UTXO_SET.size();
    }

    protected Long _getUtxoCountOnDisk(final DatabaseConnection databaseConnection) throws DatabaseException {
        final List<Row> rows = databaseConnection.query(new Query("SELECT COUNT(*) AS count FROM committed_unspent_transaction_outputs"));
        final Row row = rows.get(0);
        return row.getLong("count");
    }

    @Test
    public void should_purge_utxo_set_by_half_once_full() throws Exception {
        // Setup
        final FullNodeDatabaseManager fullNodeDatabaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager();
        final float purgePercent = 0.50F;

        final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = new UnspentTransactionOutputJvmManager(MAX_UTXO_COUNT, purgePercent, fullNodeDatabaseManager, _blockStore, _masterInflater);

        long blockHeight = 1L;
        for (int i = 0; i < MAX_UTXO_COUNT; ) {
            final int utxoCountPerBlock = ((i * 2) + 1);

            final MutableList<TransactionOutputIdentifier> transactionOutputIdentifiers = new MutableList<TransactionOutputIdentifier>(utxoCountPerBlock);

            for (int j = 0; j < utxoCountPerBlock; ++j) {
                if (i >= MAX_UTXO_COUNT) { break; }

                final Sha256Hash transactionHash = Sha256Hash.wrap(HashUtil.sha256(ByteUtil.integerToBytes(i)));
                final Integer outputIndex = (j % 4);

                transactionOutputIdentifiers.add(new TransactionOutputIdentifier(transactionHash, outputIndex));
                i += 1;
            }

            unspentTransactionOutputDatabaseManager.insertUnspentTransactionOutputs(transactionOutputIdentifiers, blockHeight);
            unspentTransactionOutputDatabaseManager.setUncommittedUnspentTransactionOutputBlockHeight(blockHeight);
            blockHeight += 1L;
        }

        { // Sanity-check the UTXO count...
            final Long utxoCount = _getUtxoCountInMemory();
            Assert.assertEquals(MAX_UTXO_COUNT, utxoCount);
        }

        // Action
        unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(_fullNodeDatabaseManagerFactory, CommitAsyncMode.BLOCK_UNTIL_COMPLETE);

        // Assert
        final long utxoCountInMemory = _getUtxoCountInMemory();
        Assert.assertEquals((long) (MAX_UTXO_COUNT * (1.0F - purgePercent)), utxoCountInMemory);

        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            Assert.assertEquals(MAX_UTXO_COUNT, _getUtxoCountOnDisk(databaseConnection));
        }
    }
}
