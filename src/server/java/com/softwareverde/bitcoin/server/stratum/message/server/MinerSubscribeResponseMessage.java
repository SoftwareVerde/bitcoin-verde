package com.softwareverde.bitcoin.server.stratum.message.server;

import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.bitcoin.server.stratum.message.ResponseMessage;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.json.Json;

public class MinerSubscribeResponseMessage extends ResponseMessage {
    protected ByteArray _subscriptionId;
    protected final MutableList<RequestMessage.ServerCommand> _subscriptions = new MutableList<>();
    protected ByteArray _extraNonce;
    protected Integer _extraNonce2ByteCount;

    public MinerSubscribeResponseMessage(final Integer messageId) {
        super(messageId);
        _result = RESULT_TRUE;
    }

    public MinerSubscribeResponseMessage(final Integer messageId, final Error error) {
        super(messageId);

        _result = (error != null ? RESULT_FALSE : RESULT_TRUE);
        _error = error;
    }

    public ByteArray getSubscriptionId() {
        return _subscriptionId;
    }

    public void setSubscriptionId(final ByteArray byteArray) {
        _subscriptionId = byteArray;
    }

    public MutableList<RequestMessage.ServerCommand> getSubscriptions() {
        return _subscriptions;
    }

    public void addSubscription(final RequestMessage.ServerCommand serverCommand) {
        if (_subscriptions.contains(serverCommand)) { return; }
        _subscriptions.add(serverCommand);
    }

    public ByteArray getExtraNonce() {
        return _extraNonce;
    }

    public void setExtraNonce(final ByteArray extraNonce) {
        _extraNonce = extraNonce;
    }

    public Integer getExtraNonce2ByteCount() {
        return _extraNonce2ByteCount;
    }

    public void setExtraNonce2ByteCount(final Integer extraNonce2ByteCount) {
        _extraNonce2ByteCount = extraNonce2ByteCount;
    }

    @Override
    public Json toJson() {
        final Json json = super.toJson();
        if (_result == RESULT_FALSE) { return json; }

        final Json resultJson = new Json(true);
        {
            final Json subscriptionsJson = new Json(true);
            for (final RequestMessage.ServerCommand serverCommand : _subscriptions) {
                final Json subscriptionJson = new Json(true);
                subscriptionJson.add(serverCommand.getValue());
                subscriptionJson.add(_subscriptionId);

                subscriptionsJson.add(subscriptionJson);
            }
            resultJson.add(subscriptionsJson);
            resultJson.add(_extraNonce);
            resultJson.add(_extraNonce2ByteCount);
        }
        json.put("result", resultJson);

        return json;
    }
}
