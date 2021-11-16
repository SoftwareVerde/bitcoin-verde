package com.softwareverde.bitcoin.server.module.explorer.api.v1.get;

import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.BlocksApi;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.http.server.servlet.routed.RequestHandler;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

import java.util.Map;

public class GetBlockTransactionsHandler implements RequestHandler<Environment> {

    /**
     * LIST TRANSACTIONS FOR BLOCK
     * Requires GET: blockHash, pageSize, pageNumber
     * Requires POST:
     */
    @Override
    public Response handleRequest(final Request request, final Environment environment, final Map<String, String> urlParameters) throws Exception {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        final Sha256Hash blockHash = (urlParameters.containsKey("blockHash") ? Sha256Hash.fromHexString(urlParameters.get("blockHash")) : null);
        if (blockHash == null) {
            final BlocksApi.BlockTransactionsResult result = new BlocksApi.BlockTransactionsResult();
            result.setWasSuccess(false);
            result.setErrorMessage("Invalid block hash.");
            return new JsonResponse(Response.Codes.BAD_REQUEST, result);
        }

        final Integer pageSize = (getParameters.containsKey("pageSize") ? Util.parseInt(getParameters.get("pageSize")) : null);
        if ( (pageSize == null) || (pageSize < 1) ) {
            final BlocksApi.BlockTransactionsResult result = new BlocksApi.BlockTransactionsResult();
            result.setWasSuccess(false);
            result.setErrorMessage("Invalid page size.");
            return new JsonResponse(Response.Codes.BAD_REQUEST, result);
        }

        final Integer pageNumber = (getParameters.containsKey("pageNumber") ? Util.parseInt(getParameters.get("pageNumber")) : null);
        if ( (pageNumber == null) || (pageNumber < 0) ) {
            final BlocksApi.BlockTransactionsResult result = new BlocksApi.BlockTransactionsResult();
            result.setWasSuccess(false);
            result.setErrorMessage("Invalid page number.");
            return new JsonResponse(Response.Codes.BAD_REQUEST, result);
        }

        final Json rpcResponseJson;
        try (final NodeJsonRpcConnection nodeJsonRpcConnection = environment.getNodeJsonRpcConnection()) {
            if (nodeJsonRpcConnection == null) {
                final BlocksApi.BlockTransactionsResult result = new BlocksApi.BlockTransactionsResult();
                result.setWasSuccess(false);
                result.setErrorMessage("Unable to connect to node.");
                return new JsonResponse(Response.Codes.SERVER_ERROR, result);
            }

            rpcResponseJson = nodeJsonRpcConnection.getBlockTransactions(blockHash, pageSize, pageNumber);
            if (rpcResponseJson == null) {
                return new JsonResponse(Response.Codes.SERVER_ERROR, new BlocksApi.BlockTransactionsResult(false, "Request timed out."));
            }
        }

        if (! rpcResponseJson.getBoolean("wasSuccess")) {
            final String errorMessage = rpcResponseJson.getString("errorMessage");
            return new JsonResponse(Response.Codes.SERVER_ERROR, new BlocksApi.BlockTransactionsResult(false, errorMessage));
        }

        final Json transactionsJson = rpcResponseJson.get("transactions");
        final BlocksApi.BlockTransactionsResult blockTransactionsResult = new BlocksApi.BlockTransactionsResult();
        blockTransactionsResult.setWasSuccess(true);
        blockTransactionsResult.setTransactions(transactionsJson);
        return new JsonResponse(Response.Codes.OK, blockTransactionsResult);
    }
}
