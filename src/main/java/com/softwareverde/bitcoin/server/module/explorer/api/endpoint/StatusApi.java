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
        private Json _serverLoad = new Json();
        private Json _statistics = new Json(true);
        private Json _serviceStatuses = new Json();
        private String _status;

        public void setServerLoad(final Json serverLoad) {
            _serverLoad = serverLoad;
        }
        public void setStatistics(final Json statistics) {
            _statistics = statistics;
        }
        public void setServiceStatuses(final Json serviceStatuses) {
            _serviceStatuses = serviceStatuses;
        }
        public void setStatus(final String status) {
            _status = status;
        }

        @Override
        public Json toJson() {
            final Json json = super.toJson();
            json.put("status", _status);
            json.put("statistics", _statistics);
            json.put("serverLoad", _serverLoad);
            json.put("serviceStatuses", _serviceStatuses);
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
            if (socketConnection == null) {
                final StatusResult result = new StatusResult();
                result.setWasSuccess(false);
                return new JsonResponse(ResponseCodes.SERVER_ERROR, result);
            }

            final String status;
            final Json statisticsJson;
            final Json serverLoadJson;
            final Json serviceStatusesJson;
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
                serverLoadJson = rpcResponseJson.get("serverLoad");
                serviceStatusesJson = rpcResponseJson.get("serviceStatuses");
            }

            final StatusResult statusResult = new StatusResult();
            statusResult.setWasSuccess(true);
            statusResult.setStatus(status);
            statusResult.setStatistics(statisticsJson);
            statusResult.setServerLoad(serverLoadJson);
            statusResult.setServiceStatuses(serviceStatusesJson);
            return new JsonResponse(ResponseCodes.OK, statusResult);
        }
    }
}
