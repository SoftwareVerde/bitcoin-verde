package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.v1.get.GetSlpValidityHandler;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.json.Json;

public class SlpApi extends ExplorerApiEndpoint {
    public static class SlpValidityResult extends ApiResult {
        private Boolean _isValid;
        protected Sha256Hash _transactionHash;

        public void setIsValid(final Boolean isValid) {
            _isValid = isValid;
        }

        public void setTransactionHash(final Sha256Hash transactionHash) {
            _transactionHash = transactionHash;
        }

        @Override
        public Json toJson() {
            final Json json = super.toJson();
            json.put("isValid", _isValid);
            json.put("transactionHash", _transactionHash);
            return json;
        }
    }

    public SlpApi(final String apiPrePath, final Environment environment) {
        super(environment);

        _defineEndpoint((apiPrePath + "/slp/validate/<transactionHash>"), HttpMethod.GET, new GetSlpValidityHandler());
    }
}
