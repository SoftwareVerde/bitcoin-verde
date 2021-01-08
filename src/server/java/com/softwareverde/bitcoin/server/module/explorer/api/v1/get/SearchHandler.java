package com.softwareverde.bitcoin.server.module.explorer.api.v1.get;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.endpoint.SearchApi;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.http.server.servlet.routed.RequestHandler;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

import java.util.Map;

public class SearchHandler implements RequestHandler<Environment> {

    /**
     * SEARCH
     * Requires GET:   query, [rawFormat=0]
     * Requires POST:
     **/
    @Override
    public Response handleRequest(final Request request, final Environment environment, final Map<String, String> parameters) throws Exception {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        final String queryParam = getParameters.get("query").trim();
        if (queryParam.isEmpty()) {
            return new JsonResponse(Response.Codes.BAD_REQUEST, (new ApiResult(false, "Missing Parameter: query")));
        }

        final Boolean rawFormat = (getParameters.containsKey("rawFormat") ? Util.parseBool(getParameters.get("rawFormat")) : null);

        try (final NodeJsonRpcConnection nodeJsonRpcConnection = environment.getNodeJsonRpcConnection()) {
            if (nodeJsonRpcConnection == null) {
                final SearchApi.SearchResult result = new SearchApi.SearchResult();
                result.setWasSuccess(false);
                result.setErrorMessage("Unable to connect to node.");
                return new JsonResponse(Response.Codes.SERVER_ERROR, result);
            }

            SearchApi.SearchResult.ObjectType objectType = null;
            Jsonable object = null;
            String rawObject = null;
            {
                final int hashCharacterLength = 64;

                final AddressInflater addressInflater = new AddressInflater();
                final Address address;
                {
                    final Address base58Address = addressInflater.fromBase58Check(queryParam);
                    if (base58Address != null) {
                        address = base58Address;
                    }
                    else {
                        address = addressInflater.fromBase32Check(queryParam);
                    }
                }

                if (address != null) {
                    final Json responseJson = nodeJsonRpcConnection.getAddressTransactions(address);
                    if (responseJson == null) {
                        return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                    }

                    if (responseJson.getBoolean("wasSuccess")) {
                        final Json transactionsJson = responseJson.get("address");

                        object = transactionsJson;
                        objectType = SearchApi.SearchResult.ObjectType.ADDRESS;
                    }
                }
                else if ( (queryParam.startsWith("00000000")) || (queryParam.length() != hashCharacterLength) ) {
                    final Json queryBlockResponseJson;
                    if (queryParam.length() == hashCharacterLength) {
                        final Sha256Hash blockHash = Sha256Hash.fromHexString(queryParam);
                        if (Util.coalesce(rawFormat)) {
                            queryBlockResponseJson = nodeJsonRpcConnection.getBlock(blockHash, true);
                        }
                        else {
                            queryBlockResponseJson = nodeJsonRpcConnection.getBlockHeader(blockHash, false);
                            if ( (queryBlockResponseJson != null) && queryBlockResponseJson.hasKey("block") ) {
                                final Json blockTransactionsJson;
                                try (final NodeJsonRpcConnection secondNodeJsonRpcConnection = environment.getNodeJsonRpcConnection()) {
                                    if (secondNodeJsonRpcConnection == null) {
                                        final SearchApi.SearchResult result = new SearchApi.SearchResult();
                                        result.setWasSuccess(false);
                                        result.setErrorMessage("Unable to connect to node.");
                                        return new JsonResponse(Response.Codes.SERVER_ERROR, result);
                                    }

                                    final Json queryBlockTransactionsJson = secondNodeJsonRpcConnection.getBlockTransactions(blockHash, 32, 0);
                                    blockTransactionsJson = queryBlockTransactionsJson.get("transactions");
                                }

                                final Json blockJson = queryBlockResponseJson.get("block");
                                blockJson.put("transactions", blockTransactionsJson);
                                queryBlockResponseJson.put("block", blockJson);
                            }
                        }
                    }
                    else {
                        final boolean queryParamContainsNonNumeric = (! StringUtil.pregMatch("([^0-9,. ])", queryParam).isEmpty());
                        if ( (! Util.isLong(queryParam)) || (queryParamContainsNonNumeric) ) {
                            return new JsonResponse(Response.Codes.BAD_REQUEST, (new ApiResult(false, "Invalid Parameter Value: " + queryParam)));
                        }
                        final Long blockHeight = Util.parseLong(queryParam);
                        if (Util.coalesce(rawFormat)) {
                            queryBlockResponseJson = nodeJsonRpcConnection.getBlock(blockHeight, true);
                        }
                        else {
                            queryBlockResponseJson = nodeJsonRpcConnection.getBlockHeader(blockHeight, false);
                            if ( (queryBlockResponseJson != null) && queryBlockResponseJson.hasKey("block") ) {
                                final Json blockTransactionsJson;
                                try (final NodeJsonRpcConnection secondNodeJsonRpcConnection = environment.getNodeJsonRpcConnection()) {
                                    if (secondNodeJsonRpcConnection == null) {
                                        final SearchApi.SearchResult result = new SearchApi.SearchResult();
                                        result.setWasSuccess(false);
                                        result.setErrorMessage("Unable to connect to node.");
                                        return new JsonResponse(Response.Codes.SERVER_ERROR, result);
                                    }

                                    final Json queryBlockTransactionsJson = secondNodeJsonRpcConnection.getBlockTransactions(blockHeight, 32, 0);
                                    blockTransactionsJson = queryBlockTransactionsJson.get("transactions");
                                }

                                final Json blockJson = queryBlockResponseJson.get("block");
                                blockJson.put("transactions", blockTransactionsJson);
                                queryBlockResponseJson.put("block", blockJson);
                            }
                        }
                    }

                    if (queryBlockResponseJson == null) {
                        return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                    }

                    if (queryBlockResponseJson.getBoolean("wasSuccess")) {
                        if (Util.coalesce(rawFormat)) {
                            final String blockHex = queryBlockResponseJson.getString("block");

                            rawObject = blockHex;
                            objectType = SearchApi.SearchResult.ObjectType.RAW_BLOCK;
                        }
                        else {
                            final Json blockJson = queryBlockResponseJson.get("block");
                            final boolean isFullBlock = (blockJson.get("transactions").length() > 0);

                            object = blockJson;
                            objectType = (isFullBlock ? SearchApi.SearchResult.ObjectType.BLOCK : SearchApi.SearchResult.ObjectType.BLOCK_HEADER);
                        }
                    }
                }

                if ( (objectType == null) && (queryParam.length() == hashCharacterLength) ) {
                    final Sha256Hash blockHash = Sha256Hash.fromHexString(queryParam);
                    final Json queryTransactionResponseJson = nodeJsonRpcConnection.getTransaction(blockHash, rawFormat);

                    if (queryTransactionResponseJson == null) {
                        return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                    }

                    if (queryTransactionResponseJson.getBoolean("wasSuccess")) {
                        if (Util.coalesce(rawFormat)) {
                            final String transactionHex = queryTransactionResponseJson.getString("transaction");

                            rawObject = transactionHex;
                            objectType = SearchApi.SearchResult.ObjectType.RAW_TRANSACTION;
                        }
                        else {
                            final Json transactionJson = queryTransactionResponseJson.get("transaction");

                            object = transactionJson;
                            objectType = SearchApi.SearchResult.ObjectType.TRANSACTION;
                        }
                    }
                }
            }
            final Boolean wasSuccess = (objectType != null);

            final SearchApi.SearchResult searchResult = new SearchApi.SearchResult();
            searchResult.setWasSuccess(wasSuccess);
            searchResult.setObjectType(objectType);
            if (rawObject != null) {
                searchResult.setRawObject(rawObject);
            }
            else {
                searchResult.setObject(object);
            }
            return new JsonResponse(Response.Codes.OK, searchResult);
        }
    }
}
