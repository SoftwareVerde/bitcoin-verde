package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.JsonResponse;
import com.softwareverde.servlet.response.Response;
import com.softwareverde.socket.SocketConnection;

import static com.softwareverde.servlet.response.Response.ResponseCodes;

public class NodesApi extends ExplorerApiEndpoint {
    public static final Long RPC_DURATION_TIMEOUT_MS = 30000L;

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

    public NodesApi(final Configuration.ExplorerProperties explorerProperties) {
        super(explorerProperties);
    }

    @Override
    protected Response _onRequest(final Request request) {
        // final GetParameters getParameters = request.getGetParameters();
        // final PostParameters postParameters = request.getPostParameters();

        {   // LIST NODES
            // Requires GET:
            // Requires POST:
            try (final SocketConnection socketConnection = _newRpcConnection()) {
                if (socketConnection == null) {
                    final NodesResult result = new NodesResult();
                    result.setWasSuccess(false);
                    return new JsonResponse(ResponseCodes.SERVER_ERROR, result);
                }

                final Json nodesJson;
                {
                    final Json rpcRequestJson = new Json();
                    {
                        rpcRequestJson.put("method", "GET");
                        rpcRequestJson.put("query", "NODES");
                    }

                    socketConnection.write(rpcRequestJson.toString());

                    final String rpcResponseString = socketConnection.waitForMessage(RPC_DURATION_TIMEOUT_MS);
                    if (rpcResponseString == null) {
                        return new JsonResponse(Response.ResponseCodes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                    }

                    final Json rpcResponseJson = Json.parse(rpcResponseString);
                    if (! rpcResponseJson.getBoolean("wasSuccess")) {
                        final String errorMessage = rpcRequestJson.getString("errorMessage");
                        return new JsonResponse(Response.ResponseCodes.SERVER_ERROR, new ApiResult(false, errorMessage));
                    }

                    nodesJson = rpcResponseJson.get("nodes");
                }

                final NodesResult nodesResult = new NodesResult();
                nodesResult.setWasSuccess(true);
                nodesResult.setNodes(nodesJson);
                return new JsonResponse(ResponseCodes.OK, nodesResult);
            }
        }
    }
}
