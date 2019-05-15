package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeJsonRpcConnection;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;

public class NodesApi extends ExplorerApiEndpoint {
    private static class NodesResult extends ApiResult {
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

    public NodesApi(final Configuration.ExplorerProperties explorerProperties, final ThreadPool threadPool) {
        super(explorerProperties, threadPool);
    }

    @Override
    protected Response _onRequest(final Request request) {
        // final GetParameters getParameters = request.getGetParameters();
        // final PostParameters postParameters = request.getPostParameters();

        {   // LIST NODES
            // Requires GET:
            // Requires POST:
            try (final NodeJsonRpcConnection nodeJsonRpcConnection = _getNodeJsonRpcConnection()) {
                if (nodeJsonRpcConnection == null) {
                    final NodesResult result = new NodesResult();
                    result.setWasSuccess(false);
                    result.setErrorMessage("Unable to connect to node.");
                    return new JsonResponse(Response.Codes.SERVER_ERROR, result);
                }

                final Json nodesJson;
                {
                    final Json rpcResponseJson = nodeJsonRpcConnection.getNodes();
                    if (rpcResponseJson == null) {
                        return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                    }

                    if (! rpcResponseJson.getBoolean("wasSuccess")) {
                        final String errorMessage = rpcResponseJson.getString("errorMessage");
                        return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, errorMessage));
                    }

                    nodesJson = rpcResponseJson.get("nodes");
                }

                final NodesResult nodesResult = new NodesResult();
                nodesResult.setWasSuccess(true);
                nodesResult.setNodes(nodesJson);
                return new JsonResponse(Response.Codes.OK, nodesResult);
            }
        }
    }
}
