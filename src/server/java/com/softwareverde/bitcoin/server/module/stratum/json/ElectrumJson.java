package com.softwareverde.bitcoin.server.module.stratum.json;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.json.Json;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Map;

public class ElectrumJson extends Json {
    protected void _defineJsonRpc() {
        this.put("jsonrpc", "2.0");
    }

    public ElectrumJson(final JSONObject jsonObject) {
        super(jsonObject);

        _defineJsonRpc();
    }

    public ElectrumJson(final JSONArray jsonArray) {
        super(jsonArray);
    }

    public ElectrumJson() { }

    public ElectrumJson(final Boolean isArray) {
        super(isArray);

        if (! isArray) {
            _defineJsonRpc();
        }
    }

    public <T> ElectrumJson(final Collection<T> c) {
        super(c);
    }

    public <T> ElectrumJson(final Map<String, T> keyValueMap) {
        super(keyValueMap);

        _defineJsonRpc();
    }

    @Override
    public <T> void add(final T value) {
        if (value instanceof ByteArray) {
            final String hexString = value.toString();
            super.add(hexString.toLowerCase());
        }
        else {
            super.add(value);
        }
    }

    @Override
    public <T> void put(final String key, final T value) {
        if (value instanceof ByteArray) {
            final String hexString = value.toString();
            super.put(key, hexString.toLowerCase());
        }
        else {
            super.put(key, value);
        }
    }
}
