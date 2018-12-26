package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.explorer.api.ApiResult;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;
import com.softwareverde.servlet.GetParameters;
import com.softwareverde.servlet.PostParameters;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.JsonResponse;
import com.softwareverde.servlet.response.Response;
import com.softwareverde.socket.SocketConnection;
import com.softwareverde.util.Util;

import static com.softwareverde.servlet.response.Response.ResponseCodes;

public class SearchApi extends ExplorerApiEndpoint {
    public static final Long RPC_DURATION_TIMEOUT_MS = 90000L;

    private static class SearchResult extends ApiResult {
        public enum ObjectType {
            BLOCK, BLOCK_HEADER, TRANSACTION, ADDRESS
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
            // Requires GET:    query
            // Requires POST:
            final String queryParam = getParameters.get("query");
            if (queryParam.isEmpty()) {
                return new JsonResponse(ResponseCodes.BAD_REQUEST, (new ApiResult(false, "Missing Parameter: query")));
            }

            try (final SocketConnection socketConnection = _newRpcConnection()) {
                if (socketConnection == null) {
                    final SearchResult result = new SearchResult();
                    result.setWasSuccess(false);
                    return new JsonResponse(ResponseCodes.SERVER_ERROR, result);
                }

                SearchResult.ObjectType objectType = null;
                Jsonable object = null;
                {
                    final int hashCharacterLength = 64;

                    final AddressInflater addressInflater = new AddressInflater();
                    final Address address = addressInflater.fromBase58Check(queryParam);

                    if (address != null) {
                        final Json rpcRequestJson = new Json();
                        {
                            final Json rpcParametersJson = new Json();
                            rpcParametersJson.put("address", address.toBase58CheckEncoded());

                            rpcRequestJson.put("method", "GET");
                            rpcRequestJson.put("query", "ADDRESS");
                            rpcRequestJson.put("parameters", rpcParametersJson);
                        }
                        socketConnection.write(rpcRequestJson.toString());

                        final String queryBlockResponse = socketConnection.waitForMessage(RPC_DURATION_TIMEOUT_MS);
                        if (queryBlockResponse == null) {
                            return new JsonResponse(Response.ResponseCodes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                        }
                        final Json responseJson = Json.parse(queryBlockResponse);
                        if (responseJson.getBoolean("wasSuccess")) {
                            final Json transactionsJson = responseJson.get("transactions");

                            object = transactionsJson;
                            objectType = SearchResult.ObjectType.ADDRESS;
                        }
                    }
                    else if ( (queryParam.startsWith("00000000")) || (queryParam.length() != hashCharacterLength) ) {
                        final Json rpcRequestJson = new Json();
                        {
                            final Json rpcParametersJson = new Json();
                            if (queryParam.length() == hashCharacterLength) {
                                rpcParametersJson.put("hash", queryParam);
                            }
                            else {
                                if (! Util.isLong(queryParam)) {
                                    return new JsonResponse(ResponseCodes.BAD_REQUEST, (new ApiResult(false, "Invalid Parameter Value: " + queryParam)));
                                }
                                rpcParametersJson.put("blockHeight", queryParam);
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

                            final Boolean isFullBlock = (blockJson.get("transactions").length() > 0);

                            object = blockJson;
                            objectType = (isFullBlock ? SearchResult.ObjectType.BLOCK : SearchResult.ObjectType.BLOCK_HEADER);
                        }
                    }

                    if ( (objectType == null) && (queryParam.length() == hashCharacterLength) ) {
                        final Json rpcRequestJson = new Json();
                        {
                            final Json rpcParametersJson = new Json();
                            rpcParametersJson.put("hash", queryParam);

                            rpcRequestJson.put("method", "GET");
                            rpcRequestJson.put("query", "TRANSACTION");
                            rpcRequestJson.put("parameters", rpcParametersJson);
                        }
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
        }

        // return new JsonResponse(ResponseCodes.BAD_REQUEST, (new ApiResult(false, "Nothing to do.")));
    }
}
