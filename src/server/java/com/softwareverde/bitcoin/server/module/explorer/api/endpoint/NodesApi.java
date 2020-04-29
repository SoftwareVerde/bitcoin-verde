package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.v1.get.GetNodesHandler;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.json.Json;

public class NodesApi extends ExplorerApiEndpoint {
    public static class NodesResult extends ApiResult {
        private Json _nodes = new Json(true);

        public void setNodes(final Json nodes) {
            _nodes = nodes;
        }

        @Override
        public Json toJson() {
            final Json json = super.toJson();
            json.put("nodes", _nodes);
            return json;
        }
    }

    public NodesApi(final String apiPrePath, final Environment environment) {
        super(environment);

        _defineEndpoint((apiPrePath + "/nodes"), HttpMethod.GET, new GetNodesHandler());
    }
}
