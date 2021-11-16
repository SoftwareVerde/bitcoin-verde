package com.softwareverde.bitcoin.server.module.explorer.api.v1.get;

import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.DoubleSpendProofsApi;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.http.server.servlet.routed.RequestHandler;
import com.softwareverde.json.Json;

import java.util.Map;

public class ListDoubleSpendProofsHandler implements RequestHandler<Environment> {

    /**
     * LIST DOUBLE SPEND PROOFS
     * Requires GET:
     * Requires POST:
     */
    @Override
    public Response handleRequest(final Request request, final Environment environment, final Map<String, String> parameters) throws Exception {
        try (final NodeJsonRpcConnection nodeJsonRpcConnection = environment.getNodeJsonRpcConnection()) {
            if (nodeJsonRpcConnection == null) {
                final DoubleSpendProofsApi.DoubleSpendProofResult result = new DoubleSpendProofsApi.DoubleSpendProofResult();
                result.setWasSuccess(false);
                result.setErrorMessage("Unable to connect to node.");
                return new JsonResponse(Response.Codes.SERVER_ERROR, result);
            }

            final Json doubleSpendProofsJson;
            {
                final Json rpcResponseJson = nodeJsonRpcConnection.getDoubleSpendProofs();
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
    }
}
