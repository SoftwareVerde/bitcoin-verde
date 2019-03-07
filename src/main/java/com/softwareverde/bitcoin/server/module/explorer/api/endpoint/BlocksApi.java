package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeJsonRpcConnection;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

public class BlocksApi extends ExplorerApiEndpoint {
    private static class RecentBlocksResult extends ApiResult {
        private Json _blockHeadersJson = new Json(true);

        public void setBlockHeadersJson(final Json blockHeadersJson) {
            _blockHeadersJson = blockHeadersJson;
        }

        @Override
        public Json toJson() {
            final Json json = super.toJson();
            json.put("blockHeaders", _blockHeadersJson);
            return json;
        }
    }

    public BlocksApi(final Configuration.ExplorerProperties explorerProperties, final ThreadPool threadPool) {
        super(explorerProperties, threadPool);
    }

    @Override
    protected Response _onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        {   // LIST BLOCK HEADERS
            // Requires GET:    [blockHeight=null], [maxBlockCount]
            // Requires POST:
            try (final NodeJsonRpcConnection nodeJsonRpcConnection = _getNodeJsonRpcConnection()) {
                if (nodeJsonRpcConnection == null) {
                    final RecentBlocksResult result = new RecentBlocksResult();
                    result.setWasSuccess(false);
                    result.setErrorMessage("Unable to connect to node.");
                    return new JsonResponse(Response.Codes.SERVER_ERROR, result);
                }

                final Json blockHeadersJson;
                {
                    final Long blockHeight = (getParameters.containsKey("blockHeight") ? Util.parseLong(getParameters.get("blockHeight"), null) : null);
                    final Integer maxBlockCount = (getParameters.containsKey("maxBlockCount") ? Util.parseInt(getParameters.get("maxBlockCount"), null) : null);

                    final Json rpcResponseJson = nodeJsonRpcConnection.getBlockHeaders(blockHeight, maxBlockCount, false);
                    if (rpcResponseJson == null) {
                        return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                    }

                    if (! rpcResponseJson.getBoolean("wasSuccess")) {
                        final String errorMessage = rpcResponseJson.getString("errorMessage");
                        return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, errorMessage));
                    }

                    blockHeadersJson = rpcResponseJson.get("blockHeaders");
                }

                final RecentBlocksResult recentBlocksResult = new RecentBlocksResult();
                recentBlocksResult.setWasSuccess(true);
                recentBlocksResult.setBlockHeadersJson(blockHeadersJson);
                return new JsonResponse(Response.Codes.OK, recentBlocksResult);
            }
        }
    }
}
