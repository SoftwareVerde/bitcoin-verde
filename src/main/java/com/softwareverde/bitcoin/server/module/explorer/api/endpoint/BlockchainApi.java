package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.explorer.api.ApiResult;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeJsonRpcConnection;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.GetParameters;
import com.softwareverde.servlet.PostParameters;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.JsonResponse;
import com.softwareverde.servlet.response.Response;

import static com.softwareverde.servlet.response.Response.ResponseCodes;

public class BlockchainApi extends ExplorerApiEndpoint {
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

    public BlockchainApi(final Configuration.ExplorerProperties explorerProperties, final ThreadPool threadPool) {
        super(explorerProperties, threadPool);
    }

    @Override
    protected Response _onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        {   // GET BLOCKCHAIN TREE
            // Requires GET:
            // Requires POST:
            try (final NodeJsonRpcConnection nodeJsonRpcConnection = _getNodeJsonRpcConnection()) {
                if (nodeJsonRpcConnection == null) {
                    final BlockchainResult result = new BlockchainResult();
                    result.setWasSuccess(false);
                    return new JsonResponse(ResponseCodes.SERVER_ERROR, result);
                }

                final Json blockchainJson;
                {
                    final Json rpcResponseJson = nodeJsonRpcConnection.getBlockchainMetadata();
                    if (rpcResponseJson == null) {
                        return new JsonResponse(Response.ResponseCodes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                    }

                    if (! rpcResponseJson.getBoolean("wasSuccess")) {
                        final String errorMessage = rpcResponseJson.getString("errorMessage");
                        return new JsonResponse(Response.ResponseCodes.SERVER_ERROR, new ApiResult(false, errorMessage));
                    }

                    blockchainJson = nodeJsonRpcConnection.getBlockchainMetadata();
                }

                final BlockchainResult blockchainResult = new BlockchainResult();
                blockchainResult.setWasSuccess(true);
                blockchainResult.setBlockchainMetadataJson(blockchainJson);
                return new JsonResponse(ResponseCodes.OK, blockchainResult);
            }
        }
    }
}
