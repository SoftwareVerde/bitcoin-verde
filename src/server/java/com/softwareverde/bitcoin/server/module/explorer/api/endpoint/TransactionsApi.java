package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.v1.get.GetDoubleSpendProofHandler;
import com.softwareverde.bitcoin.server.module.explorer.api.v1.get.GetTransactionHandler;
import com.softwareverde.bitcoin.server.module.explorer.api.v1.post.SubmitTransactionHandler;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.json.Json;

public class TransactionsApi extends ExplorerApiEndpoint {
    public static class SubmitTransactionResult extends ApiResult { }

    public static class GetTransactionResult extends ApiResult {
        private Json _payload = null;

        public void setTransactionJson(final Json transactionJson) {
            _payload = transactionJson;
        }

        @Override
        public Json toJson() {
            final Json json = super.toJson();
            json.put("transaction", _payload);
            return json;
        }
    }

    public TransactionsApi(final String apiPrePath, final Environment environment) {
        super(environment);

        _defineEndpoint((apiPrePath + "/transactions"), HttpMethod.POST, new SubmitTransactionHandler());
        _defineEndpoint((apiPrePath + "/transactions/<hash>"), HttpMethod.GET, new GetTransactionHandler());
        _defineEndpoint((apiPrePath + "/transactions/<transactionHash>/double-spend-proofs"), HttpMethod.GET, new GetDoubleSpendProofHandler());
    }
}
