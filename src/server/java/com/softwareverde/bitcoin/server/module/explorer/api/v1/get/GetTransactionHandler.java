package com.softwareverde.bitcoin.server.module.explorer.api.v1.get;

import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.TransactionsApi;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.http.server.servlet.routed.RequestHandler;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

import java.util.Map;

public class GetTransactionHandler implements RequestHandler<Environment> {

    /**
     * GET TRANSACTION
     * Requires GET:    hash, <rawFormat>
     * Requires POST:
     */
    @Override
    public Response handleRequest(final Request request, final Environment environment, final Map<String, String> urlParameters) throws Exception {
        final String transactionHashString;
        final Sha256Hash transactionHash;
        {
            final GetParameters getParameters = request.getGetParameters();
            transactionHashString = Util.coalesce(urlParameters.get("hash"), getParameters.get("hash"));
            if (! Util.isBlank(transactionHashString)) {
                transactionHash = Sha256Hash.fromHexString(transactionHashString);
            }
            else {
                transactionHash = null;
            }
        }

        final Boolean rawFormat;
        {
            final GetParameters getParameters = request.getGetParameters();
            final String rawFormatString = Util.coalesce(urlParameters.get("rawFormat"), getParameters.get("rawFormat"));
            if (! Util.isBlank(rawFormatString)) {
                rawFormat = Util.parseBool(rawFormatString);
            }
            else {
                rawFormat = false;
            }
        }

        if (transactionHash == null) {
            final TransactionsApi.GetTransactionResult result = new TransactionsApi.GetTransactionResult();
            result.setWasSuccess(false);
            result.setErrorMessage("Invalid hash parameter: " + transactionHashString);
            return new JsonResponse(Response.Codes.BAD_REQUEST, result);
        }

        try (final NodeJsonRpcConnection nodeJsonRpcConnection = environment.getNodeJsonRpcConnection()) {
            if (nodeJsonRpcConnection == null) {
                final TransactionsApi.GetTransactionResult result = new TransactionsApi.GetTransactionResult();
                result.setWasSuccess(false);
                result.setErrorMessage("Unable to connect to node.");
                return new JsonResponse(Response.Codes.SERVER_ERROR, result);
            }

            final Json transactionJson;
            {
                final Json rpcResponseJson = nodeJsonRpcConnection.getTransaction(transactionHash, rawFormat);
                if (rpcResponseJson == null) {
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                }

                if (! rpcResponseJson.getBoolean("wasSuccess")) {
                    final String errorMessage = rpcResponseJson.getString("errorMessage");
                    return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, errorMessage));
                }

                transactionJson = rpcResponseJson.get("transaction");
            }

            final TransactionsApi.GetTransactionResult getTransactionResult = new TransactionsApi.GetTransactionResult();
            getTransactionResult.setWasSuccess(true);
            getTransactionResult.setTransactionJson(transactionJson);
            return new JsonResponse(Response.Codes.OK, transactionJson);
        }
    }
}
