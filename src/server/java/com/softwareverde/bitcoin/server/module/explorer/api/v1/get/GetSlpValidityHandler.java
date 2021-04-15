package com.softwareverde.bitcoin.server.module.explorer.api.v1.get;

import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.SlpApi;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.http.server.servlet.routed.RequestHandler;
import com.softwareverde.json.Json;

import java.util.Map;

public class GetSlpValidityHandler implements RequestHandler<Environment> {

    /**
     * VALIDATE
     * Requires GET:
     * Requires POST:
     * Requires URL PARAM: transactionHash
     */
    @Override
    public Response handleRequest(final Request request, final Environment environment, final Map<String, String> parameters) throws Exception {
        final Sha256Hash transactionHash;
        {
            final String transactionHashString = parameters.get("transactionHash");
            transactionHash = Sha256Hash.fromHexString(transactionHashString);
            if (transactionHash == null) {
                final SlpApi.SlpValidityResult result = new SlpApi.SlpValidityResult();
                result.setWasSuccess(false);
                result.setErrorMessage("Unable to connect to node.");
                return new JsonResponse(Response.Codes.SERVER_ERROR, result);
            }
        }

        try (final NodeJsonRpcConnection nodeJsonRpcConnection = environment.getNodeJsonRpcConnection()) {
            if (nodeJsonRpcConnection == null) {
                final SlpApi.SlpValidityResult result = new SlpApi.SlpValidityResult();
                result.setWasSuccess(false);
                result.setErrorMessage("Unable to connect to node.");
                return new JsonResponse(Response.Codes.SERVER_ERROR, result);
            }

            final Boolean isSlpValid;
            {
                final Json rpcResponseJson = nodeJsonRpcConnection.isValidSlpTransaction(transactionHash);
                if (rpcResponseJson == null) {
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                }

                if (! rpcResponseJson.getBoolean("wasSuccess")) {
                    final String errorMessage = rpcResponseJson.getString("errorMessage");
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, errorMessage));
                }

                isSlpValid = rpcResponseJson.getBoolean("isValidSlpTransaction");
            }

            final SlpApi.SlpValidityResult SlpValidityResult = new SlpApi.SlpValidityResult();
            SlpValidityResult.setWasSuccess(true);
            SlpValidityResult.setIsValid(isSlpValid);
            SlpValidityResult.setTransactionHash(transactionHash);
            return new JsonResponse(Response.Codes.OK, SlpValidityResult);
        }
    }
}
