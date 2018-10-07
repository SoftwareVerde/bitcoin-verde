package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.BatchedInsertQuery;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.timer.MilliTimer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;

public class BlockDatabaseManagerPerformanceTests extends IntegrationTest {

    @Before
    public void setup() {
        _resetDatabase();
    }

    public static void _createRequiredTransactionInputs(final List<Transaction> transactions, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final HashSet<Sha256Hash> excludedTransactionHashes = new HashSet<Sha256Hash>(transactions.getSize());
        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            excludedTransactionHashes.add(transactionHash);
        }

        final HashMap<Sha256Hash, TransactionId> transactionHashes = new HashMap<Sha256Hash, TransactionId>(transactions.getSize());
        for (final Transaction transaction : transactions) {
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final Sha256Hash previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                if (excludedTransactionHashes.contains(previousOutputTransactionHash)) { continue; }

                transactionHashes.put(previousOutputTransactionHash, null);
            }
        }

        {
            long transactionId = 2L;
            final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO transactions (hash, version, lock_time) VALUES (?, ?, ?)");
            for (final Sha256Hash transactionHash : transactionHashes.keySet()) {
                batchedInsertQuery.setParameter(transactionHash);
                batchedInsertQuery.setParameter(Transaction.VERSION);
                batchedInsertQuery.setParameter(LockTime.MIN_TIMESTAMP.getValue());

                transactionHashes.put(transactionHash, TransactionId.wrap(transactionId));
                transactionId += 1L;
            }
            databaseConnection.executeSql(batchedInsertQuery);
        }

        {
            int sortOrder = 0;
            final BlockId genesisBlockId = BlockId.wrap(1L);
            final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO block_transactions (block_id, transaction_id, sort_order) VALUES (?, ?, ?)");
            for (final Sha256Hash transactionHash : transactionHashes.keySet()) {
                final TransactionId transactionId = transactionHashes.get(transactionHash);

                batchedInsertQuery.setParameter(genesisBlockId);
                batchedInsertQuery.setParameter(transactionId);
                batchedInsertQuery.setParameter(sortOrder);

                sortOrder += 1;
            }
            databaseConnection.executeSql(batchedInsertQuery);
        }

        {
            int transactionIndex = 0;
            final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO transaction_outputs (transaction_id, `index`, amount) VALUES (?, ?, ?)");
            for (final Transaction transaction : transactions) {
                final boolean isGenesisTransaction = (transactionIndex == 0);
                transactionIndex += 1;

                if (isGenesisTransaction) { continue; }

                for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                    final Sha256Hash previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                    if (excludedTransactionHashes.contains(previousOutputTransactionHash)) { continue; }

                    final TransactionId transactionId = transactionHashes.get(previousOutputTransactionHash);

                    batchedInsertQuery.setParameter(transactionId);
                    batchedInsertQuery.setParameter(transactionInput.getPreviousOutputIndex());
                    batchedInsertQuery.setParameter(Long.MAX_VALUE);
                }
            }
            databaseConnection.executeSql(batchedInsertQuery);
        }
    }

    // TODO: Create a test that has a transaction whose transactionInputs spends a previousOutputTransactionHash of EMPTY_HASH and whose index is -1, but is not a coinbase transaction... (Probably fails...)

    @Test
    public void should_store_giant_block_quickly() throws Exception {
        // Setup
        final MilliTimer setupTimer = new MilliTimer();
        setupTimer.start();

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockInflater blockInflater = new BlockInflater();

        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            blockDatabaseManager.storeBlock(blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK)));
        }

        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(IoUtil.getResource("/blocks/000000000000000001C37467F0843DD9E09536C21938C5C20551191788A70541")));

        _createRequiredTransactionInputs(block.getTransactions(), databaseConnection);

        setupTimer.stop();
        System.out.println("Setup Duration: " + setupTimer.getMillisecondsElapsed() + "ms");

        // Action
        final MilliTimer storeTimer = new MilliTimer();
        storeTimer.start();
        final BlockId blockId;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            blockId = blockDatabaseManager.storeBlock(block);
        }
        storeTimer.stop();
        System.out.println("Store Duration: " + storeTimer.getMillisecondsElapsed() + "ms");

        //  0.5-30ms each; 1231567ms total
        //  80s - Implemented batch inserts
        //  67s - Improved TxOutput Searching
        //  53s - Batched TxOutput Searching
        //  44s - Batched Address Inserting
        // 174s - "Better" Batched Address Inserting (Inefficient Script::equals & Script::hashcode)
        //  24s - Better Batched Address Inserting
        //  26s - Added Duplicate-transaction detection

        // Final Timing Results:
        // Script Pattern Matching: 1ms
        // Store Scripts: 1ms
        // Store TransactionOutputs: 1ms
        // Store LockingScripts: 61ms
        // Store Transactions: 3ms
        // Store TxOutputs: 62ms
        // Store TxInputs: 4ms
        // Stored 1 Transactions: 70ms
        // Associated 1 Transactions: 1ms
        // Setup Duration: 4615ms
        // -----
        // Store Addresses: 4276ms
        // Script Pattern Matching: 69ms
        // Store Scripts: 4035ms
        // Store TransactionOutputs: 2097ms
        // Store LockingScripts: 8380ms
        // Store Transactions: 5471ms
        // Store TxOutputs: 10560ms
        // Store TxInputs: 8392ms
        // Stored 95861 Transactions: 24423ms
        // Associated 95861 Transactions: 1230ms
        // Store Duration: 25847ms

        // Assert
        Assert.assertNotNull(blockId);
    }

}
