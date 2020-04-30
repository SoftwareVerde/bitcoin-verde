package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.v1.get.GetBlockTransactionsHandler;
import com.softwareverde.bitcoin.server.module.explorer.api.v1.get.ListBlockHeadersHandler;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.json.Json;

public class BlocksApi extends ExplorerApiEndpoint {
    public static class RecentBlocksResult extends ApiResult {
        private Json _blockHeadersJson = new Json(true);

        public void setBlockHeadersJson(final Json blockHeadersJson) {
            _blockHeadersJson = blockHeadersJson;
        }

        @Override
        public Json toJson() {
            final Json json = super.toJson();
            json.put("blockHeaders", _blockHeadersJson);
            return json;
        }
    }

    public static class BlockTransactionsResult extends ApiResult {
        private Json _transactions = new Json(true);

        public BlockTransactionsResult() {
            super();
        }

        public BlockTransactionsResult(final Boolean wasSuccess, final String errorMessage) {
            super(wasSuccess, errorMessage);
        }

        public void setTransactions(final Json transactions) {
            _transactions = transactions;
        }

        @Override
        public Json toJson() {
            final Json json = super.toJson();
            json.put("transactions", _transactions);
            return json;
        }
    }

    public BlocksApi(final String apiPrePath, final Environment environment) {
        super(environment);

        _defineEndpoint((apiPrePath + "/blocks"), HttpMethod.GET, new ListBlockHeadersHandler());
        _defineEndpoint((apiPrePath + "/blocks/<blockHash>/transactions"), HttpMethod.GET, new GetBlockTransactionsHandler());
    }
}
