package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.server.module.explorer.api.Environment;
import com.softwareverde.bitcoin.server.module.explorer.api.v1.get.GetStatusHandler;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.json.Json;

public class StatusApi extends ExplorerApiEndpoint {
    public static class StatusResult extends ApiResult {
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

    public StatusApi(final String apiPrePath, final Environment environment) {
        super(environment);

        _defineEndpoint((apiPrePath + "/status"), HttpMethod.GET, new GetStatusHandler());
    }
}
