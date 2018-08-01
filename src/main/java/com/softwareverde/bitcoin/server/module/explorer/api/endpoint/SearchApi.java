package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.explorer.api.ApiResult;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;
import com.softwareverde.servlet.GetParameters;
import com.softwareverde.servlet.PostParameters;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.JsonResponse;
import com.softwareverde.servlet.response.Response;
import com.softwareverde.socket.SocketConnection;

import static com.softwareverde.servlet.response.Response.ResponseCodes;

public class SearchApi extends ExplorerApiEndpoint {
    public static final Long RPC_DURATION_TIMEOUT_MS = 3000L;

    private static class SearchResult extends ApiResult {
        public enum ObjectType {
            BLOCK, TRANSACTION
        }

        private ObjectType _objectType;
        public void setObjectType(final ObjectType objectType) { _objectType = objectType; }

        private Jsonable _object;
        public void setObject(final Jsonable object) { _object = object; }

        @Override
        public Json toJson() {
            final Json json = super.toJson();
            json.put("objectType", _objectType);
            json.put("object", _object);
            return json;
        }
    }

    public SearchApi(final Configuration.ExplorerProperties explorerProperties) {
        super(explorerProperties);
    }

    @Override
    protected Response _onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        {   // SEARCH
            // Requires GET:    hash
            // Requires POST:
            final String objectHash = getParameters.get("hash");
            if (objectHash.isEmpty()) {
                return new JsonResponse(ResponseCodes.BAD_REQUEST, (new ApiResult(false, "Missing Parameter: hash")));
            }

            final SocketConnection socketConnection = _newRpcConnection();

            SearchResult.ObjectType objectType = null;
            Jsonable object = null;
            {
                final Json rpcRequestJson = new Json();
                {
                    final Json rpcParametersJson = new Json();
                    {
                        rpcParametersJson.put("hash", objectHash);
                    }

                    rpcRequestJson.put("method", "GET");
                    rpcRequestJson.put("query", "BLOCK");
                    rpcRequestJson.put("parameters", rpcParametersJson);
                }

                socketConnection.write(rpcRequestJson.toString());
                final String queryBlockResponse = socketConnection.waitForMessage(RPC_DURATION_TIMEOUT_MS);
                if (queryBlockResponse == null) {
                    return new JsonResponse(Response.ResponseCodes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                }

                final Json queryBlockResponseJson = Json.parse(queryBlockResponse);
                if (queryBlockResponseJson.getBoolean("wasSuccess")) {
                    final Json blockJson = queryBlockResponseJson.get("block");
                    object = blockJson;
                    objectType = SearchResult.ObjectType.BLOCK;
                }
                else {
                    rpcRequestJson.put("query", "TRANSACTION");
                    socketConnection.write(rpcRequestJson.toString());
                    final String queryTransactionResponse = socketConnection.waitForMessage(RPC_DURATION_TIMEOUT_MS);
                    if (queryTransactionResponse == null) {
                        return new JsonResponse(Response.ResponseCodes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                    }

                    final Json queryTransactionResponseJson = Json.parse(queryTransactionResponse);
                    if (queryTransactionResponseJson.getBoolean("wasSuccess")) {
                        final Json transactionJson = queryTransactionResponseJson.get("transaction");
                        object = transactionJson;
                        objectType = SearchResult.ObjectType.TRANSACTION;
                    }
                }
            }

            final Boolean wasSuccess = (objectType != null);

            final SearchResult searchResult = new SearchResult();
            searchResult.setWasSuccess(wasSuccess);
            searchResult.setObjectType(objectType);
            searchResult.setObject(object);
            return new JsonResponse(ResponseCodes.OK, searchResult);
        }

        // return new JsonResponse(ResponseCodes.BAD_REQUEST, (new ApiResult(false, "Nothing to do.")));
    }
}
