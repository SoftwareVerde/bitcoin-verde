package com.softwareverde.bitcoin.server.module.explorer.api.v1.get;

import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.BlocksApi;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.http.server.servlet.routed.RequestHandler;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

import java.util.Map;

public class ListBlockHeadersHandler implements RequestHandler<Environment> {

    /**
     * LIST BLOCK HEADERS
     * Requires GET:    [blockHeight=null], [maxBlockCount]
     * Requires POST:
     */
    @Override
    public Response handleRequest(final Request request, final Environment environment, final Map<String, String> parameters) throws Exception {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        try (final NodeJsonRpcConnection nodeJsonRpcConnection = environment.getNodeJsonRpcConnection()) {
            if (nodeJsonRpcConnection == null) {
                final BlocksApi.RecentBlocksResult result = new BlocksApi.RecentBlocksResult();
                result.setWasSuccess(false);
                result.setErrorMessage("Unable to connect to node.");
                return new JsonResponse(Response.Codes.SERVER_ERROR, result);
            }

            final Json blockHeadersJson;
            {
                final Long blockHeight = (getParameters.containsKey("blockHeight") ? Util.parseLong(getParameters.get("blockHeight"), null) : null);
                final Integer maxBlockCount = (getParameters.containsKey("maxBlockCount") ? Util.parseInt(getParameters.get("maxBlockCount"), null) : null);
                final Boolean rawFormat = (getParameters.containsKey("rawFormat") ? Util.parseBool(getParameters.get("rawFormat"), false) : false);

                final Json rpcResponseJson = nodeJsonRpcConnection.getBlockHeaders(blockHeight, maxBlockCount, rawFormat);
                if (rpcResponseJson == null) {
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                }

                if (! rpcResponseJson.getBoolean("wasSuccess")) {
                    final String errorMessage = rpcResponseJson.getString("errorMessage");
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, errorMessage));
                }

                blockHeadersJson = rpcResponseJson.get("blockHeaders");
            }

            final BlocksApi.RecentBlocksResult recentBlocksResult = new BlocksApi.RecentBlocksResult();
            recentBlocksResult.setWasSuccess(true);
            recentBlocksResult.setBlockHeadersJson(blockHeadersJson);
            return new JsonResponse(Response.Codes.OK, recentBlocksResult);
        }
    }
}
