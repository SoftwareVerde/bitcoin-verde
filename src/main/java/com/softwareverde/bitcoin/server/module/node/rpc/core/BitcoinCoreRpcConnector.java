package com.softwareverde.bitcoin.server.module.node.rpc.core;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.HttpRequest;
import com.softwareverde.http.HttpResponse;
import com.softwareverde.http.server.servlet.request.Headers;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class BitcoinCoreRpcConnector implements BitcoinRpcConnector {
    public static Map<NotificationType, String> getZmqEndpoints(final String host, final Map<NotificationType, Integer> zmqPorts) {
        final HashMap<NotificationType, String> zmqEndpoints = new HashMap<>();
        if (zmqPorts == null) { return zmqEndpoints; }

        final String baseEndpointUri = ("tcp://" + host + ":");
        for (final NotificationType notificationType : NotificationType.values()) {
            final Integer zmqPort = zmqPorts.get(notificationType);
            if (zmqPort == null) { continue; }

            final String endpointUri = (baseEndpointUri + zmqPort);
            zmqEndpoints.put(notificationType, endpointUri);
        }
        return zmqEndpoints;
    }

    protected final AtomicInteger _nextRequestId = new AtomicInteger(1);
    protected final BitcoinNodeAddress _bitcoinNodeAddress;
    protected final RpcCredentials _rpcCredentials;

    protected final Map<NotificationType, ZmqNotificationThread> _zmqNotificationThreads = new HashMap<>();
    protected Map<NotificationType, String> _zmqEndpoints = null;

    protected Map<NotificationType, String> _getZmqEndpoints() {
        final String host = _bitcoinNodeAddress.getHost();
        final String baseEndpointUri = ("tcp://" + host + ":");

        final byte[] requestPayload;
        { // Build request payload
            final Json json = new Json(false);
            json.put("id", _nextRequestId.getAndIncrement());
            json.put("method", "getzmqnotifications");

            { // Method Parameters
                final Json paramsJson = new Json(true);
                json.put("params", paramsJson);
            }

            requestPayload = StringUtil.stringToBytes(json.toString());
        }

        Logger.trace("Attempting to collect ZMQ configuration for node: " + _toString());

        final MutableRequest request = new MutableRequest();
        request.setMethod(HttpMethod.POST);
        request.setRawPostData(requestPayload);

        final HashMap<NotificationType, String> zmqEndpoints = new HashMap<>();
        final Json resultJson;
        {
            final Response response = this.handleRequest(request);
            final String rawResponse = StringUtil.bytesToString(response.getContent());
            if (! Json.isJson(rawResponse)) {
                Logger.debug("Received error from " + _toString() +": " + rawResponse.replaceAll("[\\n\\r]+", "/"));
                return zmqEndpoints;
            }
            final Json responseJson = Json.parse(rawResponse);

            final String errorString = responseJson.getString("error");
            if (! Util.isBlank(errorString)) {
                Logger.debug("Received error from " + _toString() + ": " + errorString);
                return zmqEndpoints;
            }

            resultJson = responseJson.get("result");
        }

        for (int i = 0; i < resultJson.length(); ++i) {
            final Json configJson = resultJson.get(i);
            final String messageTypeString = configJson.getString("type");
            final NotificationType notificationType = ZmqMessageTypeConverter.fromPublishString(messageTypeString);
            final String address = configJson.getString("address");

            final Integer port;
            {
                final int colonIndex = address.lastIndexOf(':');
                if (colonIndex < 0) { continue; }

                final int portBeginIndex = (colonIndex + 1);
                if (portBeginIndex >= address.length()) { continue; }

                port = Util.parseInt(address.substring(portBeginIndex));
            }

            final String endpointUri = (baseEndpointUri + port);
            zmqEndpoints.put(notificationType, endpointUri);
        }

        return zmqEndpoints;
    }

    protected String _toString() {
        return (this.getHost() + ":" + this.getPort());
    }

    public BitcoinCoreRpcConnector(final BitcoinNodeAddress bitcoinNodeAddress) {
        this(bitcoinNodeAddress, null);
    }

    public BitcoinCoreRpcConnector(final BitcoinNodeAddress bitcoinNodeAddress, final RpcCredentials rpcCredentials) {
        _bitcoinNodeAddress = bitcoinNodeAddress;
        _rpcCredentials = rpcCredentials;
    }

    @Override
    public String getHost() {
        return _bitcoinNodeAddress.getHost();
    }

    @Override
    public Integer getPort() {
        return _bitcoinNodeAddress.getPort();
    }

    @Override
    public Monitor getMonitor() {
        return new BitcoinCoreRpcMonitor();
    }

    @Override
    public Response handleRequest(final Request request, final Monitor monitor) {
        final MutableByteArray rawPostData = MutableByteArray.wrap(request.getRawPostData());

        final Integer proxiedResponseCode;
        final ByteArray proxiedResult;
        { // Proxy the request to the target node...
            final HttpRequest webRequest = new HttpRequest();
            webRequest.setUrl("http" + (_bitcoinNodeAddress.isSecure() ? "s" : "") + "://" + _bitcoinNodeAddress.getHost() + ":" + _bitcoinNodeAddress.getPort());
            webRequest.setAllowWebSocketUpgrade(false);
            webRequest.setFollowsRedirects(false);
            webRequest.setValidateSslCertificates(false);
            webRequest.setRequestData(rawPostData);
            webRequest.setMethod(request.getMethod());

            { // Set request headers...
                final Headers headers = request.getHeaders();
                for (final String key : headers.getHeaderNames()) {
                    final Collection<String> values = headers.getHeader(key);
                    for (final String value : values) {
                        webRequest.setHeader(key, value);
                    }
                }

                if (_rpcCredentials != null) {
                    final String key = _rpcCredentials.getAuthorizationHeaderKey();
                    final String value = _rpcCredentials.getAuthorizationHeaderValue();
                    webRequest.setHeader(key, value);
                }
            }

            if (monitor instanceof BitcoinCoreRpcMonitor) {
                ((BitcoinCoreRpcMonitor) monitor).beforeRequestStart(webRequest);
            }

            final HttpResponse proxiedResponse;
            try {
                proxiedResponse = webRequest.execute();
            }
            finally {
                if (monitor instanceof BitcoinCoreRpcMonitor) {
                    ((BitcoinCoreRpcMonitor) monitor).afterRequestEnd();
                }
            }

            proxiedResponseCode = (proxiedResponse != null ? proxiedResponse.getResponseCode() : null);
            proxiedResult = (proxiedResponse != null ? proxiedResponse.getRawResult() : null);
        }

        final Response response = new Response();
        response.setCode(Util.coalesce(proxiedResponseCode, Response.Codes.SERVER_ERROR));
        if (proxiedResult != null) {
            response.setContent(proxiedResult.getBytes());
        }
        else {
            response.setContent(BitcoinCoreUtil.getErrorMessage(proxiedResponseCode));
        }
        return response;
    }

    public BlockTemplate getBlockTemplate() {
        return this.getBlockTemplate(null);
    }

    public BlockTemplate getBlockTemplate(final Monitor monitor) {
        final byte[] requestPayload;
        { // Build request payload
            final Json json = new Json(false);
            json.put("id", _nextRequestId.getAndIncrement());
            json.put("method", "getblocktemplate");

            { // Method Parameters
                final Json paramsJson = new Json(true);
                json.put("params", paramsJson);
            }

            requestPayload = StringUtil.stringToBytes(json.toString());
        }

        final MutableRequest request = new MutableRequest();
        request.setMethod(HttpMethod.POST);
        request.setRawPostData(requestPayload);

        final Json blockTemplateJson;
        {
            final Response response = this.handleRequest(request, monitor);
            final String rawResponse = StringUtil.bytesToString(response.getContent());
            if (! Json.isJson(rawResponse)) {
                Logger.debug("Received error from " + _toString() +": " + rawResponse.replaceAll("[\\n\\r]+", "/"));
                return null;
            }
            final Json responseJson = Json.parse(rawResponse);

            final String errorString = responseJson.getString("error");
            if (! Util.isBlank(errorString)) {
                Logger.debug("Received error from " + _toString() + ": " + errorString);
                return null;
            }

            blockTemplateJson = responseJson.get("result");
        }

        final BlockTemplate blockTemplate = new BlockTemplate();
        final TransactionInflater transactionInflater = new TransactionInflater();

        blockTemplate.setBlockVersion(blockTemplateJson.getLong("version"));
        blockTemplate.setDifficulty(Difficulty.decode(ByteArray.fromHexString(blockTemplateJson.getString("bits"))));
        blockTemplate.setPreviousBlockHash(Sha256Hash.fromHexString(blockTemplateJson.getString("previousblockhash")));
        blockTemplate.setMinimumBlockTime(blockTemplateJson.getLong("mintime"));
        blockTemplate.setNonceRange(ByteArray.fromHexString(blockTemplateJson.getString("noncerange")));

        blockTemplate.setCoinbaseAmount(blockTemplateJson.getLong("coinbasevalue"));
        blockTemplate.setBlockHeight(blockTemplateJson.getLong("height"));

        final Json transactionsJson = blockTemplateJson.get("transactions");
        for (int i = 0; i < transactionsJson.length(); ++i) {
            final Json transactionJson = transactionsJson.get(i);
            final Transaction transaction = transactionInflater.fromBytes(ByteArray.fromHexString(transactionJson.getString("data")));
            final Long fee = transactionJson.getLong("fee");
            final Integer signatureOperationCount = transactionJson.getInteger("sigops");
            blockTemplate.addTransaction(transaction, fee, signatureOperationCount);
        }

        blockTemplate.setCurrentTime(blockTemplateJson.getLong("curtime"));
        blockTemplate.setMaxSignatureOperationCount(blockTemplateJson.getLong("sigoplimit"));
        blockTemplate.setMaxBlockByteCount(blockTemplateJson.getLong("sizelimit"));

        blockTemplate.setTarget(Sha256Hash.fromHexString(blockTemplateJson.getString("target")));
        blockTemplate.setLongPollId(blockTemplateJson.getString("longpollid"));

        final Json coinbaseAuxJson = blockTemplateJson.get("coinbaseaux");
        final String coinbaseAuxFlags = coinbaseAuxJson.getString("flags");
        blockTemplate.setCoinbaseAuxFlags(coinbaseAuxFlags);

        final Json capabilitiesJson = blockTemplateJson.get("capabilities");
        for (int i = 0; i < capabilitiesJson.length(); ++i) {
            final String capability = coinbaseAuxJson.getString(i);
            blockTemplate.addCapability(capability);
        }

        final Json mutableFieldsJson = blockTemplateJson.get("mutable");
        for (int i = 0; i < mutableFieldsJson.length(); ++i) {
            final String mutableField = mutableFieldsJson.getString(i);
            blockTemplate.addMutableField(mutableField);
        }

        return blockTemplate;
    }

    public Boolean submitBlock(final Block block) {
        return this.submitBlock(block, null);
    }

    public Boolean submitBlock(final Block block, final Monitor monitor) {
        final byte[] requestPayload;
        { // Build request payload
            final Json json = new Json(false);
            json.put("id", _nextRequestId.getAndIncrement());
            json.put("method", "submitblock");

            { // Method Parameters
                final BlockDeflater blockDeflater = new BlockDeflater();
                final ByteArray blockData = blockDeflater.toBytes(block);
                final String blockDataHexString = blockData.toString();

                final Json paramsJson = new Json(true);
                paramsJson.add(blockDataHexString.toLowerCase()); // hexdata
                // paramsJson.add(0); // dummy
                json.put("params", paramsJson);
            }

            requestPayload = StringUtil.stringToBytes(json.toString());
        }

        final MutableRequest request = new MutableRequest();
        request.setMethod(HttpMethod.POST);
        request.setRawPostData(requestPayload);

        final Response response = this.handleRequest(request, monitor);
        final String rawResponse = StringUtil.bytesToString(response.getContent());
        if (! Json.isJson(rawResponse)) {
            Logger.debug("Received error from " + _toString() +": " + rawResponse.replaceAll("[\\n\\r]+", "/"));
            return false;
        }
        final Json responseJson = Json.parse(rawResponse);

        final String errorString = responseJson.getString("error");
        if (! Util.isBlank(errorString)) {
            Logger.debug("Received error from " + _toString() + ": " + errorString);
            return false;
        }

        return true;
    }

    public Boolean supportsNotifications() {
        if (_zmqEndpoints == null) {
            _zmqEndpoints = _getZmqEndpoints();
        }

        return (! _zmqEndpoints.isEmpty());
    }

    public Boolean supportsNotification(final NotificationType notificationType) {
        final Map<NotificationType, String> zmqEndpoints = _zmqEndpoints;
        if (zmqEndpoints == null) { return false; }

        return (zmqEndpoints.get(notificationType) != null);
    }

    public void subscribeToNotifications(final NotificationCallback notificationCallback) {
        Map<NotificationType, String> zmqEndpoints = _zmqEndpoints;
        if (zmqEndpoints == null) {
            zmqEndpoints = _getZmqEndpoints();
            _zmqEndpoints = zmqEndpoints;
        }
        if (zmqEndpoints == null) { return; }

        for (final NotificationType notificationType : zmqEndpoints.keySet()) {
            final String endpointUri = zmqEndpoints.get(notificationType);
            final ZmqNotificationThread zmqNotificationThread = new ZmqNotificationThread(notificationType, endpointUri, notificationCallback);
            _zmqNotificationThreads.put(notificationType, zmqNotificationThread);
            zmqNotificationThread.start();
        }
    }

    public void unsubscribeToNotifications() {
        for (final ZmqNotificationThread zmqNotificationThread : _zmqNotificationThreads.values()) {
            zmqNotificationThread.interrupt();
            try { zmqNotificationThread.join(); } catch (final Exception exception) { }
        }
        _zmqNotificationThreads.clear();
    }

    @Override
    public String toString() {
        return _toString();
    }
}
