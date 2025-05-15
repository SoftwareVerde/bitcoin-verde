package com.softwareverde.bitcoin.server.module.electrum.json;

import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;
import com.softwareverde.network.socket.JsonProtocolMessage;

public class ElectrumJsonProtocolMessage extends JsonProtocolMessage {
    protected void _defineJsonRpc() {
        _message.put("jsonrpc", "2.0");
    }

    public ElectrumJsonProtocolMessage(final Json json) {
        super(json);

        if (! _message.isArray()) {
            _defineJsonRpc();
        }
    }

    public ElectrumJsonProtocolMessage(final Jsonable jsonable) {
        super(jsonable);

        if (! _message.isArray()) {
            _defineJsonRpc();
        }
    }

    public Json getMessage() {
        return _message;
    }
}
