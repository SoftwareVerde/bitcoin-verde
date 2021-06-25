package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.http.websocket.WebSocket;

public class AnnouncementWebSocketConfiguration {
    public final WebSocket webSocket;
    public Boolean blockHeadersAreEnabled = true;
    public Boolean fullBlockHeaderDataIsEnabled = false;

    public Boolean transactionsAreEnabled = true;
    public Boolean fullTransactionDataIsEnabled = false;

    public MutableBloomFilter bloomFilter = null;

    public AnnouncementWebSocketConfiguration(final WebSocket webSocket) {
        this.webSocket = webSocket;
    }
}
