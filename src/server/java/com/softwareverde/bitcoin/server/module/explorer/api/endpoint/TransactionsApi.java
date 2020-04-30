package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.v1.post.SubmitTransactionHandler;
import com.softwareverde.http.HttpMethod;

public class TransactionsApi extends ExplorerApiEndpoint {
    public static class SubmitTransactionResult extends ApiResult { }

    public TransactionsApi(final String apiPrePath, final Environment environment) {
        super(environment);

        _defineEndpoint((apiPrePath + "/transactions"), HttpMethod.POST, new SubmitTransactionHandler());
    }
}
