package com.softwareverde.bitcoin.server.stratum.message.client;

import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.json.Json;

public class AuthorizeMessage extends RequestMessage {
    protected Json _parametersJson = new Json(true);

    public AuthorizeMessage() {
        super(ClientCommand.AUTHORIZE);
    }

    public void setCredentials(final String username, final String password) {
        _parametersJson = new Json(true);
        _parametersJson.add(username);
        _parametersJson.add(password);
    }

    @Override
    protected Json _getParametersJson() {
        return _parametersJson;
    }
}
