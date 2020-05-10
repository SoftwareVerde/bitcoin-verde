package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.v1.get.GetBlockchainSegmentsHandler;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.json.Json;

public class BlockchainApi extends ExplorerApiEndpoint {
    public static class BlockchainResult extends ApiResult {
        private Json _blockchainJson = new Json();

        public void setBlockchainMetadataJson(final Json blockchainJson) {
            _blockchainJson = blockchainJson;
        }

        @Override
        public Json toJson() {
            final Json json = super.toJson();
            json.put("blockchainMetadata", _blockchainJson);
            return json;
        }
    }

    public BlockchainApi(final String apiPrePath, final Environment environment) {
        super(environment);

        _defineEndpoint((apiPrePath + "/blockchain"), HttpMethod.GET, new GetBlockchainSegmentsHandler());
    }
}
