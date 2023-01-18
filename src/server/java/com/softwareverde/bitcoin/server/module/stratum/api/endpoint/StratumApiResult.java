package com.softwareverde.bitcoin.server.module.stratum.api.endpoint;

import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.json.Json;

public class StratumApiResult extends ApiResult {
    protected final MutableMap<String, Object> _values = new MutableHashMap<>();

    public void put(final String key, final Object object) {
        _values.put(key, object);
    }

    public StratumApiResult() { }

    public StratumApiResult(final Boolean wasSuccess, final String errorMessage) {
        super(wasSuccess, errorMessage);
    }

    @Override
    public Json toJson() {
        final Json json = super.toJson();
        for (final String key : _values.getKeys()) {
            final Object value = _values.get(key);
            json.put(key, value);
        }
        return json;
    }
}