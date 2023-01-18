package com.softwareverde.bitcoin.server.module.electrum.json;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.map.Map;
import com.softwareverde.json.Json;
import com.softwareverde.util.Tuple;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;


public class ElectrumJson extends Json {
    public ElectrumJson(final JSONObject jsonObject) {
        super(jsonObject);
    }

    public ElectrumJson(final JSONArray jsonArray) {
        super(jsonArray);
    }

    public ElectrumJson() { }

    public ElectrumJson(final Boolean isArray) {
        super(isArray);
    }

    public <T> ElectrumJson(final Collection<T> c) {
        super(c);
    }

    public <T> ElectrumJson(final java.util.Map<String, T> keyValueMap) {
        super(keyValueMap);
    }

    public <T> ElectrumJson(final Map<String, T> keyValueMap) {
        super(new java.util.HashMap<String, T>() {{
            for (final Tuple<String, T> entry : keyValueMap) {
                this.put(entry.first, entry.second);
            }
        }});
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
