package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.GetParameters;
import com.softwareverde.servlet.PostParameters;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.JsonResponse;
import com.softwareverde.servlet.response.Response;
import com.softwareverde.socket.SocketConnection;

import static com.softwareverde.servlet.response.Response.ResponseCodes;

public class BlockchainApi extends ExplorerApiEndpoint {
    public static final Long RPC_DURATION_TIMEOUT_MS = 30000L;

    private static class BlockchainResult extends ApiResult {
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

    public BlockchainApi(final Configuration.ExplorerProperties explorerProperties) {
        super(explorerProperties);
    }

    @Override
    protected Response _onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        {   // GET BLOCKCHAIN TREE
            // Requires GET:
            // Requires POST:
            try (final SocketConnection socketConnection = _newRpcConnection()) {
                if (socketConnection == null) {
                    final BlockchainResult result = new BlockchainResult();
                    result.setWasSuccess(false);
                    return new JsonResponse(ResponseCodes.SERVER_ERROR, result);
                }

                final Json blockchainJson;
                {
                    final Json rpcRequestJson = new Json();
                    {
                        final Json rpcParametersJson = new Json();
                        rpcRequestJson.put("method", "GET");
                        rpcRequestJson.put("query", "BLOCKCHAIN");
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

                    blockchainJson = rpcResponseJson.get("blockchainMetadata");
                }

                final BlockchainResult blockchainResult = new BlockchainResult();
                blockchainResult.setWasSuccess(true);
                blockchainResult.setBlockchainMetadataJson(blockchainJson);
                return new JsonResponse(ResponseCodes.OK, blockchainResult);
            }
        }
    }
}
