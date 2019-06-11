package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.configuration.ExplorerProperties;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeJsonRpcConnection;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

public class SearchApi extends ExplorerApiEndpoint {
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

    public SearchApi(final ExplorerProperties explorerProperties, final ThreadPool threadPool) {
        super(explorerProperties, threadPool);
    }

    @Override
    protected Response _onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        {   // SEARCH
            // Requires GET:    query
            // Requires POST:
            final String queryParam = getParameters.get("query").trim();
            if (queryParam.isEmpty()) {
                return new JsonResponse(Response.Codes.BAD_REQUEST, (new ApiResult(false, "Missing Parameter: query")));
            }

            try (final NodeJsonRpcConnection nodeJsonRpcConnection = _getNodeJsonRpcConnection()) {
                if (nodeJsonRpcConnection == null) {
                    final SearchResult result = new SearchResult();
                    result.setWasSuccess(false);
                    result.setErrorMessage("Unable to connect to node.");
                    return new JsonResponse(Response.Codes.SERVER_ERROR, result);
                }

                SearchResult.ObjectType objectType = null;
                Jsonable object = null;
                {
                    final int hashCharacterLength = 64;

                    final AddressInflater addressInflater = new AddressInflater();
                    final Address address = addressInflater.fromBase58Check(queryParam);

                    if (address != null) {
                        final Json responseJson = nodeJsonRpcConnection.getAddressTransactions(address);
                        if (responseJson == null) {
                            return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                        }

                        if (responseJson.getBoolean("wasSuccess")) {
                            final Json transactionsJson = responseJson.get("address");

                            object = transactionsJson;
                            objectType = SearchResult.ObjectType.ADDRESS;
                        }
                    }
                    else if ( (queryParam.startsWith("00000000")) || (queryParam.length() != hashCharacterLength) ) {
                        final Json queryBlockResponseJson;
                        if (queryParam.length() == hashCharacterLength) {
                            final Sha256Hash blockHash = Sha256Hash.fromHexString(queryParam);
                            queryBlockResponseJson = nodeJsonRpcConnection.getBlock(blockHash);
                        }
                        else {
                            final Boolean queryParamContainsNonNumeric = (! StringUtil.pregMatch("([^0-9,. ])", queryParam).isEmpty());
                            if ( (! Util.isLong(queryParam)) || (queryParamContainsNonNumeric) ) {
                                return new JsonResponse(Response.Codes.BAD_REQUEST, (new ApiResult(false, "Invalid Parameter Value: " + queryParam)));
                            }
                            final Long blockHeight = Util.parseLong(queryParam);
                            queryBlockResponseJson = nodeJsonRpcConnection.getBlock(blockHeight);
                        }

                        if (queryBlockResponseJson == null) {
                            return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                        }

                        if (queryBlockResponseJson.getBoolean("wasSuccess")) {
                            final Json blockJson = queryBlockResponseJson.get("block");

                            final Boolean isFullBlock = (blockJson.get("transactions").length() > 0);

                            object = blockJson;
                            objectType = (isFullBlock ? SearchResult.ObjectType.BLOCK : SearchResult.ObjectType.BLOCK_HEADER);
                        }
                    }

                    if ( (objectType == null) && (queryParam.length() == hashCharacterLength) ) {
                        final Sha256Hash blockHash = Sha256Hash.fromHexString(queryParam);
                        final Json queryTransactionResponseJson = nodeJsonRpcConnection.getTransaction(blockHash);

                        if (queryTransactionResponseJson == null) {
                            return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                        }

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
                return new JsonResponse(Response.Codes.OK, searchResult);
            }
        }

        // return new JsonResponse(ResponseCodes.BAD_REQUEST, (new ApiResult(false, "Nothing to do.")));
    }
}
