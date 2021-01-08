package com.softwareverde.bitcoin.server.module.explorer.api.v1.post;

import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.TransactionsApi;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.http.server.servlet.routed.RequestHandler;
import com.softwareverde.json.Json;
import com.softwareverde.util.HexUtil;

import java.util.Map;

public class SubmitTransactionHandler implements RequestHandler<Environment> {

    /**
     * SUBMIT TRANSACTION TO NETWORK
     * Requires GET:
     * Requires POST: transactionData
     */
    @Override
    public Response handleRequest(final Request request, final Environment environment, final Map<String, String> parameters) throws Exception {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        final ByteArray transactionData = (postParameters.containsKey("transactionData") ? MutableByteArray.wrap(HexUtil.hexStringToByteArray(postParameters.get("transactionData"))) : null);
        if (transactionData == null) {
            final TransactionsApi.SubmitTransactionResult result = new TransactionsApi.SubmitTransactionResult();
            result.setWasSuccess(false);
            result.setErrorMessage("Invalid transaction data.");
            return new JsonResponse(Response.Codes.BAD_REQUEST, result);
        }

        final TransactionInflater transactionInflater = new TransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(transactionData);
        if (transaction == null) {
            final TransactionsApi.SubmitTransactionResult result = new TransactionsApi.SubmitTransactionResult();
            result.setWasSuccess(false);
            result.setErrorMessage("Invalid transaction.");
            return new JsonResponse(Response.Codes.BAD_REQUEST, result);
        }

        final Json rpcResponseJson;
        try (final NodeJsonRpcConnection nodeJsonRpcConnection = environment.getNodeJsonRpcConnection()) {
            if (nodeJsonRpcConnection == null) {
                final TransactionsApi.SubmitTransactionResult result = new TransactionsApi.SubmitTransactionResult();
                result.setWasSuccess(false);
                result.setErrorMessage("Unable to connect to node.");
                return new JsonResponse(Response.Codes.SERVER_ERROR, result);
            }

            rpcResponseJson = nodeJsonRpcConnection.submitTransaction(transaction);
            if (rpcResponseJson == null) {
                return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
            }
        }

        if (! rpcResponseJson.getBoolean("wasSuccess")) {
            final String errorMessage = rpcResponseJson.getString("errorMessage");
            return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, errorMessage));
        }

        final TransactionsApi.SubmitTransactionResult submitTransactionResult = new TransactionsApi.SubmitTransactionResult();
        submitTransactionResult.setWasSuccess(true);
        return new JsonResponse(Response.Codes.OK, submitTransactionResult);
    }
}
