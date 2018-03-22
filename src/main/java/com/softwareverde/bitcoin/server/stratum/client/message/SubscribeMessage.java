package com.softwareverde.bitcoin.server.stratum.client.message;

import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;

public class SubscribeMessage extends RequestMessage {
    public SubscribeMessage() {
        super(ClientCommand.SUBSCRIBE.getValue());

        _parameters.add("user agent/version");
        _parameters.add(Constants.USER_AGENT);
    }
}
