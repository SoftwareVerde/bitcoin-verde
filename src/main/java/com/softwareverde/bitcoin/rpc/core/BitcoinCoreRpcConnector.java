package com.softwareverde.bitcoin.rpc.core;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.rpc.BitcoinMiningRpcConnector;
import com.softwareverde.bitcoin.rpc.BitcoinNodeRpcAddress;
import com.softwareverde.bitcoin.rpc.BlockTemplate;
import com.softwareverde.bitcoin.rpc.MutableBlockTemplate;
import com.softwareverde.bitcoin.rpc.RpcCredentials;
import com.softwareverde.bitcoin.rpc.RpcNotificationCallback;
import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.bitcoin.rpc.core.zmq.ZmqMessageTypeConverter;
import com.softwareverde.bitcoin.rpc.core.zmq.ZmqNotificationThread;
import com.softwareverde.bitcoin.rpc.monitor.Monitor;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
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
import com.softwareverde.util.Container;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class BitcoinCoreRpcConnector implements BitcoinMiningRpcConnector {
    public static Map<RpcNotificationType, String> getZmqEndpoints(final String host, final Map<RpcNotificationType, Integer> zmqPorts) {
        final HashMap<RpcNotificationType, String> zmqEndpoints = new HashMap<>();
        if (zmqPorts == null) { return zmqEndpoints; }

        final String baseEndpointUri = ("tcp://" + host + ":");
        for (final RpcNotificationType rpcNotificationType : RpcNotificationType.values()) {
            final Integer zmqPort = zmqPorts.get(rpcNotificationType);
            if (zmqPort == null) { continue; }

            final String endpointUri = (baseEndpointUri + zmqPort);
            zmqEndpoints.put(rpcNotificationType, endpointUri);
        }
        return zmqEndpoints;
    }

    public static Boolean isSuccessfulResponse(final Response response, final Json preParsedResponse, final Container<String> errorStringContainer) {
        errorStringContainer.value = null;
        if (response == null) { return false; }

        if (! Util.areEqual(Response.Codes.OK, response.getCode())) {
            return false;
        }

        final Json responseJson;
        if (preParsedResponse != null) {
            responseJson = preParsedResponse;
        }
        else {
            final String rawResponse = StringUtil.bytesToString(response.getContent());
            responseJson = Json.parse(rawResponse);
        }

        final String errorString = responseJson.getString("error");
        if (! Util.isBlank(errorString)) {
            errorStringContainer.value = errorString;
            return false;
        }

        return true;
    }

    public static final String IDENTIFIER = "CORE";

    protected final AtomicInteger _nextRequestId = new AtomicInteger(1);
    protected final BitcoinNodeRpcAddress _bitcoinNodeRpcAddress;
    protected final RpcCredentials _rpcCredentials;

    protected final Map<RpcNotificationType, ZmqNotificationThread> _zmqNotificationThreads = new HashMap<>();
    protected Map<RpcNotificationType, String> _zmqEndpoints = null;

    protected String _toString() {
        return (this.getHost() + ":" + this.getPort());
    }

    protected Map<RpcNotificationType, String> _getZmqEndpoints() {
        final String host = _bitcoinNodeRpcAddress.getHost();
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

        final HashMap<RpcNotificationType, String> zmqEndpoints = new HashMap<>();
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
            final RpcNotificationType rpcNotificationType = ZmqMessageTypeConverter.fromPublishString(messageTypeString);
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
            zmqEndpoints.put(rpcNotificationType, endpointUri);
        }

        return zmqEndpoints;
    }

    public BitcoinCoreRpcConnector(final BitcoinNodeRpcAddress bitcoinNodeRpcAddress) {
        this(bitcoinNodeRpcAddress, null);
    }

    public BitcoinCoreRpcConnector(final BitcoinNodeRpcAddress bitcoinNodeRpcAddress, final RpcCredentials rpcCredentials) {
        _bitcoinNodeRpcAddress = bitcoinNodeRpcAddress;
        _rpcCredentials = rpcCredentials;
    }

    public void setZmqEndpoint(final RpcNotificationType rpcNotificationType, final String endpointUri) {
        if (_zmqEndpoints == null) {
            _zmqEndpoints = new HashMap<>();
        }

        _zmqEndpoints.put(rpcNotificationType, endpointUri);
    }

    public void clearZmqEndpoints() {
        _zmqEndpoints = null;
    }

    @Override
    public String getHost() {
        return _bitcoinNodeRpcAddress.getHost();
    }

    @Override
    public Integer getPort() {
        return _bitcoinNodeRpcAddress.getPort();
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
            webRequest.setUrl("http" + (_bitcoinNodeRpcAddress.isSecure() ? "s" : "") + "://" + _bitcoinNodeRpcAddress.getHost() + ":" + _bitcoinNodeRpcAddress.getPort());
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

    @Override
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

        final MutableBlockTemplate blockTemplate = new MutableBlockTemplate();
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

    @Override
    public Boolean validateBlockTemplate(final BlockTemplate blockTemplate, final Monitor monitor) {
        final byte[] requestPayload;
        { // Build request payload
            final Json json = new Json(false);
            json.put("id", _nextRequestId.getAndIncrement());
            json.put("method", "validateblocktemplate");

            { // Method Parameters
                final BlockDeflater blockDeflater = new BlockDeflater();
                final Block block = blockTemplate.toBlock();
                final ByteArray blockTemplateBytes = blockDeflater.toBytes(block);

                final Json paramsJson = new Json(true);
                paramsJson.add(blockTemplateBytes);

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
        return responseJson.get("result", false);
    }

    @Override
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

        // Result is considered valid according to the C++ code. (string "null" or actual null are both accepted)
        // {"result": null}
        final String resultValue = responseJson.getOrNull("result", Json.Types.STRING);
        if (resultValue == null) { return true; }
        return Util.areEqual("null", resultValue.toLowerCase());
    }

    @Override
    public Boolean supportsNotifications() {
        if (_zmqEndpoints == null) {
            _zmqEndpoints = _getZmqEndpoints();
        }

        return (! _zmqEndpoints.isEmpty());
    }

    @Override
    public Boolean supportsNotification(final RpcNotificationType rpcNotificationType) {
        final Map<RpcNotificationType, String> zmqEndpoints = _zmqEndpoints;
        if (zmqEndpoints == null) { return false; }

        return (zmqEndpoints.get(rpcNotificationType) != null);
    }

    @Override
    public void subscribeToNotifications(final RpcNotificationCallback notificationCallback) {
        Map<RpcNotificationType, String> zmqEndpoints = _zmqEndpoints;
        if (zmqEndpoints == null) {
            zmqEndpoints = _getZmqEndpoints();
            _zmqEndpoints = zmqEndpoints;
        }
        if (zmqEndpoints == null) { return; }

        for (final RpcNotificationType rpcNotificationType : zmqEndpoints.keySet()) {
            final String endpointUri = zmqEndpoints.get(rpcNotificationType);
            final ZmqNotificationThread zmqNotificationThread = new ZmqNotificationThread(rpcNotificationType, endpointUri, notificationCallback);
            _zmqNotificationThreads.put(rpcNotificationType, zmqNotificationThread);
            zmqNotificationThread.start();
        }
    }

    @Override
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
