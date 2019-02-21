package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.explorer.api.ApiResult;
import com.softwareverde.bitcoin.server.module.stratum.rpc.StratumJsonRpcConnection;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.GetParameters;
import com.softwareverde.servlet.PostParameters;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.JsonResponse;
import com.softwareverde.servlet.response.Response;

import static com.softwareverde.servlet.response.Response.ResponseCodes;

public class PoolApi extends ExplorerApiEndpoint {
    private static class PrototypeBlockResult extends ApiResult {
        private Json _blockJson = new Json();

        public void setBlockJson(final Json blockJson) {
            _blockJson = blockJson;
        }

        @Override
        public Json toJson() {
            final Json json = super.toJson();
            json.put("block", _blockJson);
            return json;
        }
    }

    public PoolApi(final Configuration.ExplorerProperties explorerProperties, final ThreadPool threadPool) {
        super(explorerProperties, threadPool);
    }

    @Override
    protected Response _onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final PostParameters postParameters = request.getPostParameters();

        {   // GET PROTOTYPE BLOCK
            // Requires GET:
            // Requires POST:
            try (final StratumJsonRpcConnection stratumJsonRpcConnection = _getStratumJsonRpcConnection()) {
                if (stratumJsonRpcConnection == null) {
                    final PrototypeBlockResult result = new PrototypeBlockResult();
                    result.setWasSuccess(false);
                    result.setErrorMessage("Unable to connect to stratum node.");
                    return new JsonResponse(ResponseCodes.SERVER_ERROR, result);
                }

                final Json prototypeBlockJson;
                {
                    final Json rpcResponseJson = stratumJsonRpcConnection.getPrototypeBlock(false);
                    if (rpcResponseJson == null) {
                        return new JsonResponse(Response.ResponseCodes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                    }

                    if (! rpcResponseJson.getBoolean("wasSuccess")) {
                        final String errorMessage = rpcResponseJson.getString("errorMessage");
                        return new JsonResponse(Response.ResponseCodes.SERVER_ERROR, new ApiResult(false, errorMessage));
                    }

                    prototypeBlockJson = rpcResponseJson.get("block");
                }

                final PrototypeBlockResult prototypeBlockResult = new PrototypeBlockResult();
                prototypeBlockResult.setWasSuccess(true);
                prototypeBlockResult.setBlockJson(prototypeBlockJson);
                return new JsonResponse(ResponseCodes.OK, prototypeBlockResult);
            }
        }
    }
}
