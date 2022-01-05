package com.softwareverde.bitcoin.server.module.electrum;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.network.socket.JsonSocket;

import java.lang.ref.WeakReference;

class ConnectionAddress {
    public final AddressSubscriptionKey subscriptionKey;
    public final WeakReference<JsonSocket> connection;
    public Sha256Hash status;

    public ConnectionAddress(final AddressSubscriptionKey subscriptionKey, final JsonSocket jsonSocket) {
        this.subscriptionKey = subscriptionKey;
        this.connection = new WeakReference<>(jsonSocket);
    }

    @Override
    public String toString() {
        return this.subscriptionKey + ":" + this.status;
    }
}
