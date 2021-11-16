package com.softwareverde.bitcoin.server.module.explorer.api.v1.get;

import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.BlockchainApi;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.http.server.servlet.routed.RequestHandler;
import com.softwareverde.json.Json;

import java.util.Map;

public class GetBlockchainSegmentsHandler implements RequestHandler<Environment> {

    /**
     * GET BLOCKCHAIN METADATA
     * Requires GET:
     * Requires POST:
     */
    @Override
    public Response handleRequest(final Request request, final Environment environment, final Map<String, String> parameters) throws Exception {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        try (final NodeJsonRpcConnection nodeJsonRpcConnection = environment.getNodeJsonRpcConnection()) {
            if (nodeJsonRpcConnection == null) {
                final BlockchainApi.BlockchainResult result = new BlockchainApi.BlockchainResult();
                result.setWasSuccess(false);
                result.setErrorMessage("Unable to connect to node.");
                return new JsonResponse(Response.Codes.SERVER_ERROR, result);
            }

            final Json blockchainJson;
            {
                final Json rpcResponseJson = nodeJsonRpcConnection.getBlockchainMetadata();
                if (rpcResponseJson == null) {
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                }

                if (! rpcResponseJson.getBoolean("wasSuccess")) {
                    final String errorMessage = rpcResponseJson.getString("errorMessage");
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, errorMessage));
                }

                blockchainJson = rpcResponseJson.get("blockchainMetadata");
            }

            final BlockchainApi.BlockchainResult blockchainResult = new BlockchainApi.BlockchainResult();
            blockchainResult.setWasSuccess(true);
            blockchainResult.setBlockchainMetadataJson(blockchainJson);
            return new JsonResponse(Response.Codes.OK, blockchainResult);
        }
    }
}
