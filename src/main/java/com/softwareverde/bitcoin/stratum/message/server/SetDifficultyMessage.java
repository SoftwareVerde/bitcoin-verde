package com.softwareverde.bitcoin.server.stratum.message.server;

import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.json.Json;

public class SetDifficultyMessage extends RequestMessage {
    protected Long _shareDifficulty = 2048L;

    public SetDifficultyMessage() {
        super(ServerCommand.SET_DIFFICULTY);
    }

    public void setShareDifficulty(final Long shareDifficulty) {
        _shareDifficulty = shareDifficulty;
    }

    @Override
    protected Json _getParametersJson() {
        final Json parametersJson = new Json(true);
        parametersJson.add(_shareDifficulty);
        return parametersJson;
    }
}
