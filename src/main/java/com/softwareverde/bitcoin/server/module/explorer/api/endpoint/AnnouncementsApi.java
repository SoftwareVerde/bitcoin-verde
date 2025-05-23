package com.softwareverde.bitcoin.server.module.explorer.api.endpoint;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.address.ParsedAddress;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.bloomfilter.BloomFilterInflater;
import com.softwareverde.bitcoin.bloomfilter.UpdateBloomFilterMode;
import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.configuration.ExplorerProperties;
import com.softwareverde.bitcoin.server.module.api.ApiResult;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionBloomFilterMatcher;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.server.servlet.WebSocketServlet;
import com.softwareverde.http.server.servlet.request.WebSocketRequest;
import com.softwareverde.http.server.servlet.response.WebSocketResponse;
import com.softwareverde.http.websocket.WebSocket;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.RotatingQueue;
import com.softwareverde.util.Util;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class AnnouncementsApi implements WebSocketServlet {
    public static class WebSocketApiResult extends ApiResult {
        public static WebSocketApiResult createSuccessResult(final Long requestId) {
            final WebSocketApiResult webSocketApiResult = new WebSocketApiResult();
            webSocketApiResult.setWasSuccess(true);
            webSocketApiResult.addData("requestId", requestId);
            return webSocketApiResult;
        }

        public static WebSocketApiResult createFailureResult(final Long requestId, final String errorMessage) {
            final WebSocketApiResult webSocketApiResult = new WebSocketApiResult();
            webSocketApiResult.setWasSuccess(false);
            webSocketApiResult.setErrorMessage(errorMessage);
            webSocketApiResult.addData("requestId", requestId);
            return webSocketApiResult;
        }

        protected final MutableMap<String, String> _payload = new MutableHashMap<>();

        public void addData(final String key, final String value) {
            _payload.put(key, value);
        }

        public void addData(final String key, final Object value) {
            _payload.put(key, (value != null ? value.toString() : null));
        }

        public void addData(final String key, final Jsonable jsonable) {
            final String value = (jsonable != null ? jsonable.toString() : null);
            _payload.put(key, value);
        }

        @Override
        public Json toJson() {
            final Json json = super.toJson();

            for (final String key : _payload.getKeys()) {
                final String value = _payload.get(key);

                json.put(key, value);
            }

            return json;
        }

        @Override
        public String toString() {
            final Json json = this.toJson();
            return json.toString();
        }
    }

    protected final AddressInflater _addressInflater = new AddressInflater();

    protected static final Object MUTEX = new Object();
    protected static final MutableMap<Long, AnnouncementWebSocketConfiguration> WEB_SOCKETS = new MutableHashMap<>();

    protected static List<AnnouncementWebSocketConfiguration> getWebSockets() {
        synchronized (MUTEX) {
            final MutableList<AnnouncementWebSocketConfiguration> webSockets = new MutableArrayList<>();
            for (final AnnouncementWebSocketConfiguration webSocketConfiguration : WEB_SOCKETS.getValues()) {
                webSockets.add(webSocketConfiguration);
            }
            return webSockets;
        }
    }

    protected static final ReentrantReadWriteLock.ReadLock QUEUE_READ_LOCK;
    protected static final ReentrantReadWriteLock.WriteLock QUEUE_WRITE_LOCK;
    static {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        QUEUE_READ_LOCK = readWriteLock.readLock();
        QUEUE_WRITE_LOCK = readWriteLock.writeLock();
    }

    protected static final RotatingQueue<Json> BLOCK_HEADERS = new RotatingQueue<>(16);
    protected static final RotatingQueue<Json> TRANSACTIONS = new RotatingQueue<>(32);
    protected static final RotatingQueue<Json> DOUBLE_SPEND_PROOFS = new RotatingQueue<>(64);

    protected static class CachedObjects {
        public final List<Json> blockHeaders;
        public final List<Json> transactions;
        public final List<Json> doubleSpendProofs;

        public CachedObjects(final List<Json> blockHeaders, final List<Json> transactions, final List<Json> doubleSpendProofs) {
            this.blockHeaders = blockHeaders;
            this.transactions = transactions;
            this.doubleSpendProofs = doubleSpendProofs;
        }
    }

    protected static CachedObjects getCachedObjects() {
        QUEUE_READ_LOCK.lock();
        try {
            final MutableList<Json> blockHeaders = new MutableArrayList<>();
            for (final Json json : BLOCK_HEADERS) {
                blockHeaders.add(json);
            }

            final MutableList<Json> transactions = new MutableArrayList<>();
            for (final Json json : TRANSACTIONS) {
                transactions.add(json);
            }

            final MutableList<Json> doubleSpendProofs = new MutableArrayList<>();
            for (final Json json : DOUBLE_SPEND_PROOFS) {
                doubleSpendProofs.add(json);
            }

            return new CachedObjects(blockHeaders, transactions, doubleSpendProofs);
        }
        finally {
            QUEUE_READ_LOCK.unlock();
        }
    }

    protected static final AtomicLong _nextSocketId = new AtomicLong(1L);

    protected Thread _rpcConnectionThread;
    protected final Runnable _rpcConnectionThreadRunnable = new Runnable() {
        @Override
        public void run() {
            final Thread thread = Thread.currentThread();

            try {
                while ( (! thread.isInterrupted()) && (! _isShuttingDown) ) {
                    Thread.sleep(500L);

                    _checkRpcConnections();
                }
            }
            catch (final Exception exception) {
                Logger.debug(exception);
            }
            finally {
                synchronized (_rpcConnectionThreadRunnable) {
                    if (_rpcConnectionThread == thread) {
                        _rpcConnectionThread = null;
                    }
                }
            }
        }
    };

    protected Thread _webSocketPingThread;
    protected final Runnable _webSocketPingThreadRunnable = new Runnable() {
        @Override
        public void run() {
            while(true) {
                final Long pingNonce = (long) (Math.random() * Integer.MAX_VALUE);
                final String pingMessage;
                {
                    final Json pingJson = new Json(false);
                    pingJson.put("ping", pingNonce);
                    pingMessage = pingJson.toString();
                }

                final List<AnnouncementWebSocketConfiguration> webSockets = AnnouncementsApi.getWebSockets();
                for (final AnnouncementWebSocketConfiguration webSocketConfiguration : webSockets) {
                    final WebSocket webSocket = webSocketConfiguration.webSocket;
                    webSocket.sendMessage(pingMessage);
                }

                try {
                    Thread.sleep(15000L);
                }
                catch (final InterruptedException exception) {
                    break;
                }
            }
        }
    };

    protected final ExplorerProperties _explorerProperties;
    protected final Object _socketConnectionMutex = new Object();
    protected volatile Boolean _isShuttingDown = false;
    protected NodeJsonRpcConnection _nodeJsonRpcConnection = null;
    protected NodeJsonRpcConnection _rawDataNodeJsonRpcConnection = null;

    protected final NodeJsonRpcConnection.AnnouncementHookCallback _announcementHookCallback = new NodeJsonRpcConnection.AnnouncementHookCallback() {
        @Override
        public void onNewBlockHeader(final Json blockJson) {
            _onNewBlock(blockJson);
        }

        @Override
        public void onNewTransaction(final Json transactionJson) {
            _onNewTransaction(transactionJson);
        }

        @Override
        public void onNewDoubleSpendProof(final Json doubleSpendProofJson) {
            _onDoubleSpendProof(doubleSpendProofJson);
        }
    };

    protected final NodeJsonRpcConnection.RawAnnouncementHookCallback _rawAnnouncementHookCallback = new NodeJsonRpcConnection.RawAnnouncementHookCallback() {
        @Override
        public void onNewBlockHeader(final BlockHeader blockHeader) {
            _onNewBlock(blockHeader);
        }

        @Override
        public void onNewTransaction(final Transaction transaction, final Long transactionFee) {
            _onNewTransaction(transaction);
        }

        @Override
        public void onNewDoubleSpendProof(final DoubleSpendProof doubleSpendProofJson) {
            final Json json = doubleSpendProofJson.toJson();
            _onDoubleSpendProof(json);
        }
    };

    protected Json _wrapObject(final String objectType, final Json object) {
        final Json json = new Json();
        json.put("objectType", objectType);
        json.put("object", object);
        return json;
    }

    protected Json _wrapObject(final String objectType, final Object data) {
        final Json json = new Json();
        json.put("objectType", objectType);
        json.put("object", data);
        return json;
    }

    // NOTE: A light JSON message is sent instead of the whole Transaction Json in order to keep WebSocket._maxPacketByteCount small...
    protected Json _transactionJsonToTransactionHashJson(final Json transactionJson) {
        final Json transactionHashJson = new Json(false);
        transactionHashJson.put("hash", transactionJson.getString("hash"));
        return transactionHashJson;
    }

    protected List<Address> _transactionJsonToAddresses(final Json transactionJson) {
        final MutableList<String> addressStrings = new MutableArrayList<>();

        final Json transactionInputsJson = transactionJson.get("inputs");
        for (int i = 0; i < transactionInputsJson.length(); ++i) {
            final Json inputJson = transactionInputsJson.get(i);
            final String addressString = inputJson.getOrNull("address", Json.Types.STRING);
            final String cashAddressString = inputJson.getOrNull("cashAddress", Json.Types.STRING);

            if (addressString != null) {
                addressStrings.add(addressString);
            }

            if (cashAddressString != null) {
                addressStrings.add(cashAddressString);
            }
        }

        final Json transactionOutputsJson = transactionJson.get("outputs");
        for (int i = 0; i < transactionOutputsJson.length(); ++i) {
            final Json outputJson = transactionOutputsJson.get(i);
            final String addressString = outputJson.getOrNull("address", Json.Types.STRING);
            final String cashAddressString = outputJson.getOrNull("cashAddress", Json.Types.STRING);

            if (addressString != null) {
                addressStrings.add(addressString);
            }

            if (cashAddressString != null) {
                addressStrings.add(cashAddressString);
            }
        }

        final AddressInflater addressInflater = new AddressInflater();
        final MutableList<Address> addresses = new MutableArrayList<>(addressStrings.getCount());
        for (final String addressString : addressStrings) {
            final ParsedAddress address = Util.coalesce(addressInflater.fromBase32Check(addressString), addressInflater.fromBase58Check(addressString));
            if (address == null) { continue; }

            addresses.add(address.getBytes());
        }

        return addresses;
    }

    protected void _checkRpcConnections() {
        _checkRpcConnection();
        _checkRawRpcConnection();
    }

    protected void _checkRpcConnection() {
        { // Lock-less check...
            final NodeJsonRpcConnection rpcConnection = _nodeJsonRpcConnection;
            if ((rpcConnection != null) && (rpcConnection.isConnected())) {
                return;
            }
        }

        synchronized (_socketConnectionMutex) {
            { // Locked, 2nd check...
                final NodeJsonRpcConnection rpcConnection = _nodeJsonRpcConnection;
                if ((rpcConnection != null) && (rpcConnection.isConnected())) {
                    return;
                }

                if (rpcConnection != null) {
                    rpcConnection.close();
                }
            }

            if (_isShuttingDown) { return; }

            final String bitcoinRpcUrl = _explorerProperties.getBitcoinRpcUrl();
            final Integer bitcoinRpcPort = _explorerProperties.getBitcoinRpcPort();
            _nodeJsonRpcConnection = null;

            try {
                final NodeJsonRpcConnection nodeJsonRpcConnection = new NodeJsonRpcConnection(bitcoinRpcUrl, bitcoinRpcPort);
                final Boolean wasSuccessful = nodeJsonRpcConnection.upgradeToAnnouncementHook(_announcementHookCallback);
                if (wasSuccessful) {
                    _nodeJsonRpcConnection = nodeJsonRpcConnection;
                }
            }
            catch (final Exception exception) {
                Logger.warn(exception);
            }
        }
    }

    protected void _checkRawRpcConnection() {
        { // Lock-less check...
            final NodeJsonRpcConnection rpcConnection = _rawDataNodeJsonRpcConnection;
            if ((rpcConnection != null) && (rpcConnection.isConnected())) {
                return;
            }
        }

        synchronized (_socketConnectionMutex) {
            { // Locked, 2nd check...
                final NodeJsonRpcConnection rpcConnection = _rawDataNodeJsonRpcConnection;
                if ((rpcConnection != null) && (rpcConnection.isConnected())) {
                    return;
                }

                if (rpcConnection != null) {
                    rpcConnection.close();
                }
            }

            if (_isShuttingDown) { return; }

            final String bitcoinRpcUrl = _explorerProperties.getBitcoinRpcUrl();
            final Integer bitcoinRpcPort = _explorerProperties.getBitcoinRpcPort();
            _rawDataNodeJsonRpcConnection = null;

            try {
                final NodeJsonRpcConnection nodeJsonRpcConnection = new NodeJsonRpcConnection(bitcoinRpcUrl, bitcoinRpcPort);
                final Boolean wasSuccessful = nodeJsonRpcConnection.upgradeToAnnouncementHook(_rawAnnouncementHookCallback);
                if (wasSuccessful) {
                    _rawDataNodeJsonRpcConnection = nodeJsonRpcConnection;
                }
            }
            catch (final Exception exception) {
                Logger.warn(exception);
            }
        }
    }

    public AnnouncementsApi(final ExplorerProperties explorerProperties) {
        _explorerProperties = explorerProperties;
    }

    protected void _broadcastNewBlockHeader(final Json blockHeaderJson) {
        final String message;
        {
            final Json messageJson = _wrapObject("BLOCK", blockHeaderJson);
            message = messageJson.toString();
        }

        final List<AnnouncementWebSocketConfiguration> webSockets = AnnouncementsApi.getWebSockets();
        for (final AnnouncementWebSocketConfiguration webSocketConfiguration : webSockets) {
            if (! webSocketConfiguration.blockHeadersAreEnabled) { continue; }
            if (webSocketConfiguration.fullBlockHeaderDataIsEnabled) { continue; }

            final WebSocket webSocket = webSocketConfiguration.webSocket;
            webSocket.sendMessage(message);
        }
    }

    protected void _broadcastNewTransaction(final Json transactionJson) {
        final String message;
        {
            final Json trimmedTransactionJson = _transactionJsonToTransactionHashJson(transactionJson);
            final Json messageJson = _wrapObject("TRANSACTION_HASH", trimmedTransactionJson);
            message = messageJson.toString();
        }

        final List<Address> transactionAddresses = _transactionJsonToAddresses(transactionJson);

        final List<AnnouncementWebSocketConfiguration> webSockets = AnnouncementsApi.getWebSockets();
        for (final AnnouncementWebSocketConfiguration webSocketConfiguration : webSockets) {
            if (! webSocketConfiguration.transactionsAreEnabled) { continue; }
            if (webSocketConfiguration.fullTransactionDataIsEnabled) { continue; }

            boolean transactionMatchesFilters = true;
            if (webSocketConfiguration.addresses != null) {
                transactionMatchesFilters = false;

                final List<Address> addresses = webSocketConfiguration.addresses;
                for (final Address address : addresses) {
                    if (transactionAddresses.contains(address)) {
                        transactionMatchesFilters = true;
                        break;
                    }
                }
            }

            if (transactionMatchesFilters) {
                final WebSocket webSocket = webSocketConfiguration.webSocket;
                webSocket.sendMessage(message);
            }
        }
    }

    protected void _broadcastDoubleSpendProof(final Json doubleSpendProofJson) {
        final String message;
        {
            final Json messageJson = _wrapObject("DOUBLE_SPEND_PROOF", doubleSpendProofJson);
            message = messageJson.toString();
        }

        final List<AnnouncementWebSocketConfiguration> webSockets = AnnouncementsApi.getWebSockets();
        for (final AnnouncementWebSocketConfiguration webSocketConfiguration : webSockets) {
            if (! webSocketConfiguration.doubleSpendProofsAreEnabled) { continue; }

            final WebSocket webSocket = webSocketConfiguration.webSocket;
            webSocket.sendMessage(message);
        }
    }

    protected void _broadcastNewBlockHeader(final BlockHeader blockHeader) {
        final String message;
        {
            final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
            final ByteArray blockHeaderBytes = blockHeaderDeflater.toBytes(blockHeader);
            final Json messageJson = _wrapObject("RAW_BLOCK", blockHeaderBytes);
            message = messageJson.toString();
        }

        final List<AnnouncementWebSocketConfiguration> webSockets = AnnouncementsApi.getWebSockets();
        for (final AnnouncementWebSocketConfiguration webSocketConfiguration : webSockets) {
            if (! webSocketConfiguration.blockHeadersAreEnabled) { continue; }
            if (! webSocketConfiguration.fullBlockHeaderDataIsEnabled) { continue; }

            final WebSocket webSocket = webSocketConfiguration.webSocket;
            webSocket.sendMessage(message);
        }
    }

    protected void _broadcastNewTransaction(final Transaction transaction) {
        final String message;
        {
            final TransactionDeflater transactionDeflater = new TransactionDeflater();
            final ByteArray transactionBytes = transactionDeflater.toBytes(transaction);
            final Json messageJson = _wrapObject("RAW_TRANSACTION", transactionBytes);
            message = messageJson.toString();
        }

        final List<Address> transactionAddresses;
        {
            final MutableList<Address> addresses = new MutableArrayList<>();
            final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
            for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
                final LockingScript lockingScript = transactionOutput.getLockingScript();
                final Address address = scriptPatternMatcher.extractAddress(lockingScript);
                if (address != null) {
                    addresses.add(address);
                }
            }
            transactionAddresses = addresses;
        }

        final List<AnnouncementWebSocketConfiguration> webSockets = AnnouncementsApi.getWebSockets();
        for (final AnnouncementWebSocketConfiguration webSocketConfiguration : webSockets) {
            if (! webSocketConfiguration.transactionsAreEnabled) { continue; }
            if (! webSocketConfiguration.fullTransactionDataIsEnabled) { continue; }

            final boolean shouldBroadcastTransaction;
            final MutableBloomFilter bloomFilter = webSocketConfiguration.bloomFilter;
            final List<Address> addresses = webSocketConfiguration.addresses;
            if (bloomFilter != null) {
                final UpdateBloomFilterMode updateBloomFilterMode = Util.coalesce(UpdateBloomFilterMode.valueOf(bloomFilter.getUpdateMode()), UpdateBloomFilterMode.READ_ONLY);
                final TransactionBloomFilterMatcher transactionBloomFilterMatcher = new TransactionBloomFilterMatcher(bloomFilter, updateBloomFilterMode, _addressInflater);
                shouldBroadcastTransaction = transactionBloomFilterMatcher.shouldInclude(transaction);
            }
            else if (addresses != null) {
                boolean transactionMatchesAddressFilter = false;
                for (final Address address : addresses) {
                    if (transactionAddresses.contains(address)) {
                        transactionMatchesAddressFilter = true;
                        break;
                    }
                }
                shouldBroadcastTransaction = transactionMatchesAddressFilter;
            }
            else {
                shouldBroadcastTransaction = true;
            }

            if (shouldBroadcastTransaction) {
                final WebSocket webSocket = webSocketConfiguration.webSocket;
                webSocket.sendMessage(message);
            }
        }
    }

    protected void _onNewBlock(final Json blockJson) {
        try {
            QUEUE_WRITE_LOCK.lock();

            BLOCK_HEADERS.add(blockJson);
        }
        finally {
            QUEUE_WRITE_LOCK.unlock();
        }

        _broadcastNewBlockHeader(blockJson);
    }

    protected void _onNewTransaction(final Json transactionJson) {
        try {
            QUEUE_WRITE_LOCK.lock();

            TRANSACTIONS.add(transactionJson);
        }
        finally {
            QUEUE_WRITE_LOCK.unlock();
        }

        _broadcastNewTransaction(transactionJson);
    }

    protected void _onDoubleSpendProof(final Json doubleSpendProofJson) {
        try {
            QUEUE_WRITE_LOCK.lock();

            Logger.info("DSProof Received: " + doubleSpendProofJson);
            DOUBLE_SPEND_PROOFS.add(doubleSpendProofJson);
        }
        finally {
            QUEUE_WRITE_LOCK.unlock();
        }

        _broadcastDoubleSpendProof(doubleSpendProofJson);
    }

    protected void _onNewBlock(final BlockHeader blockHeader) {
        _broadcastNewBlockHeader(blockHeader);
    }

    protected void _onNewTransaction(final Transaction transaction) {
        _broadcastNewTransaction(transaction);
    }

    protected void _handleWebSocketGet(final AnnouncementWebSocketConfiguration webSocketConfiguration, final Long requestId, final String query, final Json parameters) {
        final WebSocket webSocket = webSocketConfiguration.webSocket;
        final Long webSocketId = webSocket.getId();

        final WebSocketApiResult apiResult = WebSocketApiResult.createFailureResult(requestId, "Unsupported query: " + query);
        Logger.debug("Unknown WebSocket Query: " + query + " (id=" + webSocketId + ")");

        webSocket.sendMessage(apiResult.toString());
    }

    protected void _handleWebSocketPost(final AnnouncementWebSocketConfiguration webSocketConfiguration, final Long requestId, final String query, final Json parameters) {
        final WebSocket webSocket = webSocketConfiguration.webSocket;
        final Long webSocketId = webSocket.getId();

        final WebSocketApiResult apiResult;

        switch (query.toUpperCase()) {
            case "ENABLE_RAW_TRANSACTIONS": {
                webSocketConfiguration.transactionsAreEnabled = true;
                webSocketConfiguration.fullTransactionDataIsEnabled = true;
                apiResult = WebSocketApiResult.createSuccessResult(requestId);
            } break;

            case "DISABLE_RAW_TRANSACTIONS": {
                webSocketConfiguration.fullTransactionDataIsEnabled = false;
                apiResult = WebSocketApiResult.createSuccessResult(requestId);
            } break;

            case "ENABLE_TRANSACTIONS": {
                webSocketConfiguration.transactionsAreEnabled = true;
                apiResult = WebSocketApiResult.createSuccessResult(requestId);
            } break;

            case "DISABLE_TRANSACTIONS": {
                webSocketConfiguration.transactionsAreEnabled = false;
                apiResult = WebSocketApiResult.createSuccessResult(requestId);
            } break;

            case "ENABLE_RAW_BLOCKS": {
                webSocketConfiguration.blockHeadersAreEnabled = true;
                webSocketConfiguration.fullBlockHeaderDataIsEnabled = true;
                apiResult = WebSocketApiResult.createSuccessResult(requestId);
            } break;

            case "DISABLE_RAW_BLOCKS": {
                webSocketConfiguration.fullBlockHeaderDataIsEnabled = false;
                apiResult = WebSocketApiResult.createSuccessResult(requestId);
            } break;

            case "ENABLE_BLOCKS": {
                webSocketConfiguration.blockHeadersAreEnabled = true;
                apiResult = WebSocketApiResult.createSuccessResult(requestId);
            } break;

            case "DISABLE_BLOCKS": {
                webSocketConfiguration.blockHeadersAreEnabled = false;
                apiResult = WebSocketApiResult.createSuccessResult(requestId);
            } break;

            case "ENABLE_DOUBLE_SPEND_PROOFS": {
                webSocketConfiguration.doubleSpendProofsAreEnabled = true;
                apiResult = WebSocketApiResult.createSuccessResult(requestId);
            } break;

            case "DISABLE_DOUBLE_SPEND_PROOFS": {
                webSocketConfiguration.doubleSpendProofsAreEnabled = false;
                apiResult = WebSocketApiResult.createSuccessResult(requestId);
            } break;

            case "SET_BLOOM_FILTER": {
                final BloomFilterInflater bloomFilterInflater = new BloomFilterInflater();
                final String bloomFilterHexString = parameters.getString("bloomFilter");
                final ByteArray bloomFilterBytes = ByteArray.fromHexString(bloomFilterHexString);
                if (bloomFilterBytes == null) {
                    Logger.debug("Invalid Bloom Filter: " + bloomFilterHexString + " (id=" + webSocketId + ")");
                    apiResult = WebSocketApiResult.createFailureResult(requestId, "Invalid bloom filter.");
                    break;
                }

                final MutableBloomFilter bloomFilter = bloomFilterInflater.fromBytes(bloomFilterBytes);
                if (bloomFilter == null) {
                    Logger.debug("Invalid Bloom Filter: " + bloomFilterHexString + " (id=" + webSocketId + ")");
                    apiResult = WebSocketApiResult.createFailureResult(requestId, "Invalid bloom filter.");
                    break;
                }

                webSocketConfiguration.bloomFilter = bloomFilter;
                apiResult = WebSocketApiResult.createSuccessResult(requestId);
            } break;

            case "SET_ADDRESSES": {
                final AddressInflater addressInflater = new AddressInflater();
                final MutableList<Address> addresses = new MutableArrayList<>();
                for (int i = 0; i < parameters.length(); ++i) {
                    final String addressString = parameters.getString(i);
                    final ParsedAddress address = Util.coalesce(addressInflater.fromBase32Check(addressString), addressInflater.fromBase58Check(addressString));
                    if (address == null) { continue; }

                    addresses.add(address.getBytes());
                }

                webSocketConfiguration.addresses = ((! addresses.isEmpty()) ? addresses : null);
                apiResult = WebSocketApiResult.createSuccessResult(requestId);
            } break;

            default: {
                apiResult = WebSocketApiResult.createFailureResult(requestId, "Unsupported query: " + query);
                Logger.debug("Unknown WebSocket Query: " + query + " (id=" + webSocketId + ")");
            } break;
        }

        webSocket.sendMessage(apiResult.toString());
    }

    @Override
    public WebSocketResponse onRequest(final WebSocketRequest webSocketRequest) {
        final WebSocketResponse webSocketResponse = new WebSocketResponse();
        if (! _isShuttingDown) {
            final Long webSocketId = _nextSocketId.getAndIncrement();
            webSocketResponse.setWebSocketId(webSocketId);
            webSocketResponse.upgradeToWebSocket();
        }
        return webSocketResponse;
    }

    @Override
    public void onNewWebSocket(final WebSocket webSocket) {
        if (_isShuttingDown) {
            webSocket.close();
            return;
        }

        _checkRpcConnections();

        final AnnouncementWebSocketConfiguration webSocketConfiguration = new AnnouncementWebSocketConfiguration(webSocket);

        final Long webSocketId = webSocket.getId();

        webSocket.setMessageReceivedCallback(new WebSocket.MessageReceivedCallback() {
            @Override
            public void onMessage(final String message) {
                final Json json = Json.parse(message);
                if (json.hasKey("pong")) {
                    final Long pingNonce = json.getLong("pong");
                    Logger.trace("Received pong (" + pingNonce + ") from: " + webSocket.getId());
                    return;
                }
                else if (json.hasKey("ping")) {
                    final Long pingNonce = json.getLong("ping");
                    Logger.trace("Received ping (" + pingNonce + ") from: " + webSocket.getId());

                    final String pongMessage;
                    {
                        final Json pongJson = new Json(false);
                        pongJson.put("pong", pingNonce);
                        pongMessage = pongJson.toString();
                    }

                    webSocket.sendMessage(pongMessage);
                    return;
                }

                final String methodString = (json.hasKey("method") ? json.getString("method") : "");
                final HttpMethod method = HttpMethod.fromString(methodString);
                final String query = json.getString("query");
                final Json parameters = json.get("parameters");
                final Long requestId = json.getLong("requestId");

                if (method != null) {
                    switch (method) {
                        case GET: {
                            _handleWebSocketGet(webSocketConfiguration, requestId, query, parameters);
                            return;
                        }

                        case POST: {
                            _handleWebSocketPost(webSocketConfiguration, requestId, query, parameters);
                            return;
                        }
                    }
                }

                Logger.debug("Unknown WebSocket Method: " + methodString + " (id=" + webSocketId + ")");
                final WebSocketApiResult apiResult = WebSocketApiResult.createFailureResult(requestId, "Unknown Method: " + methodString);
                webSocket.sendMessage(apiResult.toString());
            }
        });

        webSocket.setConnectionClosedCallback(new WebSocket.ConnectionClosedCallback() {
            @Override
            public void onClose(final int code, final String message) {
                synchronized (MUTEX) {
                    Logger.debug("WebSocket Closed: " + webSocketId + " (count=" + (WEB_SOCKETS.getCount() - 1) + ")");
                    WEB_SOCKETS.remove(webSocketId);
                }
            }
        });

        webSocket.startListening();

        synchronized (MUTEX) {
            Logger.debug("Adding WebSocket: " + webSocketId + " (count=" + (WEB_SOCKETS.getCount() + 1) + ")");
            WEB_SOCKETS.put(webSocketId, webSocketConfiguration);
        }

        final CachedObjects objects = AnnouncementsApi.getCachedObjects();

        for (final Json blockHeaderJson : objects.blockHeaders) {
            final Json messageJson = _wrapObject("BLOCK", blockHeaderJson);
            final String message = messageJson.toString();
            webSocket.sendMessage(message);
        }

        for (final Json transactionJson : objects.transactions) {
            final Json trimmedTransactionJson = _transactionJsonToTransactionHashJson(transactionJson);
            final Json messageJson = _wrapObject("TRANSACTION_HASH", trimmedTransactionJson);
            final String message = messageJson.toString();
            webSocket.sendMessage(message);
        }

        for (final Json blockHeaderJson : objects.doubleSpendProofs) {
            final Json messageJson = _wrapObject("DOUBLE_SPEND_PROOF", blockHeaderJson);
            final String message = messageJson.toString();
            webSocket.sendMessage(message);
        }
    }

    public void start() {
        synchronized (_rpcConnectionThreadRunnable) {
            final Thread existingRpcConnectionThread = _rpcConnectionThread;
            if (existingRpcConnectionThread != null) {
                existingRpcConnectionThread.interrupt();
            }

            final Thread rpcConnectionThread = new Thread(_rpcConnectionThreadRunnable);
            rpcConnectionThread.setName("RPC Connection Monitor");
            rpcConnectionThread.setDaemon(true);
            rpcConnectionThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(final Thread thread, final Throwable exception) {
                    Logger.warn(exception);
                }
            });
            _rpcConnectionThread = rpcConnectionThread;
            rpcConnectionThread.start();
        }

        synchronized (_webSocketPingThreadRunnable) {
            final Thread existingWebSocketPingThread = _webSocketPingThread;
            if (existingWebSocketPingThread != null) {
                existingWebSocketPingThread.interrupt();
            }

            final Thread webSocketPingThread = new Thread(_webSocketPingThreadRunnable);
            webSocketPingThread.setName("WebSocket Ping");
            webSocketPingThread.setDaemon(true);
            webSocketPingThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(final Thread thread, final Throwable exception) {
                    Logger.warn(exception);
                }
            });
            _webSocketPingThread = webSocketPingThread;
            _webSocketPingThread.start();
        }
    }

    public void stop() {
        _isShuttingDown = true;

        synchronized (_rpcConnectionThreadRunnable) {
            final Thread rpcConnectionThread = _rpcConnectionThread;
            if (rpcConnectionThread != null) {
                rpcConnectionThread.interrupt();
                try { rpcConnectionThread.join(5000L); } catch (final Exception exception) { }
            }
        }

        synchronized (_webSocketPingThreadRunnable) {
            final Thread webSocketPingThread = _webSocketPingThread;
            if (webSocketPingThread != null) {
                webSocketPingThread.interrupt();
                try { webSocketPingThread.join(5000L); } catch (final Exception exception) { }
            }
        }

        synchronized (_socketConnectionMutex) {
            final NodeJsonRpcConnection rpcConnection = _nodeJsonRpcConnection;
            if (rpcConnection != null) {
                rpcConnection.close();
            }
        }

        synchronized (MUTEX) {
            for (final AnnouncementWebSocketConfiguration webSocketConfiguration : WEB_SOCKETS.getValues()) {
                final WebSocket webSocket = webSocketConfiguration.webSocket;
                webSocket.close();
            }
            WEB_SOCKETS.clear();
        }
    }
}
