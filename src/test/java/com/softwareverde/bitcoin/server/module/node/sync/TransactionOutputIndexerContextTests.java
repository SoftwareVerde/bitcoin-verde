package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.context.AtomicTransactionOutputIndexerContext;
import com.softwareverde.bitcoin.context.TransactionOutputIndexerContext;
import com.softwareverde.bitcoin.context.lazy.LazyTransactionOutputIndexerContext;
import com.softwareverde.bitcoin.test.IntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TransactionOutputIndexerContextTests extends IntegrationTest {
    @Before
    public void before() {
        super.before();
    }

    @After
    public void after() {
        super.after();
    }

    @Test
    public void should_index_transaction_output() throws Exception {
        // Setup
        final TransactionOutputIndexerContext transactionOutputIndexerContext = new LazyTransactionOutputIndexerContext(_fullNodeDatabaseManagerFactory);

        // Action
        try (final AtomicTransactionOutputIndexerContext context = transactionOutputIndexerContext.newTransactionOutputIndexerContext()) {

        }

        // Assert
    }
}
