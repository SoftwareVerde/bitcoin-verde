package com.softwareverde.bitcoin.server.stratum.message.client;

import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;

public class AuthorizeMessage extends RequestMessage {
    public AuthorizeMessage() {
        super(ClientCommand.AUTHORIZE.getValue());
    }

    public void setCredentials(final String username, final String password) {
        _parameters.add(username);
        _parameters.add(password);
    }
}
