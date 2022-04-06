package com.softwareverde.bitcoin.server.stratum.message.client;

import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.json.Json;

public class SubscribeMessage extends RequestMessage {
    protected String _userAgent = "";
    protected ByteArray _subscriptionId = null;

    public SubscribeMessage() {
        super(ClientCommand.SUBSCRIBE);
    }

    public void setUserAgent(final String userAgent) {
        _userAgent = userAgent;
    }

    public void setSubscriptionId(final ByteArray subscriptionId) {
        _subscriptionId = subscriptionId;
    }

    public String getUserAgent() {
        return _userAgent;
    }

    public ByteArray getSubscriptionId() {
        return _subscriptionId;
    }

    @Override
    protected Json _getParametersJson() {
        final Json parametersJson = new Json(true);
        parametersJson.add(_userAgent);
        if (_subscriptionId != null) {
            parametersJson.add(_subscriptionId);
        }
        return parametersJson;
    }
}
