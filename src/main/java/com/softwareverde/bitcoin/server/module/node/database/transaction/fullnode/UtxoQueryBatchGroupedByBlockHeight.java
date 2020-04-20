package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode;

import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.util.Util;

class UtxoQueryBatchGroupedByBlockHeight implements BatchRunner.Batch<UnspentTransactionOutput> {
    public interface QueryExecutor {
        void executeQuery(List<UnspentTransactionOutput> unspentTransactionOutputsByBlockHeight, Long blockHeight, Query query, DatabaseConnection databaseConnection) throws DatabaseException;
    }

    protected final String _queryString;
    protected final QueryExecutor _queryExecutor;
    protected final DatabaseConnectionFactory _databaseConnectionFactory;

    public UtxoQueryBatchGroupedByBlockHeight(final DatabaseConnectionFactory databaseConnectionFactory, final String query, final QueryExecutor queryExecutor) {
        _queryString = query;
        _queryExecutor = queryExecutor;
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    public void run(final List<UnspentTransactionOutput> batchItems) throws Exception {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {

            final MutableList<UnspentTransactionOutput> unspentTransactionOutputsByBlockHeight = new MutableList<UnspentTransactionOutput>(batchItems.getCount());

            Long lastBlockHeight = -1L; // Cannot be null, since null is an acceptable value from UnspentTransactionOutput::getBlockHeight...
            for (final UnspentTransactionOutput unspentTransactionOutput : batchItems) {
                final Long blockHeight = unspentTransactionOutput.getBlockHeight();

                if ( (! unspentTransactionOutputsByBlockHeight.isEmpty()) && (! Util.areEqual(blockHeight, lastBlockHeight)) ) {
                    final Query query = new Query(_queryString);
                    _queryExecutor.executeQuery(unspentTransactionOutputsByBlockHeight, lastBlockHeight, query, databaseConnection);
                    unspentTransactionOutputsByBlockHeight.clear();
                }

                unspentTransactionOutputsByBlockHeight.add(unspentTransactionOutput);
                lastBlockHeight = blockHeight;
            }

            if (! unspentTransactionOutputsByBlockHeight.isEmpty()) {
                final Query query = new Query(_queryString);
                _queryExecutor.executeQuery(unspentTransactionOutputsByBlockHeight, lastBlockHeight, query, databaseConnection);
                unspentTransactionOutputsByBlockHeight.clear();
            }
        }
    }
}
