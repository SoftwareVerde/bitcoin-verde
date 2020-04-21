package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.configuration.ExplorerProperties;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeJsonRpcConnection;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.JsonResponse;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;

public class StatusApi extends ExplorerApiEndpoint {
    private static class StatusResult extends ApiResult {
        private Json _serverLoad = new Json();
        private Json _statistics = new Json(true);
        private Json _utxoCacheStatus = new Json();
        private Json _serviceStatuses = new Json();
        private String _status;

        public void setServerLoad(final Json serverLoad) {
            _serverLoad = serverLoad;
        }
        public void setStatistics(final Json statistics) {
            _statistics = statistics;
        }
        public void setUtxoCacheStatus(final Json utxoCacheStatus) {
            _utxoCacheStatus = utxoCacheStatus;
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
            json.put("utxoCacheStatus", _utxoCacheStatus);
            json.put("serverLoad", _serverLoad);
            json.put("serviceStatuses", _serviceStatuses);
            return json;
        }
    }

    public StatusApi(final ExplorerProperties explorerProperties, final ThreadPool threadPool) {
        super(explorerProperties, threadPool);
    }

    @Override
    protected Response _onRequest(final Request request) {
        // final GetParameters getParameters = request.getGetParameters();
        // final PostParameters postParameters = request.getPostParameters();

        {   // CHECK STATUS
            // Requires GET:
            // Requires POST:
            try (final NodeJsonRpcConnection nodeJsonRpcConnection = _getNodeJsonRpcConnection()) {
                if (nodeJsonRpcConnection == null) {
                    final StatusResult result = new StatusResult();
                    result.setWasSuccess(false);
                    result.setErrorMessage("Unable to connect to node.");
                    return new JsonResponse(Response.Codes.SERVER_ERROR, result);
                }

                final String status;
                final Json statisticsJson;
                final Json utxoCacheStatusJson;
                final Json serverLoadJson;
                final Json serviceStatusesJson;
                {
                    final Json rpcResponseJson = nodeJsonRpcConnection.getStatus();
                    if (rpcResponseJson == null) {
                        return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, "Request timed out."));
                    }

                    if (! rpcResponseJson.getBoolean("wasSuccess")) {
                        final String errorMessage = rpcResponseJson.getString("errorMessage");
                        return new JsonResponse(Response.Codes.SERVER_ERROR, new ApiResult(false, errorMessage));
                    }

                    statisticsJson = rpcResponseJson.get("statistics");
                    utxoCacheStatusJson = rpcResponseJson.get("utxoCacheStatus");
                    status = rpcResponseJson.getString("status");
                    serverLoadJson = rpcResponseJson.get("serverLoad");
                    serviceStatusesJson = rpcResponseJson.get("serviceStatuses");
                }

                final StatusResult statusResult = new StatusResult();
                statusResult.setWasSuccess(true);
                statusResult.setStatus(status);
                statusResult.setStatistics(statisticsJson);
                statusResult.setUtxoCacheStatus(utxoCacheStatusJson);
                statusResult.setServerLoad(serverLoadJson);
                statusResult.setServiceStatuses(serviceStatusesJson);
                return new JsonResponse(Response.Codes.OK, statusResult);
            }
        }
    }
}
