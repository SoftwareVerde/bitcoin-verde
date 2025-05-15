package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.v1.get.GetDoubleSpendProofHandler;
import com.softwareverde.bitcoin.server.module.explorer.api.v1.get.ListDoubleSpendProofsHandler;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.json.Json;

public class DoubleSpendProofsApi extends ExplorerApiEndpoint {
    public static class DoubleSpendProofResult extends ApiResult {
        private Boolean _returnTypeIsArray = null;
        private Json _payload = null;

        public void setDoubleSpendProofsJson(final Json doubleSpendProofsJson) {
            _returnTypeIsArray = true;
            _payload = doubleSpendProofsJson;
        }

        public void setDoubleSpendProofJson(final Json doubleSpendProofJson) {
            _returnTypeIsArray = false;
            _payload = doubleSpendProofJson;
        }

        @Override
        public Json toJson() {
            final Json json = super.toJson();
            if (_returnTypeIsArray != null) {
                if (_returnTypeIsArray) {
                    json.put("doubleSpendProofs", _payload);
                }
                else {
                    json.put("doubleSpendProof", _payload);
                }
            }
            return json;
        }
    }

    public DoubleSpendProofsApi(final String apiPrePath, final Environment environment) {
        super(environment);

        _defineEndpoint((apiPrePath + "/double-spend-proofs"), HttpMethod.GET, new ListDoubleSpendProofsHandler());
        _defineEndpoint((apiPrePath + "/double-spend-proofs/<hash>"), HttpMethod.GET, new GetDoubleSpendProofHandler());
    }
}
