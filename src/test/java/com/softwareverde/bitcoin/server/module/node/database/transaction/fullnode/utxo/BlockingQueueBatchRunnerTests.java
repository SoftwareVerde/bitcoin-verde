package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.transaction.BlockingQueueBatchRunner;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeDatabaseConnectionFactoryStub;
import com.softwareverde.bitcoin.test.fake.FakeDatabaseConnectionStub;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedDeque;

public class BlockingQueueBatchRunnerTests extends UnitTest {
    static class FakeDatabaseConnection extends FakeDatabaseConnectionStub {
        @Override
        public java.util.List<Row> query(final com.softwareverde.database.query.Query query) {
            return new ArrayList<Row>(0);
        }
    }

    static class FakeDatabaseConnectionFactory extends FakeDatabaseConnectionFactoryStub {
        @Override
        public DatabaseConnection newConnection() throws DatabaseException {
            return new FakeDatabaseConnection();
        }
    }

    @Test
    public void should_include_all_items_when_less_than_the_batch_size() throws Exception {
        // Setup
        final ConcurrentLinkedDeque<TransactionOutputIdentifier> executedItems = new ConcurrentLinkedDeque<TransactionOutputIdentifier>();

        final BlockingQueueBatchRunner<TransactionOutputIdentifier> blockingQueueBatchRunner = BlockingQueueBatchRunner.newInstance(
            new BatchRunner.Batch<TransactionOutputIdentifier>() {
                @Override
                public void run(final List<TransactionOutputIdentifier> batchItems) throws Exception {
                    for (final TransactionOutputIdentifier transactionOutputIdentifier : batchItems) {
                        executedItems.add(transactionOutputIdentifier);
                    }
                }
            }
        );

        final Sha256Hash transactionHash = TransactionOutputIdentifier.COINBASE.getTransactionHash();

        // Action
        blockingQueueBatchRunner.start();

        final int batchSize = (1024 + 1);

        for (int i = 0; i < batchSize; ++i) {
            final UnspentTransactionOutput transactionOutputIdentifier = new UnspentTransactionOutput(transactionHash, i, (i / 1024L));
            blockingQueueBatchRunner.addItem(transactionOutputIdentifier);
        }

        blockingQueueBatchRunner.finish();
        blockingQueueBatchRunner.join();

        // Assert
        Assert.assertEquals(batchSize, executedItems.size());

        for (int i = 0; i < batchSize; ++i) {
            final TransactionOutputIdentifier transactionOutputIdentifier = executedItems.removeFirst();
            Assert.assertEquals(transactionHash, transactionOutputIdentifier.getTransactionHash());
            Assert.assertEquals(Integer.valueOf(i), transactionOutputIdentifier.getOutputIndex());
        }

    }

    @Test
    public void UtxoQueryBatchGroupedByBlockHeight_should_include_all_items_when_less_than_the_batch_size() throws Exception {
        // Setup
        final ConcurrentLinkedDeque<TransactionOutputIdentifier> executedItems = new ConcurrentLinkedDeque<TransactionOutputIdentifier>();

        final FakeDatabaseConnectionFactory fakeDatabaseConnectionFactory = new FakeDatabaseConnectionFactory();
        final BlockingQueueBatchRunner<UnspentTransactionOutput> blockingQueueBatchRunner = BlockingQueueBatchRunner.newInstance(
            new UtxoQueryBatchGroupedByBlockHeight(
                fakeDatabaseConnectionFactory,
                "",
                new UtxoQueryBatchGroupedByBlockHeight.QueryExecutor() {
                    @Override
                    public void executeQuery(final List<UnspentTransactionOutput> unspentTransactionOutputsByBlockHeight, final Long blockHeight, final Query query, final DatabaseConnection databaseConnection) throws DatabaseException {
                        System.out.println("Executing Batch: height=" + blockHeight + ", count=" + unspentTransactionOutputsByBlockHeight.getCount());
                        for (final UnspentTransactionOutput unspentTransactionOutput : unspentTransactionOutputsByBlockHeight) {
                            executedItems.add(unspentTransactionOutput);
                        }
                    }
                }
            )
        );

        final Sha256Hash transactionHash = TransactionOutputIdentifier.COINBASE.getTransactionHash();

        // Action
        blockingQueueBatchRunner.start();

        final int batchSize = ((1024 * 3) + 1);

        for (int i = 0; i < batchSize; ++i) {
            final UnspentTransactionOutput transactionOutputIdentifier = new UnspentTransactionOutput(transactionHash, i, (i < 128L ? null : (i / 2048L)));
            blockingQueueBatchRunner.addItem(transactionOutputIdentifier);
        }

        blockingQueueBatchRunner.finish();
        blockingQueueBatchRunner.join();

        // Assert
        Assert.assertEquals(batchSize, executedItems.size());

        for (int i = 0; i < batchSize; ++i) {
            final TransactionOutputIdentifier transactionOutputIdentifier = executedItems.removeFirst();
            Assert.assertEquals(transactionHash, transactionOutputIdentifier.getTransactionHash());
            Assert.assertEquals(Integer.valueOf(i), transactionOutputIdentifier.getOutputIndex());
        }
    }

    @Test
    public void UtxoQueryBatchGroupedByBlockHeight_should_run_both_entries_when_last_entry_has_unique_block_height() throws Exception {
        // Setup
        final ConcurrentLinkedDeque<TransactionOutputIdentifier> executedItems = new ConcurrentLinkedDeque<TransactionOutputIdentifier>();

        final FakeDatabaseConnectionFactory fakeDatabaseConnectionFactory = new FakeDatabaseConnectionFactory();
        final BlockingQueueBatchRunner<UnspentTransactionOutput> blockingQueueBatchRunner = BlockingQueueBatchRunner.newInstance(
            new UtxoQueryBatchGroupedByBlockHeight(
                fakeDatabaseConnectionFactory,
                "",
                new UtxoQueryBatchGroupedByBlockHeight.QueryExecutor() {
                    @Override
                    public void executeQuery(final List<UnspentTransactionOutput> unspentTransactionOutputsByBlockHeight, final Long blockHeight, final Query query, final DatabaseConnection databaseConnection) throws DatabaseException {
                        System.out.println("Executing Batch: height=" + blockHeight + ", count=" + unspentTransactionOutputsByBlockHeight.getCount());
                        for (final UnspentTransactionOutput unspentTransactionOutput : unspentTransactionOutputsByBlockHeight) {
                            executedItems.add(unspentTransactionOutput);
                        }
                    }
                }
            )
        );

        // Action
        blockingQueueBatchRunner.start();

        blockingQueueBatchRunner.addItem(new UnspentTransactionOutput(Sha256Hash.fromHexString("0000000000000000000000000000000000000000000000000000000000000000"), 0, null));
        blockingQueueBatchRunner.addItem(new UnspentTransactionOutput(Sha256Hash.fromHexString("F4184FC596403B9D638783CF57ADFE4C75C605F6356FBC91338530E9831E9E16"), 1, 170L));
        blockingQueueBatchRunner.addItem(new UnspentTransactionOutput(Sha256Hash.fromHexString("0437CD7F8525CEED2324359C2D0BA26006D92D856A9C20FA0241106EE5A597C9"), 0, 9L));

        blockingQueueBatchRunner.finish();
        blockingQueueBatchRunner.join();

        // Assert
        Assert.assertEquals(blockingQueueBatchRunner.getTotalItemCount(), Integer.valueOf(executedItems.size()));
    }

}
