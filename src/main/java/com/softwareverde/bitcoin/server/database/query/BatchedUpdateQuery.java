package com.softwareverde.bitcoin.server.database.query;

public class BatchedUpdateQuery extends Query {
    protected static class BatchedUpdateQueryWrapper extends com.softwareverde.database.query.BatchedUpdateQuery {
        public BatchedUpdateQueryWrapper(final Query query, final Boolean shouldConsumeQuery) {
            super(query, shouldConsumeQuery);
        }
    }

    protected com.softwareverde.database.query.BatchedUpdateQuery _batchedUpdateQuery = null;

    protected void _requireBatchedUpdateQuery() {
        if (_batchedUpdateQuery == null) {
            _batchedUpdateQuery = new BatchedUpdateQueryWrapper(this, true);
        }
    }

    public BatchedUpdateQuery(final String query) {
        super(query);
    }

    @Override
    public String getQueryString() {
        _requireBatchedUpdateQuery();
        return _batchedUpdateQuery.getQueryString();
    }

    public void clear() {
        _requireBatchedUpdateQuery();
        _batchedUpdateQuery.clear();
    }
}