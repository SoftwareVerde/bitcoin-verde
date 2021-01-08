package com.softwareverde.bitcoin.server.module.explorer.api.v1.get;

import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.NodesApi;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.http.server.servlet.routed.RequestHandler;
import com.softwareverde.json.Json;

import java.util.Map;

public class GetNodesHandler implements RequestHandler<Environment> {

    /**
     * LIST CONNECTED NODES
     * Requires GET:
     * Requires POST:
     */
    @Override
    public Response handleRequest(final Request request, final Environment environment, final Map<String, String> parameters) throws Exception {
        // final GetParameters getParameters = request.getGetParameters();
        // final PostParameters postParameters = request.getPostParameters();

        try (final NodeJsonRpcConnection nodeJsonRpcConnection = environment.getNodeJsonRpcConnection()) {
            if (nodeJsonRpcConnection == null) {
                final NodesApi.NodesResult result = new NodesApi.NodesResult();
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

            final NodesApi.NodesResult nodesResult = new NodesApi.NodesResult();
            nodesResult.setWasSuccess(true);
            nodesResult.setNodes(nodesJson);
            return new JsonResponse(Response.Codes.OK, nodesResult);
        }
    }
}
