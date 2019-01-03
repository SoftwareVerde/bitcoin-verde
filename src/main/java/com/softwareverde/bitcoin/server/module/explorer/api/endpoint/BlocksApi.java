package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.explorer.api.ApiResult;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.GetParameters;
import com.softwareverde.servlet.PostParameters;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.JsonResponse;
import com.softwareverde.servlet.response.Response;
import com.softwareverde.socket.SocketConnection;
import com.softwareverde.util.Util;

import static com.softwareverde.servlet.response.Response.ResponseCodes;

public class BlocksApi extends ExplorerApiEndpoint {
    public static final Long RPC_DURATION_TIMEOUT_MS = 30000L;

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

    public BlocksApi(final Configuration.ExplorerProperties explorerProperties) {
        super(explorerProperties);
    }

    @Override
    protected Response _onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        {   // LIST BLOCK HEADERS
            // Requires GET:    [blockHeight=null], [maxBlockCount]
            // Requires POST:
            try (final SocketConnection socketConnection = _newRpcConnection()) {
                if (socketConnection == null) {
                    final RecentBlocksResult result = new RecentBlocksResult();
                    result.setWasSuccess(false);
                    return new JsonResponse(ResponseCodes.SERVER_ERROR, result);
                }

                final Json blockHeadersJson;
                {
                    final Json rpcRequestJson = new Json();
                    {
                        final Json rpcParametersJson = new Json();
                        {
                            final Long blockHeight = (getParameters.containsKey("blockHeight") ? Util.parseLong(getParameters.get("blockHeight"), null) : null);
                            final Integer maxBlockCount = (getParameters.containsKey("maxBlockCount") ? Util.parseInt(getParameters.get("maxBlockCount"), null) : null);

                            rpcParametersJson.put("blockHeight", blockHeight);
                            rpcParametersJson.put("maxBlockCount", maxBlockCount);
                        }

                        rpcRequestJson.put("method", "GET");
                        rpcRequestJson.put("query", "BLOCK_HEADERS");
                        rpcRequestJson.put("parameters", rpcParametersJson);
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

                    blockHeadersJson = rpcResponseJson.get("blockHeaders");
                }

                final RecentBlocksResult recentBlocksResult = new RecentBlocksResult();
                recentBlocksResult.setWasSuccess(true);
                recentBlocksResult.setBlockHeadersJson(blockHeadersJson);
                return new JsonResponse(ResponseCodes.OK, recentBlocksResult);
            }
        }
    }
}
