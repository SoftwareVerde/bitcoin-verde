package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.module.explorer.api.ApiResult;
import com.softwareverde.json.Json;
import com.softwareverde.servlet.request.Request;
import com.softwareverde.servlet.response.JsonResponse;
import com.softwareverde.servlet.response.Response;
import com.softwareverde.socket.SocketConnection;

import static com.softwareverde.servlet.response.Response.ResponseCodes;

public class StatusApi extends ExplorerApiEndpoint {
    public static final Long RPC_DURATION_TIMEOUT_MS = 30000L;

    private static class StatusResult extends ApiResult {
        private Json _statistics = new Json(true);
        private String _status;

        public void setStatistics(final Json statistics) {
            _statistics = statistics;
        }
        public void setStatus(final String status) {
            _status = status;
        }

        @Override
        public Json toJson() {
            final Json json = super.toJson();
            json.put("status", _status);
            json.put("statistics", _statistics);
            return json;
        }
    }

    public StatusApi(final Configuration.ExplorerProperties explorerProperties) {
        super(explorerProperties);
    }

    @Override
    protected Response _onRequest(final Request request) {
        // final GetParameters getParameters = request.getGetParameters();
        // final PostParameters postParameters = request.getPostParameters();

        {   // CHECK STATUS
            // Requires GET:
            // Requires POST:
            final SocketConnection socketConnection = _newRpcConnection();

            final String status;
            final Json statisticsJson;
            {
                final Json rpcRequestJson = new Json();
                {
                    rpcRequestJson.put("method", "GET");
                    rpcRequestJson.put("query", "STATUS");
                }

                socketConnection.write(rpcRequestJson.toString());

                final String rpcResponseString = socketConnection.waitForMessage(RPC_DURATION_TIMEOUT_MS);
                if (rpcResponseString == null) {
                    return new JsonResponse(Response.ResponseCodes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                }

                final Json rpcResponseJson = Json.parse(rpcResponseString);
                if (! rpcResponseJson.getBoolean("wasSuccess")) {
                    final String errorMessage = rpcRequestJson.getString("errorMessage");
                    return new JsonResponse(Response.ResponseCodes.SERVER_ERROR, new ApiResult(false, errorMessage));
                }

                statisticsJson = rpcResponseJson.get("statistics");
                status = rpcResponseJson.getString("status");
            }

            final StatusResult statusResult = new StatusResult();
            statusResult.setWasSuccess(true);
            statusResult.setStatus(status);
            statusResult.setStatistics(statisticsJson);
            return new JsonResponse(ResponseCodes.OK, statusResult);
        }
    }
}
