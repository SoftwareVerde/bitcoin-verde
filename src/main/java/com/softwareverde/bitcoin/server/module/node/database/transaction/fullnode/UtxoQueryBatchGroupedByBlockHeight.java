package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode;

import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.Util;

class UtxoQueryBatchGroupedByBlockHeight implements BatchRunner.Batch<UnspentTransactionOutput> {
    public interface ParameterApplier {
        void applyParameters(Query query, Long blockHeight, List<UnspentTransactionOutput> unspentTransactionOutputsByBlockHeight);
    }

    protected final String _queryString;
    protected final ParameterApplier _parameterApplier;
    protected final DatabaseConnectionFactory _databaseConnectionFactory;

    public UtxoQueryBatchGroupedByBlockHeight(final DatabaseConnectionFactory databaseConnectionFactory, final String query, final ParameterApplier parameterApplier) {
        _queryString = query;
        _parameterApplier = parameterApplier;
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    public void run(final List<UnspentTransactionOutput> batchItems) throws Exception {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {

            final MutableList<UnspentTransactionOutput> unspentTransactionOutputsByBlockHeight = new MutableList<UnspentTransactionOutput>(batchItems.getCount());

            Query deleteQuery = null;
            Long lastBlockHeight = null;
            for (final UnspentTransactionOutput unspentTransactionOutput : batchItems) {
                final Long blockHeight = unspentTransactionOutput.getBlockHeight();

                if (deleteQuery == null) {
                    deleteQuery = new Query(_queryString);
                }
                else if (! Util.areEqual(blockHeight, lastBlockHeight)) {
                    _parameterApplier.applyParameters(deleteQuery, lastBlockHeight, unspentTransactionOutputsByBlockHeight);
                    databaseConnection.executeSql(deleteQuery);
                    deleteQuery = null;
                    unspentTransactionOutputsByBlockHeight.clear();
                }

                unspentTransactionOutputsByBlockHeight.add(unspentTransactionOutput);
                lastBlockHeight = blockHeight;
            }

            if (deleteQuery != null) {
                _parameterApplier.applyParameters(deleteQuery, lastBlockHeight, unspentTransactionOutputsByBlockHeight);
                databaseConnection.executeSql(deleteQuery);
                deleteQuery = null;
                unspentTransactionOutputsByBlockHeight.clear();
            }
        }
    }
}
