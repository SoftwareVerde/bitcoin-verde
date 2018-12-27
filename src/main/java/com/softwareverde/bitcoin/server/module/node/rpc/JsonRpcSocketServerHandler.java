package com.softwareverde.bitcoin.server.module.node.rpc;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.network.socket.JsonSocketServer;
import com.softwareverde.util.Container;
import com.softwareverde.util.DateUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.util.Map;

public class JsonRpcSocketServerHandler implements JsonSocketServer.SocketConnectedCallback {
    protected static final String ERROR_MESSAGE_KEY = "errorMessage";
    protected static final String WAS_SUCCESS_KEY = "wasSuccess";

    public interface ShutdownHandler {
        Boolean shutdown();
    }

    public interface NodeHandler {
        Boolean addNode(String host, Integer port);
        List<BitcoinNode> getNodes();
    }

    public interface QueryAddressHandler {
        Long getBalance(Address address);
        List<Transaction> getAddressTransactions(Address address);
    }

    public interface ThreadPoolInquisitor {
        Integer getQueueCount();
        Integer getActiveThreadCount();
        Integer getMaxThreadCount();
    }

    public interface ServiceInquisitor {
        Map<String, String> getServiceStatuses();
    }

    public interface DataHandler {
        Long getBlockHeaderHeight();
        Long getBlockHeight();

        Long getBlockHeaderTimestamp();
        Long getBlockTimestamp();

        BlockHeader getBlockHeader(Long blockHeight);
        BlockHeader getBlockHeader(Sha256Hash blockHash);

        Block getBlock(Long blockHeight);
        Block getBlock(Sha256Hash blockHash);

        List<BlockHeader> getBlockHeaders(Long nullableBlockHeight, Integer maxBlockCount);

        Transaction getTransaction(Sha256Hash transactionHash);
    }

    public interface MetadataHandler {
        void applyMetadataToBlockHeader(Sha256Hash blockHash, Json blockJson);
        void applyMetadataToTransaction(Transaction transaction, Json transactionJson);
    }

    public static class StatisticsContainer {
        public Container<Float> averageBlockHeadersPerSecond;
        public Container<Float> averageBlocksPerSecond;
        public Container<Float> averageTransactionsPerSecond;
    }

    protected final Container<Float> _averageBlocksPerSecond;
    protected final Container<Float> _averageBlockHeadersPerSecond;
    protected final Container<Float> _averageTransactionsPerSecond;

    protected SynchronizationStatus _synchronizationStatusHandler = null;
    protected ShutdownHandler _shutdownHandler = null;
    protected NodeHandler _nodeHandler = null;
    protected QueryAddressHandler _queryAddressHandler = null;
    protected ThreadPoolInquisitor _threadPoolInquisitor = null;
    protected ServiceInquisitor _serviceInquisitor = null;
    protected DataHandler _dataHandler = null;
    protected MetadataHandler _metadataHandler = null;

    public JsonRpcSocketServerHandler(final StatisticsContainer statisticsContainer) {
        _averageBlockHeadersPerSecond = statisticsContainer.averageBlockHeadersPerSecond;
        _averageBlocksPerSecond = statisticsContainer.averageBlocksPerSecond;
        _averageTransactionsPerSecond = statisticsContainer.averageTransactionsPerSecond;
    }

    // Requires GET: [blockHeight=null], [maxBlockCount=10]
    protected void _getBlockHeaders(final Json parameters, final Json response) {

        final Long startingBlockHeight;
        {
            final String blockHeightKey = "blockHeight";
            if (! parameters.hasKey(blockHeightKey)) {
                startingBlockHeight = null;
            }
            else {
                final String blockHeightString = parameters.getString(blockHeightKey);
                startingBlockHeight = (Util.isInt(blockHeightString) ? parameters.getLong(blockHeightKey) : null);
            }
        }

        final Integer maxBlockCount;
        if (parameters.hasKey("maxBlockCount")) {
            maxBlockCount = parameters.getInteger("maxBlockCount");
        }
        else {
            maxBlockCount = 10;
        }

        final Json blockHeadersJson = new Json(true);

        final DataHandler dataHandler = _dataHandler;
        if (dataHandler != null) {
            final List<BlockHeader> blockHeaders = dataHandler.getBlockHeaders(startingBlockHeight, maxBlockCount);
            if (blockHeaders == null) {
                response.put(WAS_SUCCESS_KEY, 0);
                response.put(ERROR_MESSAGE_KEY, "Error loading BlockHeaders.");
                return;
            }

            for (final BlockHeader blockHeader : blockHeaders) {
                final Json blockJson = blockHeader.toJson();

                final MetadataHandler metadataHandler = _metadataHandler;
                if (metadataHandler != null) {
                    final Sha256Hash blockHash = blockHeader.getHash();
                    metadataHandler.applyMetadataToBlockHeader(blockHash, blockJson);
                }

                blockHeadersJson.add(blockJson);
            }
        }

        response.put("blockHeaders", blockHeadersJson);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    protected void _getBlockHeader(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if ( (! parameters.hasKey("hash")) && (! parameters.hasKey("blockHeight")) ) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: [hash|blockHeight]");
            return;
        }

        final Boolean shouldReturnRawBlockData = parameters.getBoolean("rawFormat");

        final Boolean blockHeightWasProvided = parameters.hasKey("blockHeight");
        final Long paramBlockHeight = parameters.getLong("blockHeight");
        final String paramBlockHashString = parameters.getString("hash");
        final Sha256Hash paramBlockHash = Sha256Hash.fromHexString(paramBlockHashString);

        if ( (paramBlockHash == null) && (! blockHeightWasProvided) ) {
            response.put(ERROR_MESSAGE_KEY, "Invalid block hash: " + paramBlockHashString);
            return;
        }

        final BlockHeader blockHeader;
        {
            if (blockHeightWasProvided) {
                blockHeader = dataHandler.getBlockHeader(paramBlockHeight);
                if (blockHeader == null) {
                    response.put(ERROR_MESSAGE_KEY, "Block not found at height: " + paramBlockHeight);
                    return;
                }
            }
            else {
                blockHeader = dataHandler.getBlockHeader(paramBlockHash);
                if (blockHeader == null) {
                    response.put(ERROR_MESSAGE_KEY, "Block not found: " + paramBlockHashString);
                    return;
                }
            }
        }

        if (shouldReturnRawBlockData) {
            final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
            final ByteArray blockData = blockHeaderDeflater.toBytes(blockHeader);
            response.put("block", blockData);
        }
        else {
            final Sha256Hash blockHash = blockHeader.getHash();
            final Json blockJson = blockHeader.toJson();

            final MetadataHandler metadataHandler = _metadataHandler;
            if (metadataHandler != null) {
                metadataHandler.applyMetadataToBlockHeader(blockHash, blockJson);
            }

            response.put("block", blockJson);
        }

        response.put(WAS_SUCCESS_KEY, 1);
    }

    protected void _getBlock(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if ( (! parameters.hasKey("hash")) && (! parameters.hasKey("blockHeight")) ) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: [hash|blockHeight]");
            return;
        }

        final Boolean shouldReturnRawBlockData = parameters.getBoolean("rawFormat");

        final Boolean blockHeightWasProvided = parameters.hasKey("blockHeight");
        final Long paramBlockHeight = parameters.getLong("blockHeight");
        final String paramBlockHashString = parameters.getString("hash");
        final Sha256Hash paramBlockHash = Sha256Hash.fromHexString(paramBlockHashString);

        if ( (paramBlockHash == null) && (! blockHeightWasProvided) ) {
            response.put(ERROR_MESSAGE_KEY, "Invalid block hash: " + paramBlockHashString);
            return;
        }

        final Block block;
        {
            if (blockHeightWasProvided) {
                block = dataHandler.getBlock(paramBlockHeight);
                if (block == null) {
                    response.put(ERROR_MESSAGE_KEY, "Block not found at height: " + paramBlockHeight);
                    return;
                }
            }
            else {
                block = dataHandler.getBlock(paramBlockHash);
                if (block == null) {
                    response.put(ERROR_MESSAGE_KEY, "Block not found: " + paramBlockHashString);
                    return;
                }
            }
        }

        if (shouldReturnRawBlockData) {
            final BlockDeflater blockDeflater = new BlockDeflater();
            final ByteArray blockData = blockDeflater.toBytes(block);
            response.put("block", blockData);
        }
        else {
            final Json blockJson = block.toJson();

            final MetadataHandler metadataHandler = _metadataHandler;
            if (metadataHandler != null) {
                final Sha256Hash blockHash = block.getHash();

                final List<Transaction> blockTransactions = block.getTransactions();
                final Json transactionsJson = blockJson.get("transactions");
                for (int i = 0; i < transactionsJson.length(); ++i) {
                    final Json transactionJson = transactionsJson.get(i);
                    final Transaction transaction = blockTransactions.get(i);
                    metadataHandler.applyMetadataToTransaction(transaction, transactionJson);
                }

                metadataHandler.applyMetadataToBlockHeader(blockHash, blockJson);
            }

            response.put("block", blockJson);
        }

        response.put(WAS_SUCCESS_KEY, 1);
    }

    protected void _getTransaction(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if (! parameters.hasKey("hash")) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: hash");
            return;
        }

        final Boolean shouldReturnRawTransactionData = parameters.getBoolean("rawFormat");

        final String transactionHashString = parameters.getString("hash");
        final Sha256Hash transactionHash = Sha256Hash.fromHexString(transactionHashString);

        if (transactionHash == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid transaction hash: " + transactionHashString);
            return;
        }

        final Transaction transaction = dataHandler.getTransaction(transactionHash);
        if (transaction == null) {
            response.put(ERROR_MESSAGE_KEY, "Transaction not found: " + transactionHashString);
            return;
        }

        if (shouldReturnRawTransactionData) {
            final TransactionDeflater transactionDeflater = new TransactionDeflater();
            final ByteArray transactionData = transactionDeflater.toBytes(transaction);
            response.put("transaction", HexUtil.toHexString(transactionData.getBytes()));
        }
        else {
            final Json transactionJson = transaction.toJson();

            final MetadataHandler metadataHandler = _metadataHandler;
            if (metadataHandler != null) {
                metadataHandler.applyMetadataToTransaction(transaction, transactionJson);
            }

            response.put("transaction", transactionJson);
        }
        response.put(WAS_SUCCESS_KEY, 1);
    }

    protected void _queryBlockHeight(final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Long blockHeight = dataHandler.getBlockHeight();
        final Long blockHeaderHeight = dataHandler.getBlockHeaderHeight();

        response.put("blockHeight", blockHeight);
        response.put("blockHeaderHeight", blockHeaderHeight);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    protected void _queryStatus(final Json response) {
        { // Status
            response.put("status", (_synchronizationStatusHandler != null ? _synchronizationStatusHandler.getState() : null));
        }

        { // Statistics
            final DataHandler dataHandler = _dataHandler;

            final Long blockHeight = (dataHandler != null ? dataHandler.getBlockHeight() : null);
            final Long blockHeaderHeight = (dataHandler != null ? dataHandler.getBlockHeaderHeight() : null);

            final Long blockTimestampInSeconds = (dataHandler != null ? Util.coalesce(dataHandler.getBlockTimestamp()) : 0L);
            final Long blockHeaderTimestampInSeconds = (dataHandler != null ? Util.coalesce(dataHandler.getBlockHeaderTimestamp()) : 0L);

            final Json statisticsJson = new Json();
            statisticsJson.put("blockHeaderHeight", blockHeaderHeight);
            statisticsJson.put("blockHeadersPerSecond", _averageBlockHeadersPerSecond.value);
            statisticsJson.put("blockHeaderDate", DateUtil.Utc.timestampToDatetimeString(blockHeaderTimestampInSeconds * 1000));
            statisticsJson.put("blockHeaderTimestamp", blockHeaderTimestampInSeconds);

            statisticsJson.put("blockHeight", blockHeight);
            statisticsJson.put("blocksPerSecond", _averageBlocksPerSecond.value);
            statisticsJson.put("blockDate", DateUtil.Utc.timestampToDatetimeString(blockTimestampInSeconds * 1000));
            statisticsJson.put("blockTimestamp", blockTimestampInSeconds);

            statisticsJson.put("transactionsPerSecond", _averageTransactionsPerSecond.value);
            response.put("statistics", statisticsJson);
        }

        { // Server Load
            final Json serverLoadJson = new Json();
            final ThreadPoolInquisitor threadPoolInquisitor = _threadPoolInquisitor;
            serverLoadJson.put("threadPoolQueueCount",          (threadPoolInquisitor != null ? threadPoolInquisitor.getQueueCount() : null));
            serverLoadJson.put("threadPoolActiveThreadCount",   (threadPoolInquisitor != null ? threadPoolInquisitor.getActiveThreadCount() : null));
            serverLoadJson.put("threadPoolMaxThreadCount",      (threadPoolInquisitor != null ? threadPoolInquisitor.getMaxThreadCount() : null));

            final Runtime runtime = Runtime.getRuntime();
            serverLoadJson.put("jvmMemoryUsageByteCount", (runtime.totalMemory() - runtime.freeMemory()));
            serverLoadJson.put("jvmMemoryMaxByteCount", runtime.maxMemory());

            response.put("serverLoad", serverLoadJson);
        }

        { // Service Statuses
            final Json servicesStatusJson = new Json();
            final ServiceInquisitor serviceInquisitor = _serviceInquisitor;
            if (serviceInquisitor != null) {
                final Map<String, String> serviceStatuses = serviceInquisitor.getServiceStatuses();
                for (final String serviceName : serviceStatuses.keySet()) {
                    final String serviceStatus = serviceStatuses.get(serviceName);
                    servicesStatusJson.put(serviceName, serviceStatus);
                }
            }
            response.put("serviceStatuses", servicesStatusJson);
        }

        response.put(WAS_SUCCESS_KEY, 1);
    }

    protected void _queryBalance(final Json parameters, final Json response) {
        final QueryAddressHandler queryAddressHandler = _queryAddressHandler;
        if (queryAddressHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if (! parameters.hasKey("address")) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: address");
            return;
        }

        final String addressString = parameters.getString("address");
        final AddressInflater addressInflater = new AddressInflater();
        final Address address = addressInflater.fromBase58Check(addressString);

        if (address == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid address.");
            return;
        }

        final Long balance = queryAddressHandler.getBalance(address);

        if (balance == null) {
            response.put(ERROR_MESSAGE_KEY, "Unable to determine balance.");
            return;
        }

        response.put("balance", balance);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    protected void _queryAddressTransactions(final Json parameters, final Json response) {
        final QueryAddressHandler queryAddressHandler = _queryAddressHandler;
        if (queryAddressHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if (! parameters.hasKey("address")) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: address");
            return;
        }

        final String addressString = parameters.getString("address");
        final AddressInflater addressInflater = new AddressInflater();
        final Address address = addressInflater.fromBase58Check(addressString);

        if (address == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid address: " + addressString);
            return;
        }

        final List<Transaction> addressTransactions = queryAddressHandler.getAddressTransactions(address);

        if (addressTransactions == null) {
            response.put(ERROR_MESSAGE_KEY, "Unable to determine address transactions.");
            return;
        }

        final Json addressJson = new Json();
        addressJson.put("base58CheckEncoded", address.toBase58CheckEncoded());
        addressJson.put("balance", queryAddressHandler.getBalance(address));

        { // Address Transactions
            final Json transactionsJson = new Json(true);

            final MetadataHandler metadataHandler = _metadataHandler;
            for (final Transaction transaction : addressTransactions) {
                final Json transactionJson = transaction.toJson();

                if (metadataHandler != null) {
                    metadataHandler.applyMetadataToTransaction(transaction, transactionJson);
                }

                transactionsJson.add(transactionJson);
            }

            addressJson.put("transactions", transactionsJson);
        }

        response.put("address", addressJson);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    protected void _shutdown(final Json parameters, final Json response) {
        final ShutdownHandler shutdownHandler = _shutdownHandler;
        if (shutdownHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Boolean wasSuccessful = shutdownHandler.shutdown();
        response.put(WAS_SUCCESS_KEY, (wasSuccessful ? 1 : 0));
    }

    protected void _addNode(final Json parameters, final Json response) {
        final NodeHandler nodeHandler = _nodeHandler;
        if (nodeHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if ((! parameters.hasKey("host")) || (! parameters.hasKey("port"))) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: host, port");
            return;
        }

        final String host = parameters.getString("host");
        final Integer port = parameters.getInteger("port");

        final Boolean wasSuccessful = nodeHandler.addNode(host, port);
        response.put(WAS_SUCCESS_KEY, (wasSuccessful ? 1 : 0));
    }

    protected void _nodeStatus(final Json response) {
        final NodeHandler nodeHandler = _nodeHandler;
        if (nodeHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Json nodeListJson = new Json();

        final List<BitcoinNode> nodes = _nodeHandler.getNodes();
        for (final BitcoinNode node : nodes) {
            final NodeIpAddress nodeIpAddress = node.getRemoteNodeIpAddress();
            if (nodeIpAddress == null) { continue; }

            final Json nodeJson = new Json();
            nodeJson.put("host", nodeIpAddress.getIp());
            nodeJson.put("port", nodeIpAddress.getPort());
            nodeJson.put("userAgent", node.getUserAgent());
            nodeJson.put("initializationTimestamp", (node.getInitializationTimestamp() / 1000L));
            nodeJson.put("lastMessageReceivedTimestamp", (node.getLastMessageReceivedTimestamp() / 1000L));
            nodeJson.put("networkOffset", node.getNetworkTimeOffset());

            final NodeIpAddress localNodeIpAddress = node.getLocalNodeIpAddress();
            nodeJson.put("localHost", (localNodeIpAddress != null ? localNodeIpAddress.getIp() : null));
            nodeJson.put("localPort", (localNodeIpAddress != null ? localNodeIpAddress.getPort() : null));

            nodeJson.put("handshakeIsComplete", (node.handshakeIsComplete() ? 1 : 0));
            nodeJson.put("id", node.getId());

            final Json featuresJson = new Json(false);
            for (final NodeFeatures.Feature nodeFeature : NodeFeatures.Feature.values()) {
                final Boolean hasFeatureEnabled = (node.hasFeatureEnabled(nodeFeature));
                final String featureKey = nodeFeature.name();
                if (hasFeatureEnabled == null) {
                    featuresJson.put(featureKey, null);
                }
                else {
                    featuresJson.put(featureKey, (hasFeatureEnabled ? 1 : 0));
                }
            }
            featuresJson.put("THIN_PROTOCOL_ENABLED", (node.supportsExtraThinBlocks() ? 1 : 0));
            nodeJson.put("features", featuresJson);

            nodeListJson.add(nodeJson);
        }

        response.put("nodes", nodeListJson);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    public void setSynchronizationStatusHandler(final SynchronizationStatus synchronizationStatusHandler) {
        _synchronizationStatusHandler = synchronizationStatusHandler;
    }

    public void setShutdownHandler(final ShutdownHandler shutdownHandler) {
        _shutdownHandler = shutdownHandler;
    }

    public void setNodeHandler(final NodeHandler nodeHandler) {
        _nodeHandler = nodeHandler;
    }

    public void setQueryAddressHandler(final QueryAddressHandler queryAddressHandler) {
        _queryAddressHandler = queryAddressHandler;
    }

    public void setThreadPoolInquisitor(final ThreadPoolInquisitor threadPoolInquisitor) {
        _threadPoolInquisitor = threadPoolInquisitor;
    }

    public void setServiceInquisitor(final ServiceInquisitor serviceInquisitor) {
        _serviceInquisitor = serviceInquisitor;
    }

    public void setDataHandler(final DataHandler dataHandler) {
        _dataHandler = dataHandler;
    }

    public void setMetadataHandler(final MetadataHandler metadataHandler) {
        _metadataHandler = metadataHandler;
    }

    @Override
    public void run(final JsonSocket socketConnection) {
        Logger.log("New Connection: " + socketConnection);
        socketConnection.setMessageReceivedCallback(new Runnable() {
            @Override
            public void run() {
                final Json message = socketConnection.popMessage().getMessage();
                Logger.log("Message received: "+ message);

                final String method = message.getString("method");
                final String query = message.getString("query");

                final Json response = new Json();
                response.put("method", "RESPONSE");
                response.put(WAS_SUCCESS_KEY, 0);
                response.put(ERROR_MESSAGE_KEY, null);

                final Json parameters = message.get("parameters");

                switch (method.toUpperCase()) {
                    case "GET": {
                        switch (query.toUpperCase()) {
                            case "BLOCK_HEADERS": {
                                _getBlockHeaders(parameters, response);
                            } break;

                            case "BLOCK": {
                                _getBlock(parameters, response);
                            } break;

                            case "BLOCK_HEADER": {
                                _getBlockHeader(parameters, response);
                            } break;

                            case "TRANSACTION": {
                                _getTransaction(parameters, response);
                            } break;

                            case "BLOCK_HEIGHT": {
                                _queryBlockHeight(response);
                            } break;

                            case "STATUS": {
                                _queryStatus(response);
                            } break;

                            case "NODES": {
                                _nodeStatus(response);
                            } break;

                            case "BALANCE": {
                                _queryBalance(parameters, response);
                            } break;

                            case "ADDRESS": {
                                _queryAddressTransactions(parameters, response);
                            } break;

                            default: {
                                response.put(ERROR_MESSAGE_KEY, "Invalid command: " + method + "/" + query);
                            } break;
                        }
                    } break;

                    case "POST": {
                        switch (query.toUpperCase()) {
                            case "SHUTDOWN": {
                                _shutdown(parameters, response);
                            } break;

                            case "ADD_NODE": {
                                _addNode(parameters, response);
                            } break;

                            default: {
                                response.put(ERROR_MESSAGE_KEY, "Invalid command: " + method + "/" + query);
                            } break;
                        }
                    } break;

                    default: {
                        response.put(ERROR_MESSAGE_KEY, "Invalid method: " + method);
                    } break;
                }

                final String responseString = response.toString();
                final String truncatedResponseString = responseString.substring(0, Math.min(256, responseString.length()));
                Logger.log("Writing: " + truncatedResponseString + (responseString.length() > 256 ? "..." : ""));
                socketConnection.write(new JsonProtocolMessage(response));
            }
        });
        socketConnection.beginListening();
    }
}
