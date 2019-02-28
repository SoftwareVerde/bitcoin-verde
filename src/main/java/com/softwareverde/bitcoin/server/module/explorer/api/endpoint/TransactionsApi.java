package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.GetParameters;
import com.softwareverde.servlet.PostParameters;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.JsonResponse;
import com.softwareverde.servlet.response.Response;
import com.softwareverde.util.HexUtil;

import static com.softwareverde.servlet.response.Response.ResponseCodes;

public class TransactionsApi extends ExplorerApiEndpoint {
    private static class SubmitTransactionResult extends ApiResult { }

    public TransactionsApi(final Configuration.ExplorerProperties explorerProperties, final ThreadPool threadPool) {
        super(explorerProperties, threadPool);
    }

    @Override
    protected Response _onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        {   // SUBMIT TRANSACTION TO NETWORK
            // Requires GET:
            // Requires POST: transactionData
            try (final NodeJsonRpcConnection nodeJsonRpcConnection = _getNodeJsonRpcConnection()) {
                if (nodeJsonRpcConnection == null) {
                    final SubmitTransactionResult result = new SubmitTransactionResult();
                    result.setWasSuccess(false);
                    result.setErrorMessage("Unable to connect to node.");
                    return new JsonResponse(ResponseCodes.SERVER_ERROR, result);
                }

                {
                    final ByteArray transactionData = (postParameters.containsKey("transactionData") ? MutableByteArray.wrap(HexUtil.hexStringToByteArray(postParameters.get("transactionData"))) : null);
                    if (transactionData == null) {
                        final SubmitTransactionResult result = new SubmitTransactionResult();
                        result.setWasSuccess(false);
                        result.setErrorMessage("Invalid transaction data.");
                        return new JsonResponse(ResponseCodes.BAD_REQUEST, result);
                    }

                    final TransactionInflater transactionInflater = new TransactionInflater();
                    final Transaction transaction = transactionInflater.fromBytes(transactionData);
                    if (transaction == null) {
                        final SubmitTransactionResult result = new SubmitTransactionResult();
                        result.setWasSuccess(false);
                        result.setErrorMessage("Invalid transaction.");
                        return new JsonResponse(ResponseCodes.BAD_REQUEST, result);
                    }

                    final Json rpcResponseJson = nodeJsonRpcConnection.submitTransaction(transaction);
                    if (rpcResponseJson == null) {
                        return new JsonResponse(Response.ResponseCodes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                    }

                    if (! rpcResponseJson.getBoolean("wasSuccess")) {
                        final String errorMessage = rpcResponseJson.getString("errorMessage");
                        return new JsonResponse(Response.ResponseCodes.SERVER_ERROR, new ApiResult(false, errorMessage));
                    }
                }

                final SubmitTransactionResult submitTransactionResult = new SubmitTransactionResult();
                submitTransactionResult.setWasSuccess(true);
                return new JsonResponse(ResponseCodes.OK, submitTransactionResult);
            }
        }
    }
}
