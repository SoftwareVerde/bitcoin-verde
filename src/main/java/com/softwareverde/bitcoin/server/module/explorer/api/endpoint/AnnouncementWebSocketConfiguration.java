package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.list.List;
import com.softwareverde.http.websocket.WebSocket;

public class AnnouncementWebSocketConfiguration {
    public final WebSocket webSocket;
    public Boolean blockHeadersAreEnabled = true;
    public Boolean fullBlockHeaderDataIsEnabled = false;

    public Boolean transactionsAreEnabled = true;
    public Boolean fullTransactionDataIsEnabled = false;

    public Boolean doubleSpendProofsAreEnabled = false;

    public MutableBloomFilter bloomFilter = null;
    public List<Address> addresses = null;

    public AnnouncementWebSocketConfiguration(final WebSocket webSocket) {
        this.webSocket = webSocket;
    }
}
