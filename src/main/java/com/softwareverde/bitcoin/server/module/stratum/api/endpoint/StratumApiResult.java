package com.softwareverde.bitcoin.server.module.stratum.api.endpoint;

import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.json.Json;

import java.util.HashMap;

public class StratumApiResult extends ApiResult {
    private HashMap<String, Object> _values = new HashMap<String, Object>();

    public void put(final String key, final Object object) {
        _values.put(key, object);
    }

    public StratumApiResult() {}

    public StratumApiResult(final Boolean wasSuccess, final String errorMessage) {
        super(wasSuccess, errorMessage);
    }

    @Override
    public Json toJson() {
        final Json json = super.toJson();
        for (final String key : _values.keySet()) {
            final Object value = _values.get(key);
            json.put(key, value);
        }
        return json;
    }
}