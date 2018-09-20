package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.explorer.api.ApiResult;
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
    public static final Long RPC_DURATION_TIMEOUT_MS = 90000L;

    private static class SearchResult extends ApiResult {
        public enum ObjectType {
            BLOCK, BLOCK_HEADER, TRANSACTION
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

            final Object lock = new Object();
            socketConnection.setMessageReceivedCallback(new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }
            });

            SearchResult.ObjectType objectType = null;
            Jsonable object = null;
            {
                final Json rpcRequestJson = new Json();
                {
                    final Json rpcParametersJson = new Json();
                    rpcParametersJson.put("hash", objectHash);

                    rpcRequestJson.put("method", "GET");
                    rpcRequestJson.put("parameters", rpcParametersJson);
                }

                if (objectHash.startsWith("00000000")) {
                    rpcRequestJson.put("query", SearchResult.ObjectType.BLOCK);
                    socketConnection.write(rpcRequestJson.toString());

                    synchronized (lock) {
                        try { lock.wait(RPC_DURATION_TIMEOUT_MS); } catch (final InterruptedException exception) { }
                    }

                    final String queryBlockResponse = socketConnection.popMessage();
                    if (queryBlockResponse == null) {
                        return new JsonResponse(Response.ResponseCodes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                    }
                    final Json queryBlockResponseJson = Json.parse(queryBlockResponse);
                    if (queryBlockResponseJson.getBoolean("wasSuccess")) {
                        final Json blockJson = queryBlockResponseJson.get("block");

                        final Boolean isFullBlock = (blockJson.get("transactions").length() > 0);

                        object = blockJson;
                        if (isFullBlock) {
                            objectType = SearchResult.ObjectType.BLOCK;
                        }
                        else {
                            objectType = SearchResult.ObjectType.BLOCK_HEADER;
                        }
                    }
                }

                if (objectType == null) {
                    rpcRequestJson.put("query", SearchResult.ObjectType.TRANSACTION);
                    socketConnection.write(rpcRequestJson.toString());

                    synchronized (lock) {
                        try { lock.wait(RPC_DURATION_TIMEOUT_MS); } catch (final InterruptedException exception) { }
                    }

                    final String queryTransactionResponse = socketConnection.popMessage();
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
