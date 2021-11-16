package com.softwareverde.bitcoin.server.module.explorer.api.v1.get;

import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.DoubleSpendProofsApi;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.http.server.servlet.routed.RequestHandler;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

import java.util.Map;

public class GetDoubleSpendProofHandler implements RequestHandler<Environment> {

    /**
     * GET DOUBLE SPEND PROOF
     * Requires GET:    hash | transactionHash
     * Requires POST:
     */
    @Override
    public Response handleRequest(final Request request, final Environment environment, final Map<String, String> urlParameters) throws Exception {
        final Sha256Hash doubleSpendProofHash;
        {
            final GetParameters getParameters = request.getGetParameters();
            final String doubleSpendProofHashString = Util.coalesce(urlParameters.get("hash"), getParameters.get("hash"));
            if (! Util.isBlank(doubleSpendProofHashString)) {
                doubleSpendProofHash = Sha256Hash.fromHexString(doubleSpendProofHashString);
            }
            else {
                doubleSpendProofHash = null;
            }
        }

        final Sha256Hash transactionHash;
        {
            final GetParameters getParameters = request.getGetParameters();
            final String doubleSpendProofHashString = Util.coalesce(urlParameters.get("transactionHash"), getParameters.get("transactionHash"));
            if (! Util.isBlank(doubleSpendProofHashString)) {
                transactionHash = Sha256Hash.fromHexString(doubleSpendProofHashString);
            }
            else {
                transactionHash = null;
            }
        }

        if ( (doubleSpendProofHash == null) && (transactionHash == null) ) {
            final DoubleSpendProofsApi.DoubleSpendProofResult result = new DoubleSpendProofsApi.DoubleSpendProofResult();
            result.setWasSuccess(false);
            result.setErrorMessage("Missing parameter: hash | transactionHash");
            return new JsonResponse(Response.Codes.BAD_REQUEST, result);
        }

        try (final NodeJsonRpcConnection nodeJsonRpcConnection = environment.getNodeJsonRpcConnection()) {
            if (nodeJsonRpcConnection == null) {
                final DoubleSpendProofsApi.DoubleSpendProofResult result = new DoubleSpendProofsApi.DoubleSpendProofResult();
                result.setWasSuccess(false);
                result.setErrorMessage("Unable to connect to node.");
                return new JsonResponse(Response.Codes.SERVER_ERROR, result);
            }

            if (transactionHash != null) {
                final Json doubleSpendProofsJson;
                {
                    final Json rpcResponseJson = nodeJsonRpcConnection.getTransactionDoubleSpendProofs(transactionHash);
                    if (rpcResponseJson == null) {
                        return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                    }

                    if (! rpcResponseJson.getBoolean("wasSuccess")) {
                        final String errorMessage = rpcResponseJson.getString("errorMessage");
                        return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, errorMessage));
                    }

                    doubleSpendProofsJson = rpcResponseJson.get("doubleSpendProofs");
                }

                final DoubleSpendProofsApi.DoubleSpendProofResult doubleSpendProofResult = new DoubleSpendProofsApi.DoubleSpendProofResult();
                doubleSpendProofResult.setWasSuccess(true);
                doubleSpendProofResult.setDoubleSpendProofsJson(doubleSpendProofsJson);
                return new JsonResponse(Response.Codes.OK, doubleSpendProofResult);
            }

            final Json doubleSpendProofJson;
            {
                final Json rpcResponseJson = nodeJsonRpcConnection.getDoubleSpendProof(doubleSpendProofHash);
                if (rpcResponseJson == null) {
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                }

                if (! rpcResponseJson.getBoolean("wasSuccess")) {
                    final String errorMessage = rpcResponseJson.getString("errorMessage");
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, errorMessage));
                }

                doubleSpendProofJson = rpcResponseJson.get("doubleSpendProof");
            }

            final DoubleSpendProofsApi.DoubleSpendProofResult doubleSpendProofResult = new DoubleSpendProofsApi.DoubleSpendProofResult();
            doubleSpendProofResult.setWasSuccess(true);
            doubleSpendProofResult.setDoubleSpendProofJson(doubleSpendProofJson);
            return new JsonResponse(Response.Codes.OK, doubleSpendProofResult);
        }
    }
}
