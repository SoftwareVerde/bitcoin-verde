package com.softwareverde.bitcoin.server.stratum.message;

import com.softwareverde.json.Json;

public class MutableRequestMessage extends RequestMessage {
    protected Json _parametersJson = new Json(true);

    protected MutableRequestMessage(final Integer id, final String command) {
        super(id, command);
    }

    public MutableRequestMessage(final ServerCommand command) {
        super(command);
    }

    public MutableRequestMessage(final ClientCommand command) {
        super(command);
    }

    @Override
    protected Json _getParametersJson() {
        return _parametersJson;
    }

    public void setParameters(final Json parametersJson) {
        _parametersJson = parametersJson;
    }
}
