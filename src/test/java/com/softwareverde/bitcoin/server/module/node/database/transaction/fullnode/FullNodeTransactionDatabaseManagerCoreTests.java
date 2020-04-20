package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode;

import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.bitcoin.test.FakeBlockStore;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.security.util.HashUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class FullNodeTransactionDatabaseManagerCoreTests extends IntegrationTest {
    @Before
    public void setup() {
        _resetDatabase();
    }

    protected static final Long MAX_UTXO_COUNT = 32L;

    static class PartialMockTransactionDatabaseManager extends FullNodeTransactionDatabaseManagerCore {
        public PartialMockTransactionDatabaseManager(final FullNodeDatabaseManager databaseManager, final BlockStore blockStore, final MasterInflater masterInflater) {
            super(databaseManager, blockStore, masterInflater);
        }

        @Override
        public Long _getMaxUtxoCount() {
            return MAX_UTXO_COUNT;
        }
    }

    protected Long _getUtxoCountInMemory(final DatabaseConnection databaseConnection) throws DatabaseException {
        final List<Row> rows = databaseConnection.query(new Query("SELECT COUNT(*) AS count FROM unspent_transaction_outputs"));
        final Row row = rows.get(0);
        return row.getLong("count");
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
        try (final DatabaseConnection databaseConnection = fullNodeDatabaseManager.getDatabaseConnection()) {

            final FullNodeTransactionDatabaseManager transactionDatabaseManager = new PartialMockTransactionDatabaseManager(fullNodeDatabaseManager, _blockStore, _masterInflater);

            Long blockHeight = 1L;
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

                transactionDatabaseManager.insertUnspentTransactionOutputs(transactionOutputIdentifiers, blockHeight);
                blockHeight += 1L;
            }

            { // Sanity-check the UTXO count...
                final Long utxoCount = _getUtxoCountInMemory(databaseConnection);
                Assert.assertEquals(MAX_UTXO_COUNT, utxoCount);
            }

            // Action
            transactionDatabaseManager.commitUnspentTransactionOutputs(_databaseConnectionFactory);

            // Assert
            final Long utxoCountInMemory = _getUtxoCountInMemory(databaseConnection);
            Assert.assertEquals(Long.valueOf(MAX_UTXO_COUNT / 2L), utxoCountInMemory);
        }
    }
}
