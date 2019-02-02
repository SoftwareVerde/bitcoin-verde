package com.softwareverde.bitcoin.server.module.node.rpc;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.rpc.blockchain.BlockchainMetadata;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.network.socket.JsonSocketServer;
import com.softwareverde.util.Container;
import com.softwareverde.util.DateUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

public class JsonRpcSocketServerHandler implements JsonSocketServer.SocketConnectedCallback {
    protected static final String ERROR_MESSAGE_KEY = "errorMessage";
    protected static final String WAS_SUCCESS_KEY = "wasSuccess";

    public interface ShutdownHandler {
        Boolean shutdown();
    }

    public interface NodeHandler {
        void addNode(Ip ip, Integer port);
        List<BitcoinNode> getNodes();
        void banNode(Ip ip);
        void unbanNode(Ip ip);
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

    public interface QueryBlockchainHandler {
        List<BlockchainMetadata> getBlockchainMetadata();
    }

    public static class TransactionWithFee {
        public final Transaction transaction;
        public final Long transactionFee;

        public TransactionWithFee(final Transaction transaction, final Long transactionFee) {
            this.transaction = transaction.asConst();
            this.transactionFee = transactionFee;
        }
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

        Difficulty getDifficulty();
        List<Transaction> getUnconfirmedTransactions();
        List<TransactionWithFee> getUnconfirmedTransactionsWithFees();

        Long getBlockReward();
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

    protected static abstract class LazyProtocolMessage {
        private ProtocolMessage _cachedProtocolMessage;

        protected abstract ProtocolMessage _createProtocolMessage();

        public ProtocolMessage getProtocolMessage() {
            if (_cachedProtocolMessage == null) {
                _cachedProtocolMessage = _createProtocolMessage();
            }

            return _cachedProtocolMessage;
        }
    }

    protected final ThreadPool _threadPool;
    protected final Container<Float> _averageBlocksPerSecond;
    protected final Container<Float> _averageBlockHeadersPerSecond;
    protected final Container<Float> _averageTransactionsPerSecond;

    public enum HookEvent {
        NEW_BLOCK,
        NEW_TRANSACTION;

        public static HookEvent fromString(final String string) {
            for (final HookEvent hookEvent : HookEvent.values()) {
                final String hookEventName = hookEvent.name();
                if (hookEventName.equalsIgnoreCase(string)) {
                    return hookEvent;
                }
            }

            return null;
        }
    }

    protected static class HookListener {
        public final JsonSocket socket;
        public final Boolean rawFormat;

        public HookListener(final JsonSocket socket, final Boolean rawFormat) {
            this.socket = socket;
            this.rawFormat = rawFormat;
        }
    }

    protected final HashMap<HookEvent, MutableList<HookListener>> _eventHooks = new HashMap<HookEvent, MutableList<HookListener>>();

    protected SynchronizationStatus _synchronizationStatusHandler = null;
    protected ShutdownHandler _shutdownHandler = null;
    protected NodeHandler _nodeHandler = null;
    protected QueryAddressHandler _queryAddressHandler = null;
    protected ThreadPoolInquisitor _threadPoolInquisitor = null;
    protected ServiceInquisitor _serviceInquisitor = null;
    protected DataHandler _dataHandler = null;
    protected MetadataHandler _metadataHandler = null;
    protected QueryBlockchainHandler _queryBlockchainHandler = null;

    public JsonRpcSocketServerHandler(final StatisticsContainer statisticsContainer, final ThreadPool threadPool) {
        _averageBlockHeadersPerSecond = statisticsContainer.averageBlockHeadersPerSecond;
        _averageBlocksPerSecond = statisticsContainer.averageBlocksPerSecond;
        _averageTransactionsPerSecond = statisticsContainer.averageTransactionsPerSecond;
        _threadPool = threadPool;
    }

    // Requires GET: [blockHeight], [maxBlockCount=10], [rawFormat=0]
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

        final Boolean shouldReturnRawBlockData = parameters.getBoolean("rawFormat");

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
                if (shouldReturnRawBlockData) {
                    final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
                    final ByteArray blockData = blockHeaderDeflater.toBytes(blockHeader);
                    blockHeadersJson.add(blockData);
                }
                else {
                    final Json blockJson = blockHeader.toJson();

                    final MetadataHandler metadataHandler = _metadataHandler;
                    if (metadataHandler != null) {
                        final Sha256Hash blockHash = blockHeader.getHash();
                        metadataHandler.applyMetadataToBlockHeader(blockHash, blockJson);
                    }

                    blockHeadersJson.add(blockJson);
                }
            }
        }

        response.put("blockHeaders", blockHeadersJson);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires GET: <blockHeight | hash>
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

    // Requires GET: <blockHeight | hash>, [rawFormat=0]
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

    // Requires GET: <hash>, [rawFormat=0]
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

    // Requires GET:
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

    // Requires GET:
    protected void _calculateNextDifficulty(final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Difficulty difficulty = dataHandler.getDifficulty();

        response.put("difficulty", difficulty.encode());
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires GET:
    protected void _calculateNextBlockReward(final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Long satoshis = dataHandler.getBlockReward();

        response.put("blockReward", satoshis);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires GET: [rawFormat=0]
    protected void _getUnconfirmedTransactions(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Boolean shouldReturnRawTransactionData = parameters.getBoolean("rawFormat");

        final Json unconfirmedTransactionsJson = new Json(true);

        if (shouldReturnRawTransactionData) {
            final List<TransactionWithFee> transactions = dataHandler.getUnconfirmedTransactionsWithFees();
            for (final TransactionWithFee unconfirmedTransaction : transactions) {
                final TransactionDeflater transactionDeflater = new TransactionDeflater();
                final ByteArray transactionData = transactionDeflater.toBytes(unconfirmedTransaction.transaction);

                final Json unconfirmedTransactionJson = new Json();
                unconfirmedTransactionJson.put("transaction", HexUtil.toHexString(transactionData.getBytes()));
                unconfirmedTransactionJson.put("transactionFee", unconfirmedTransaction.transactionFee);

                unconfirmedTransactionsJson.add(unconfirmedTransactionJson);
            }
        }
        else {
            final List<Transaction> transactions = dataHandler.getUnconfirmedTransactions();
            for (final Transaction transaction : transactions) {
                final Json transactionJson = transaction.toJson();

                final MetadataHandler metadataHandler = _metadataHandler;
                if (metadataHandler != null) {
                    metadataHandler.applyMetadataToTransaction(transaction, transactionJson);
                }

                unconfirmedTransactionsJson.add(transactionJson);
            }
        }

        response.put("unconfirmedTransactions", unconfirmedTransactionsJson);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires GET:
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

    // Requires GET: <address>
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

    // Requires GET: <address>
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

    // Requires GET:
    protected void _queryBlockchainMetadata(final Json response) {
        final QueryBlockchainHandler queryBlockchainHandler = _queryBlockchainHandler;
        if (queryBlockchainHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Json blockchainMetadataJson = new Json(true);

        final List<BlockchainMetadata> blockchainMetadataList = queryBlockchainHandler.getBlockchainMetadata();
        if (blockchainMetadataList == null) {
            response.put(ERROR_MESSAGE_KEY, "Error loading Blockchain metadata.");
            return;
        }

        for (final BlockchainMetadata blockchainMetadata : blockchainMetadataList) {
            blockchainMetadataJson.add(blockchainMetadata.toJson());
        }

        response.put("blockchainMetadata", blockchainMetadataJson);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires POST:
    protected void _shutdown(final Json parameters, final Json response) {
        final ShutdownHandler shutdownHandler = _shutdownHandler;
        if (shutdownHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Boolean wasSuccessful = shutdownHandler.shutdown();
        response.put(WAS_SUCCESS_KEY, (wasSuccessful ? 1 : 0));
    }

    // Requires POST: <host>, <port>
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

        if ( (port <= 0) || (port > 65535) ) {
            response.put(ERROR_MESSAGE_KEY, "Invalid port: " + port);
        }

        final Ip ip = Ip.fromString(host);
        if (ip == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid ip: " + host);
            return;
        }

        nodeHandler.addNode(ip, port);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires POST: <host>
    protected void _banNode(final Json parameters, final Json response) {
        final NodeHandler nodeHandler = _nodeHandler;
        if (nodeHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if (! parameters.hasKey("host")) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: host");
            return;
        }

        final String host = parameters.getString("host");

        final Ip ip = Ip.fromString(host);
        if (ip == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid ip: " + host);
            return;
        }

        nodeHandler.banNode(ip);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires POST: <host>
    protected void _unbanNode(final Json parameters, final Json response) {
        final NodeHandler nodeHandler = _nodeHandler;
        if (nodeHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if (! parameters.hasKey("host")) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: host");
            return;
        }

        final String host = parameters.getString("host");

        final Ip ip = Ip.fromString(host);
        if (ip == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid ip: " + host);
            return;
        }

        nodeHandler.unbanNode(ip);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires POST: events
    // Returns true if the connection should remain open...
    protected Boolean _addHook(final Json parameters, final Json response, final JsonSocket connection) {
        final NodeHandler nodeHandler = _nodeHandler;
        if (nodeHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return false;
        }

        if (! parameters.hasKey("events")) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: events");
            return false;
        }

        final HashSet<HookEvent> hookEvents = new HashSet<HookEvent>();
        final Json events = parameters.get("events");
        for (int i = 0; i < events.length(); ++i) {
            final String event = events.getString(i);
            final HookEvent hookEvent = HookEvent.fromString(event);
            if (hookEvent != null) {
                hookEvents.add(hookEvent);
            }
        }
        if (hookEvents.isEmpty()) {
            response.put(ERROR_MESSAGE_KEY, "Invalid event type(s).");
            return false;
        }

        final Boolean shouldReturnRawData = parameters.getBoolean("rawFormat");

        synchronized (_eventHooks) {
            for (final HookEvent hookEvent : hookEvents) {
                if (! _eventHooks.containsKey(hookEvent)) {
                    _eventHooks.put(hookEvent, new MutableList<HookListener>());
                }

                final MutableList<HookListener> nodeIpAddresses = _eventHooks.get(hookEvent);
                nodeIpAddresses.add(new HookListener(connection, shouldReturnRawData));
            }
        }

        response.put(WAS_SUCCESS_KEY, 1);
        return true;
    }

    // Requires GET:
    protected void _listNodes(final Json response) {
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

    public void setQueryBlockchainHandler(final QueryBlockchainHandler queryBlockchainHandler) {
        _queryBlockchainHandler = queryBlockchainHandler;
    }

    public void onNewBlock(final BlockHeader block) {
        // Ensure the provided block is only the header by copying it...
        final BlockHeader blockHeader = new ImmutableBlockHeader(block);

        final LazyProtocolMessage lazyMetadataProtocolMessage = new LazyProtocolMessage() {
            @Override
            protected ProtocolMessage _createProtocolMessage() {
                final Json blockJson = blockHeader.toJson();
                final MetadataHandler metadataHandler = _metadataHandler;
                if (metadataHandler != null) {
                    _metadataHandler.applyMetadataToBlockHeader(block.getHash(), blockJson);
                }

                final Json json = new Json();
                json.put("objectType", "BLOCK");
                json.put("object", blockJson);

                return new JsonProtocolMessage(json);
            }
        };

        final LazyProtocolMessage lazyRawDataProtocolMessage = new LazyProtocolMessage() {
            @Override
            protected ProtocolMessage _createProtocolMessage() {
                final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
                final ByteArray blockData = blockHeaderDeflater.toBytes(blockHeader);

                final Json objectJson = new Json();
                objectJson.put("data", blockData);

                final Json json = new Json();
                json.put("objectType", "BLOCK");
                json.put("object", blockData);

                return new JsonProtocolMessage(json);
            }
        };

        _threadPool.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (_eventHooks) {
                    final MutableList<HookListener> sockets = _eventHooks.get(HookEvent.NEW_BLOCK);
                    if (sockets == null) { return; }

                    final Iterator<HookListener> iterator = sockets.mutableIterator();
                    while (iterator.hasNext()) {
                        final HookListener hookListener = iterator.next();
                        final JsonSocket jsonSocket = hookListener.socket;

                        final ProtocolMessage protocolMessage = (hookListener.rawFormat ? lazyRawDataProtocolMessage.getProtocolMessage() : lazyMetadataProtocolMessage.getProtocolMessage());
                        jsonSocket.write(protocolMessage);

                        if (! jsonSocket.isConnected()) {
                            iterator.remove();
                            Logger.log("Dropping HookEvent: " + HookEvent.NEW_BLOCK + " " + jsonSocket.toString());
                        }
                    }
                }
            }
        });
    }

    public void onNewTransaction(final Transaction transaction) {
        final LazyProtocolMessage lazyMetadataProtocolMessage = new LazyProtocolMessage() {
            @Override
            protected ProtocolMessage _createProtocolMessage() {
                final Json transactionJson = transaction.toJson();
                final MetadataHandler metadataHandler = _metadataHandler;
                if (metadataHandler != null) {
                    _metadataHandler.applyMetadataToTransaction(transaction, transactionJson);
                }

                final Json json = new Json();
                json.put("objectType", "TRANSACTION");
                json.put("object", transactionJson);

                return new JsonProtocolMessage(json);
            }
        };

        final LazyProtocolMessage lazyRawProtocolMessage = new LazyProtocolMessage() {
            @Override
            protected ProtocolMessage _createProtocolMessage() {
                final TransactionDeflater transactionDeflater = new TransactionDeflater();
                final ByteArray transactionBytes = transactionDeflater.toBytes(transaction);

                final Json json = new Json();
                json.put("objectType", "TRANSACTION");
                json.put("object", transactionBytes);

                return new JsonProtocolMessage(json);
            }
        };

        _threadPool.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (_eventHooks) {
                    final MutableList<HookListener> sockets = _eventHooks.get(HookEvent.NEW_TRANSACTION);
                    if (sockets == null) { return; }

                    final Iterator<HookListener> iterator = sockets.mutableIterator();
                    while (iterator.hasNext()) {
                        final HookListener hookListener = iterator.next();
                        final JsonSocket jsonSocket = hookListener.socket;

                        final ProtocolMessage protocolMessage = (hookListener.rawFormat ? lazyRawProtocolMessage.getProtocolMessage() : lazyMetadataProtocolMessage.getProtocolMessage());
                        jsonSocket.write(protocolMessage);

                        if (! jsonSocket.isConnected()) {
                            iterator.remove();
                            Logger.log("Dropping HookEvent: " + HookEvent.NEW_TRANSACTION + " " + jsonSocket.toString());
                        }
                    }
                }
            }
        });
    }

    @Override
    public void run(final JsonSocket socketConnection) {
        socketConnection.setMessageReceivedCallback(new Runnable() {
            @Override
            public void run() {
                final Json message = socketConnection.popMessage().getMessage();

                final String method = message.getString("method");
                final String query = message.getString("query");

                final Json response = new Json();
                response.put("method", "RESPONSE");
                response.put(WAS_SUCCESS_KEY, 0);
                response.put(ERROR_MESSAGE_KEY, null);

                final Json parameters = message.get("parameters");
                Boolean closeConnection = true;

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

                            case "DIFFICULTY": {
                                _calculateNextDifficulty(response);
                            } break;

                            case "BLOCK_REWARD": {
                                _calculateNextBlockReward(response);
                            } break;

                            case "MEMPOOL":
                            case "UNCONFIRMED_TRANSACTIONS": {
                                _getUnconfirmedTransactions(parameters, response);
                            } break;

                            case "STATUS": {
                                _queryStatus(response);
                            } break;

                            case "NODES": {
                                _listNodes(response);
                            } break;

                            case "BALANCE": {
                                _queryBalance(parameters, response);
                            } break;

                            case "ADDRESS": {
                                _queryAddressTransactions(parameters, response);
                            } break;

                            case "BLOCKCHAIN": {
                                _queryBlockchainMetadata(response);
                            } break;

                            default: {
                                response.put(ERROR_MESSAGE_KEY, "Invalid " + method + " query: " + query);
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

                            case "BAN_NODE": {
                                _banNode(parameters, response);
                            } break;

                            case "UNBAN_NODE": {
                                _unbanNode(parameters, response);
                            } break;

                            case "ADD_HOOK": {
                                final Boolean keepSocketOpen = _addHook(parameters, response, socketConnection);
                                closeConnection = (! keepSocketOpen);
                            } break;

                            default: {
                                response.put(ERROR_MESSAGE_KEY, "Invalid " + method + " query: " + query);
                            } break;
                        }
                    } break;

                    default: {
                        response.put(ERROR_MESSAGE_KEY, "Invalid command: " + method.toUpperCase());
                    } break;
                }

                socketConnection.write(new JsonProtocolMessage(response));

                if (closeConnection) {
                    socketConnection.close();
                }
            }
        });
        socketConnection.beginListening();
    }
}
