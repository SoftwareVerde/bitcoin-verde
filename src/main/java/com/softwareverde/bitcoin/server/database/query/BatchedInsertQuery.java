package com.softwareverde.bitcoin.server.database.query;

public class BatchedInsertQuery extends Query {
    protected static class BatchedInsertQueryWrapper extends com.softwareverde.database.query.BatchedInsertQuery {
        public BatchedInsertQueryWrapper(final Query query, final Boolean shouldConsumeQuery) {
            super(query, shouldConsumeQuery, new ParameterFactory());
        }
    }

    protected com.softwareverde.database.query.BatchedInsertQuery _batchedInsertQuery = null;

    protected void _requireBatchedInsertQuery() {
        if (_batchedInsertQuery == null) {
            _batchedInsertQuery = new BatchedInsertQueryWrapper(this, true);
        }
    }

    public BatchedInsertQuery(final String query) {
        super(query);
    }

    @Override
    public String getQueryString() {
        _requireBatchedInsertQuery();
        return _batchedInsertQuery.getQueryString();
    }

    public void clear() {
        _requireBatchedInsertQuery();
        _batchedInsertQuery.clear();
    }
}