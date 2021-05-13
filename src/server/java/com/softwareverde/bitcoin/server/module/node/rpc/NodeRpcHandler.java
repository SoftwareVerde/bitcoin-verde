package com.softwareverde.bitcoin.server.module.node.rpc;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.BlockValidationResult;
import com.softwareverde.bitcoin.block.validator.ValidationResult;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.rpc.blockchain.BlockchainMetadata;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.TransactionWithFee;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
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

public class NodeRpcHandler implements JsonSocketServer.SocketConnectedCallback {
    public static final Integer MAX_ADDRESS_FILTER_SIZE = 256;

    protected static final String ERROR_MESSAGE_KEY = "errorMessage";
    protected static final String WAS_SUCCESS_KEY = "wasSuccess";

    public interface ShutdownHandler {
        Boolean shutdown();
    }

    public interface NodeHandler {
        void addNode(Ip ip, Integer port);
        List<BitcoinNode> getNodes();
        Boolean isPreferredNode(BitcoinNode bitcoinNode);
        void banNode(Ip ip);
        void unbanNode(Ip ip);
        void addIpToWhitelist(Ip ip);
        void removeIpFromWhitelist(Ip ip);
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

    public interface UtxoCacheHandler {
        Long getCachedUtxoCount();
        Long getMaxCachedUtxoCount();
        Long getUncommittedUtxoCount();
        Long getCommittedUtxoBlockHeight();

        void commitUtxoCache();
    }

    public interface QueryBlockchainHandler {
        List<BlockchainMetadata> getBlockchainMetadata();
    }

    public interface DataHandler {
        Long getBlockHeaderHeight();
        Long getBlockHeight();

        Long getBlockHeaderTimestamp();

        Long getBlockTimestamp();

        BlockHeader getBlockHeader(Long blockHeight);
        BlockHeader getBlockHeader(Sha256Hash blockHash);

        Long getBlockHeaderHeight(final Sha256Hash blockHash);

        Block getBlock(Long blockHeight);
        Block getBlock(Sha256Hash blockHash);

        List<Transaction> getBlockTransactions(Long blockHeight, Integer pageSize, Integer pageNumber);
        List<Transaction> getBlockTransactions(Sha256Hash blockHash, Integer pageSize, Integer pageNumber);

        List<BlockHeader> getBlockHeaders(Long nullableBlockHeight, Integer maxBlockCount);

        Transaction getTransaction(Sha256Hash transactionHash);

        Difficulty getDifficulty();
        List<Transaction> getUnconfirmedTransactions();
        List<TransactionWithFee> getUnconfirmedTransactionsWithFees();
        Block getPrototypeBlock();

        Long getBlockReward();

        Boolean isSlpTransaction(Sha256Hash transactionHash);
        Boolean isValidSlpTransaction(Sha256Hash transactionHash);
        SlpTokenId getSlpTokenId(Sha256Hash transactionHash);

        List<DoubleSpendProof> getDoubleSpendProofs();
        DoubleSpendProof getDoubleSpendProof(Sha256Hash doubleSpendProofHash);
        DoubleSpendProof getDoubleSpendProof(TransactionOutputIdentifier transactionOutputIdentifierBeingSpent);

        BlockValidationResult validatePrototypeBlock(Block block);
        ValidationResult validateTransaction(Transaction transaction, Boolean enableSlpValidation);

        void submitTransaction(Transaction transaction);
        void submitBlock(Block block);
        void reconsiderBlock(Sha256Hash blockHash);
    }

    public interface LogLevelSetter {
        void setLogLevel(String packageName, String logLevel);
    }

    public interface IndexerHandler {
        Boolean clearTransactionIndexes();
        Boolean clearAllSlpValidation();
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

    public enum HookEvent {
        NEW_BLOCK,
        NEW_TRANSACTION,
        NEW_DOUBLE_SPEND_PROOF;

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

    protected static class HookListener {
        public final JsonSocket socket;
        public final Boolean rawFormat;
        public final Boolean includeTransactionFees;
        public final BloomFilter addressFilter;

        public HookListener(final JsonSocket socket, final Boolean rawFormat, final Boolean includeTransactionFees, final List<Address> addressesFilter) {
            this.socket = socket;
            this.rawFormat = rawFormat;
            this.includeTransactionFees = includeTransactionFees;

            if (addressesFilter != null) {
                final Long itemCount = Math.min(MAX_ADDRESS_FILTER_SIZE, addressesFilter.getCount() * 2L);
                final MutableBloomFilter bloomFilter = MutableBloomFilter.newInstance(itemCount, 0.005D);
                for (final Address address : addressesFilter) {
                    bloomFilter.addItem(address);
                }
                this.addressFilter = bloomFilter;
            }
            else {
                this.addressFilter = null;
            }
        }
    }

    protected final MasterInflater _masterInflater;
    protected final ThreadPool _threadPool;
    protected final Container<Float> _averageBlocksPerSecond;
    protected final Container<Float> _averageBlockHeadersPerSecond;
    protected final Container<Float> _averageTransactionsPerSecond;

    protected final HashMap<HookEvent, MutableList<HookListener>> _eventHooks = new HashMap<HookEvent, MutableList<HookListener>>();

    protected SynchronizationStatus _synchronizationStatusHandler = null;
    protected ShutdownHandler _shutdownHandler = null;
    protected UtxoCacheHandler _utxoCacheHandler = null;
    protected NodeHandler _nodeHandler = null;
    protected QueryAddressHandler _queryAddressHandler = null;
    protected ThreadPoolInquisitor _threadPoolInquisitor = null;
    protected ServiceInquisitor _serviceInquisitor = null;
    protected DataHandler _dataHandler = null;
    protected MetadataHandler _metadataHandler = null;
    protected QueryBlockchainHandler _queryBlockchainHandler = null;
    protected LogLevelSetter _logLevelSetter = null;
    protected IndexerHandler _indexerHandler = null;

    public NodeRpcHandler(final StatisticsContainer statisticsContainer, final ThreadPool threadPool) {
        this(statisticsContainer, threadPool, new CoreInflater());
    }

    public NodeRpcHandler(final StatisticsContainer statisticsContainer, final ThreadPool threadPool, final MasterInflater masterInflater) {
        _averageBlockHeadersPerSecond = statisticsContainer.averageBlockHeadersPerSecond;
        _averageBlocksPerSecond = statisticsContainer.averageBlocksPerSecond;
        _averageTransactionsPerSecond = statisticsContainer.averageTransactionsPerSecond;
        _threadPool = threadPool;
        _masterInflater = masterInflater;
    }

    protected Json _doubleSpendProofToJson(final DoubleSpendProof doubleSpendProof) {
        final Sha256Hash doubleSpendProofHash = doubleSpendProof.getHash();
        final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent = doubleSpendProof.getTransactionOutputIdentifierBeingDoubleSpent();

        final Json transactionOutputIdentifierJson = new Json(false);
        {
            final Sha256Hash transactionHash = transactionOutputIdentifierBeingSpent.getTransactionHash();
            final Integer outputIndex = transactionOutputIdentifierBeingSpent.getOutputIndex();
            transactionOutputIdentifierJson.put("transactionHash", transactionHash);
            transactionOutputIdentifierJson.put("outputIndex", outputIndex);
        }

        final Json doubleSpendProofJson = new Json(false);
        doubleSpendProofJson.put("hash", doubleSpendProofHash);
        doubleSpendProofJson.put("transactionOutputIdentifier", transactionOutputIdentifierJson);

        return doubleSpendProofJson;
    }

    protected TransactionOutputIdentifier _parseTransactionOutputIdentifier(final String transactionOutputIdentifierString) {
        final int index = transactionOutputIdentifierString.indexOf(":");
        if (index < 0) { return null; }

        final String transactionHashString = transactionOutputIdentifierString.substring(0, index);
        final String outputIndexString = transactionOutputIdentifierString.substring(index + 1);

        if ( Util.isBlank(transactionHashString) || (! Util.isInt(outputIndexString)) ) { return null; }

        final Sha256Hash transactionHash = Sha256Hash.fromHexString(transactionHashString);
        final Integer outputIndex = Util.parseInt(outputIndexString);

        if (transactionHash == null) { return null; }

        return new TransactionOutputIdentifier(transactionHash, outputIndex);
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

        final int defaultMaxBlockCount = 10;
        final int maxMaxBlockCount = 1024;
        final int maxBlockCount;
        final String paramMaxBlockCountString = parameters.getString("maxBlockCount");
        if ( parameters.hasKey("maxBlockCount") && Util.isInt(paramMaxBlockCountString) ) {
            final Integer paramMaxBlockCount = Util.parseInt(paramMaxBlockCountString);
            if (paramMaxBlockCount < 0) {
                maxBlockCount = maxMaxBlockCount;
            }
            else {
                maxBlockCount = Math.min(paramMaxBlockCount, maxMaxBlockCount);
            }
        }
        else {
            maxBlockCount = defaultMaxBlockCount;
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
                    final BlockHeaderDeflater blockHeaderDeflater = _masterInflater.getBlockHeaderDeflater();
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

    // Requires GET: <height | hash>, [pageSize=128], [pageNumber=0]
    protected void _getBlockTransactions(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Long blockHeight;
        final Sha256Hash blockHash;
        {
            final boolean blockHashWasProvided = parameters.hasKey("hash");
            final String paramBlockHashString = parameters.getString("hash");
            blockHash = (blockHashWasProvided ? Sha256Hash.fromHexString(paramBlockHashString) : null);

            final boolean blockHeightWasProvided = parameters.hasKey("blockHeight");
            final String paramBlockHeightString = parameters.getString("blockHeight");
            blockHeight = (blockHeightWasProvided ? Util.parseLong(paramBlockHeightString) : null);

            if ( (! blockHeightWasProvided) && (! blockHashWasProvided) ) {
                response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: [hash|blockHeight]");
                return;
            }

            if ( blockHashWasProvided && (blockHash == null)) {
                response.put(ERROR_MESSAGE_KEY, "Invalid block hash: " + paramBlockHashString);
                return;
            }
            else if (blockHeightWasProvided && (blockHeight < 0) ) {
                response.put(ERROR_MESSAGE_KEY, "Invalid block height: " + paramBlockHeightString);
                return;
            }
        }

        final Integer pageSize;
        final Integer pageNumber;
        {
            final String paramPageSizeString = (parameters.hasKey("pageSize") ? parameters.getString("pageSize") : "128");
            final String paramPageNumberString = (parameters.hasKey("pageNumber") ? parameters.getString("pageNumber") : "0");

            pageSize = Util.parseInt(paramPageSizeString);
            pageNumber = Util.parseInt(paramPageNumberString);

            if (pageSize < 1) {
                response.put(ERROR_MESSAGE_KEY, "Invalid page size: " + paramPageSizeString);
                return;
            }

            if (pageNumber < 0) {
                response.put(ERROR_MESSAGE_KEY, "Invalid page number: " + paramPageNumberString);
                return;
            }
        }

        final List<Transaction> transactions;
        {
            if (blockHash != null) {
                transactions = dataHandler.getBlockTransactions(blockHash, pageSize, pageNumber);
            }
            else {
                transactions = dataHandler.getBlockTransactions(blockHeight, pageSize, pageNumber);
            }
        }
        if (transactions == null) {
            response.put(ERROR_MESSAGE_KEY, "Block not found: " + blockHash);
            return;
        }

        final Json transactionsJson = new Json(true);

        final MetadataHandler metadataHandler = _metadataHandler;
        for (final Transaction transaction : transactions) {
            final Json transactionJson = transaction.toJson();
            if (metadataHandler != null) {
                metadataHandler.applyMetadataToTransaction(transaction, transactionJson);
            }
            transactionsJson.add(transactionJson);
        }

        response.put("transactions", transactionsJson);
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
            final BlockHeaderDeflater blockHeaderDeflater = _masterInflater.getBlockHeaderDeflater();
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
            final BlockDeflater blockDeflater = _masterInflater.getBlockDeflater();
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
            final TransactionDeflater transactionDeflater = _masterInflater.getTransactionDeflater();
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

    protected void _getDoubleSpendProofs(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final List<DoubleSpendProof> doubleSpendProofs = dataHandler.getDoubleSpendProofs();

        final Json doubleSpendProofsJson = new Json(true);
        for (final DoubleSpendProof doubleSpendProof : doubleSpendProofs) {
            final Json doubleSpendProofJson = _doubleSpendProofToJson(doubleSpendProof);
            doubleSpendProofsJson.add(doubleSpendProofJson);
        }

        response.put("doubleSpendProofs", doubleSpendProofsJson);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires GET: <hash | transactionOutputIdentifier | transactionHash>
    protected void _getDoubleSpendProof(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if (parameters.hasKey("hash")) {
            final String hashString = parameters.getString("hash");
            final Sha256Hash hash = Sha256Hash.fromHexString(hashString);
            if ( (hash == null) && (! Util.isBlank(hashString)) ) {
                response.put(ERROR_MESSAGE_KEY, "Invalid hash: " + hashString);
                return;
            }

            final DoubleSpendProof doubleSpendProof = _dataHandler.getDoubleSpendProof(hash);
            if (doubleSpendProof == null) {
                response.put(ERROR_MESSAGE_KEY, "Double spend proof not found.");
                return;
            }

            final Json doubleSpendProofJson = _doubleSpendProofToJson(doubleSpendProof);
            response.put("doubleSpendProof", doubleSpendProofJson);
            response.put(WAS_SUCCESS_KEY, 1);
            return;
        }

        if (parameters.hasKey("transactionOutputIdentifier")) {
            final String transactionOutputIdentifierString = parameters.getString("transactionOutputIdentifier");
            final TransactionOutputIdentifier transactionOutputIdentifier = _parseTransactionOutputIdentifier(transactionOutputIdentifierString);

            if ( (transactionOutputIdentifier == null) && (! Util.isBlank(transactionOutputIdentifierString)) ) {
                response.put(ERROR_MESSAGE_KEY, "Invalid transaction output identifier: " + transactionOutputIdentifierString);
                return;
            }

            final DoubleSpendProof doubleSpendProof = _dataHandler.getDoubleSpendProof(transactionOutputIdentifier);
            if (doubleSpendProof == null) {
                response.put("doubleSpendProof", null); // NOTE: Not-found is still considered a success.
            }
            else {
                final Json doubleSpendProofJson = _doubleSpendProofToJson(doubleSpendProof);
                response.put("doubleSpendProof", doubleSpendProofJson);
            }

            response.put(WAS_SUCCESS_KEY, 1);
            return;
        }

        if (parameters.hasKey("transactionHash")) {
            final String transactionHashString = parameters.getString("transactionHash");
            final Sha256Hash transactionHash = Sha256Hash.fromHexString(transactionHashString);

            if ( (transactionHash == null) && (! Util.isBlank(transactionHashString)) ) {
                response.put(ERROR_MESSAGE_KEY, "Invalid hash: " + transactionHashString);
                return;
            }

            final Transaction transaction = _dataHandler.getTransaction(transactionHash);
            if (transaction == null) {
                response.put(ERROR_MESSAGE_KEY, "Unknown transaction: " + transactionHashString);
                return;
            }

            final Json doubleSpendProofsJson = new Json(true);
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                final DoubleSpendProof doubleSpendProof = _dataHandler.getDoubleSpendProof(transactionOutputIdentifier);
                if (doubleSpendProof != null) {
                    final Json doubleSpendProofJson = _doubleSpendProofToJson(doubleSpendProof);
                    doubleSpendProofsJson.add(doubleSpendProofJson);
                }
            }
            response.put("doubleSpendProofs", doubleSpendProofsJson);
            response.put(WAS_SUCCESS_KEY, 1);
            return;
        }

        response.put(ERROR_MESSAGE_KEY, "Missing parameter. Required: hash | transactionOutputIdentifier | transactionHash");
    }

    // Requires GET:
    protected void _queryBlockHeight(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if (parameters.hasKey("hash")) {
            final String blockHashString = parameters.getString("hash");
            final Sha256Hash blockHash = Sha256Hash.fromHexString(blockHashString);
            final Long blockHeaderHeight = dataHandler.getBlockHeaderHeight(blockHash);

            response.put("blockHeaderHeight", blockHeaderHeight);
            response.put(WAS_SUCCESS_KEY, 1);
        }
        else {
            final Long blockHeight = dataHandler.getBlockHeight();
            final Long blockHeaderHeight = dataHandler.getBlockHeaderHeight();

            response.put("blockHeight", blockHeight);
            response.put("blockHeaderHeight", blockHeaderHeight);
            response.put(WAS_SUCCESS_KEY, 1);
        }
    }

    // Requires GET:
    protected void _queryUtxoCache(final Json parameters, final Json response) {
        final UtxoCacheHandler utxoCacheHandler = _utxoCacheHandler;
        if (utxoCacheHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Long utxoCacheCount = utxoCacheHandler.getCachedUtxoCount();
        final Long maxUtxoCacheCount = utxoCacheHandler.getMaxCachedUtxoCount();
        final Long uncommittedUtxoCount = utxoCacheHandler.getUncommittedUtxoCount();

        final Long committedUtxoBlockHeight = utxoCacheHandler.getCommittedUtxoBlockHeight();

        response.put("utxoCacheCount", utxoCacheCount);
        response.put("maxUtxoCacheCount", maxUtxoCacheCount);
        response.put("uncommittedUtxoCount", uncommittedUtxoCount);
        response.put("committedUtxoBlockHeight", committedUtxoBlockHeight);

        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires GET:
    protected void _calculateNextDifficulty(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Difficulty difficulty = dataHandler.getDifficulty();

        response.put("difficulty", difficulty.encode());
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires GET: [rawFormat=0]
    protected void _getPrototypeBlock(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Boolean shouldReturnInRawFormat = parameters.getBoolean("rawFormat");

        final Block block = dataHandler.getPrototypeBlock();
        if (block == null) {
            response.put(ERROR_MESSAGE_KEY, "Unable to generate template.");
            return;
        }

        if (shouldReturnInRawFormat) {
            final BlockDeflater blockDeflater = _masterInflater.getBlockDeflater();
            final ByteArray bytes = blockDeflater.toBytes(block);
            response.put("block", bytes);
        }
        else {
            response.put("block", block);
        }

        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires GET:
    protected void _calculateNextBlockReward(final Json parameters, final Json response) {
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
                final TransactionDeflater transactionDeflater = _masterInflater.getTransactionDeflater();
                final ByteArray transactionData = transactionDeflater.toBytes(unconfirmedTransaction.transaction);

                final Json unconfirmedTransactionJson = new Json();
                unconfirmedTransactionJson.put("transactionData", transactionData);
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
    protected void _queryStatus(final Json parameters, final Json response) {
        { // Status
            response.put("status", (_synchronizationStatusHandler != null ? _synchronizationStatusHandler.getState() : null));
        }

        { // Statistics
            final DataHandler dataHandler = _dataHandler;

            final Long blockHeight = (dataHandler != null ? dataHandler.getBlockHeight() : null);
            final Long blockHeaderHeight = (dataHandler != null ? dataHandler.getBlockHeaderHeight() : null);

            final long blockTimestampInSeconds = (dataHandler != null ? Util.coalesce(dataHandler.getBlockTimestamp()) : 0L);
            final long blockHeaderTimestampInSeconds = (dataHandler != null ? Util.coalesce(dataHandler.getBlockHeaderTimestamp()) : 0L);

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

        { // Utxo Cache Status
            final Json queryUtxoCacheParameters = new Json();
            final Json utxoCacheStatus = new Json();
            _queryUtxoCache(queryUtxoCacheParameters, utxoCacheStatus);
            response.put("utxoCacheStatus", utxoCacheStatus);
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
        final AddressInflater addressInflater = _masterInflater.getAddressInflater();
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
        final AddressInflater addressInflater = _masterInflater.getAddressInflater();
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
        addressJson.put("base32CheckEncoded", address.toBase32CheckEncoded(true));
        addressJson.put("base58CheckEncoded", address.toBase58CheckEncoded());
        addressJson.put("balance", queryAddressHandler.getBalance(address));

        { // Address Transactions
            final Json transactionsJson = new Json(true);

            final MetadataHandler metadataHandler = _metadataHandler;
            for (final Transaction transaction : addressTransactions) {
                if (transaction == null) { continue; }

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

    // Requires GET: <hash>
    protected void _queryIsSlpTransaction(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if (! parameters.hasKey("hash")) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: hash");
            return;
        }

        final String hashString = parameters.getString("hash");
        final Sha256Hash transactionHash = Sha256Hash.fromHexString(hashString);

        if (transactionHash == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid transaction hash: " + hashString);
            return;
        }

        final Boolean isSlpTransaction = _dataHandler.isSlpTransaction(transactionHash);

        if (isSlpTransaction == null) {
            response.put(ERROR_MESSAGE_KEY, "Unable to determine SLP transaction status.");
            return;
        }

        response.put("isSlpTransaction", isSlpTransaction);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires GET: <hash>
    protected void _queryIsValidSlpTransaction(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if (! parameters.hasKey("hash")) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: hash");
            return;
        }

        final String hashString = parameters.getString("hash");
        final Sha256Hash transactionHash = Sha256Hash.fromHexString(hashString);

        if (transactionHash == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid transaction hash: " + hashString);
            return;
        }

        final Boolean isValidSlpTransaction = _dataHandler.isValidSlpTransaction(transactionHash);

        if (isValidSlpTransaction == null) {
            response.put(ERROR_MESSAGE_KEY, "Unable to determine SLP transaction validity.");
            return;
        }

        response.put("isValidSlpTransaction", isValidSlpTransaction);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires GET: <hash>
    protected void _querySlpTokenId(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if (! parameters.hasKey("hash")) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: hash");
            return;
        }

        final String hashString = parameters.getString("hash");
        final Sha256Hash transactionHash = Sha256Hash.fromHexString(hashString);

        if (transactionHash == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid transaction hash: " + hashString);
            return;
        }

        final SlpTokenId slpTokenId = _dataHandler.getSlpTokenId(transactionHash);

        if (slpTokenId == null) {
            response.put(ERROR_MESSAGE_KEY, "Unable to determine SLP Token Id.");
            return;
        }

        response.put("slpTokenId", slpTokenId);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires GET:
    protected void _queryBlockchainMetadata(final Json parameters, final Json response) {
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

    // Requires POST:
    protected void _commitUtxoCache(final Json parameters, final Json response) {
        final UtxoCacheHandler utxoCacheHandler = _utxoCacheHandler;
        if (utxoCacheHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        utxoCacheHandler.commitUtxoCache();
        response.put(WAS_SUCCESS_KEY, 1);
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

        final Ip ip = Ip.fromStringOrHost(host);
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

        final Ip ip = Ip.fromStringOrHost(host);
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

        final Ip ip = Ip.fromStringOrHost(host);
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
        final Boolean shouldIncludeTransactionFees = parameters.getBoolean("includeTransactionFees");

        final List<Address> addressFilter;
        {
            final AddressInflater addressInflater = _masterInflater.getAddressInflater();
            final Json addressFilterJson = parameters.getOrNull("addressFilter", Json.Types.JSON);
            if ( (addressFilterJson != null) && addressFilterJson.isArray() ) {
                final int itemCount = addressFilterJson.length();
                final ImmutableListBuilder<Address> listBuilder = new ImmutableListBuilder<Address>(itemCount);
                for (int i = 0; i < itemCount; ++i) {
                    final String addressString = addressFilterJson.getString(i);
                    final Address address = addressInflater.fromBase58Check(addressString);
                    if (address != null) {
                        listBuilder.add(address);
                    }
                }
                addressFilter = listBuilder.build();
            }
            else {
                addressFilter = null;
            }
        }

        synchronized (_eventHooks) {
            for (final HookEvent hookEvent : hookEvents) {
                if (! _eventHooks.containsKey(hookEvent)) {
                    _eventHooks.put(hookEvent, new MutableList<HookListener>());
                }

                final MutableList<HookListener> nodeIpAddresses = _eventHooks.get(hookEvent);
                nodeIpAddresses.add(new HookListener(connection, shouldReturnRawData, shouldIncludeTransactionFees, addressFilter));
            }
        }

        response.put(WAS_SUCCESS_KEY, 1);
        return true;
    }

    /**
     * Replaces any existing HookListeners associated with the connection with the new HookListener configuration.
     */
    protected Boolean _updateHook(final Json parameters, final Json response, final JsonSocket connection) {
        synchronized (_eventHooks) {
            // Uninstall the original HookListener...
            for (final MutableList<HookListener> hookListeners : _eventHooks.values()) {
                final Iterator<HookListener> mutableIterator = hookListeners.mutableIterator();
                while (mutableIterator.hasNext()) {
                    final HookListener hookListener = mutableIterator.next();
                    if (hookListener.socket == connection) {
                        mutableIterator.remove();
                    }
                }
            }
        }

        // Install the new HookListener...
        return _addHook(parameters, response, connection);
    }

    // Requires POST: <transaction>
    protected void _receiveTransaction(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if (! parameters.hasKey("transactionData")) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: transactionData");
            return;
        }

        final String transactionHexString = parameters.getString("transactionData");
        final ByteArray transactionBytes = MutableByteArray.wrap(HexUtil.hexStringToByteArray(transactionHexString));
        if (transactionBytes == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid Transaction bytes.");
            return;
        }

        final TransactionInflater transactionInflater = _masterInflater.getTransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(transactionBytes);
        if (transaction == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid Transaction");
            return;
        }

        dataHandler.submitTransaction(transaction);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires POST: <block>
    protected void _receiveBlock(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if (! parameters.hasKey("blockData")) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: blockData");
            return;
        }

        final String blockHexString = parameters.getString("blockData");
        final ByteArray blockBytes = MutableByteArray.wrap(HexUtil.hexStringToByteArray(blockHexString));
        if (blockBytes == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid Block bytes.");
            return;
        }

        final BlockInflater blockInflater = _masterInflater.getBlockInflater();
        final Block block = blockInflater.fromBytes(blockBytes);
        if (block == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid Block");
            return;
        }

        dataHandler.submitBlock(block);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires POST: <blockData>
    protected void _validatePrototypeBlock(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if (! parameters.hasKey("blockData")) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: blockData");
            return;
        }

        final String blockHexString = parameters.getString("blockData");
        final ByteArray blockBytes = MutableByteArray.wrap(HexUtil.hexStringToByteArray(blockHexString));
        if (blockBytes == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid Block bytes.");
            return;
        }

        final BlockInflater blockInflater = _masterInflater.getBlockInflater();
        final Block block = blockInflater.fromBytes(blockBytes);
        if (block == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid Block.");
            return;
        }

        final BlockValidationResult blockValidationResult = dataHandler.validatePrototypeBlock(block);
        response.put("blockValidation", blockValidationResult);

        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires POST: <transactionData>, [enableSlpValidation=1]
    protected void _validateTransaction(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if (! parameters.hasKey("transactionData")) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: transactionData");
            return;
        }

        final String transactionHexString = parameters.getString("transactionData");
        final ByteArray transactionBytes = MutableByteArray.wrap(HexUtil.hexStringToByteArray(transactionHexString));
        if (transactionBytes == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid Transaction bytes.");
            return;
        }

        final TransactionInflater transactionInflater = _masterInflater.getTransactionInflater();
        final Transaction transaction = transactionInflater.fromBytes(transactionBytes);
        if (transaction == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid Transaction.");
            return;
        }

        final String enableSlpValidationKey = "enableSlpValidation";
        final Boolean enableSlpValidation = (parameters.hasKey(enableSlpValidationKey) ? parameters.getBoolean(enableSlpValidationKey) : true);

        final ValidationResult blockValidationResult = dataHandler.validateTransaction(transaction, enableSlpValidation);
        response.put("transactionValidation", blockValidationResult);

        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires POST: <packageName, logLevel>
    protected void _setLogLevel(final Json parameters, final Json response) {
        final LogLevelSetter logLevelSetter = _logLevelSetter;
        if (logLevelSetter == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if (! parameters.hasKey("packageName")) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: packageName");
            return;
        }

        if (! parameters.hasKey("logLevel")) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: logLevel");
            return;
        }

        final String packageName = parameters.getString("packageName");
        final String logLevel = parameters.getString("logLevel");

        logLevelSetter.setLogLevel(packageName, logLevel);

        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires POST: <blockHash>
    protected void _reconsiderBlock(final Json parameters, final Json response) {
        final DataHandler dataHandler = _dataHandler;
        if (dataHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        if (! parameters.hasKey("blockHash")) {
            response.put(ERROR_MESSAGE_KEY, "Missing parameters. Required: blockHash");
            return;
        }

        final Sha256Hash blockHash = Sha256Hash.fromHexString(parameters.getString("blockHash"));
        if (blockHash == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid Block hash.");
            return;
        }

        dataHandler.reconsiderBlock(blockHash);

        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires POST:
    protected  void _clearTransactionIndexes(final Json parameters, final Json response) {
        final IndexerHandler indexerHandler = _indexerHandler;
        if (indexerHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Boolean wasSuccessful = indexerHandler.clearTransactionIndexes();
        response.put(WAS_SUCCESS_KEY, (wasSuccessful ? 1 : 0));
    }

    // Requires POST:
    protected  void _clearSlpValidation(final Json parameters, final Json response) {
        final IndexerHandler indexerHandler = _indexerHandler;
        if (indexerHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Boolean wasSuccessful = indexerHandler.clearAllSlpValidation();
        response.put(WAS_SUCCESS_KEY, (wasSuccessful ? 1 : 0));
    }

    // Requires POST: <host>
    protected void _addIpToWhitelist(final Json parameters, final Json response) {
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

        final Ip ip = Ip.fromStringOrHost(host);
        if (ip == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid ip: " + host);
            return;
        }

        nodeHandler.addIpToWhitelist(ip);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires POST: <host>
    protected void _removeIpFromWhitelist(final Json parameters, final Json response) {
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

        final Ip ip = Ip.fromStringOrHost(host);
        if (ip == null) {
            response.put(ERROR_MESSAGE_KEY, "Invalid ip: " + host);
            return;
        }

        nodeHandler.removeIpFromWhitelist(ip);
        response.put(WAS_SUCCESS_KEY, 1);
    }

    // Requires GET:
    protected void _listNodes(final Json parameters, final Json response) {
        final NodeHandler nodeHandler = _nodeHandler;
        if (nodeHandler == null) {
            response.put(ERROR_MESSAGE_KEY, "Operation not supported.");
            return;
        }

        final Json nodeListJson = new Json();

        final List<BitcoinNode> bitcoinNodes = nodeHandler.getNodes();
        for (final BitcoinNode bitcoinNode : bitcoinNodes) {
            final NodeIpAddress nodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();
            if (nodeIpAddress == null) { continue; }

            final Json nodeJson = new Json();
            nodeJson.put("isPreferred", nodeHandler.isPreferredNode(bitcoinNode));
            nodeJson.put("host", nodeIpAddress.getIp());
            nodeJson.put("port", nodeIpAddress.getPort());
            nodeJson.put("userAgent", bitcoinNode.getUserAgent());
            nodeJson.put("initializationTimestamp", (bitcoinNode.getInitializationTimestamp() / 1000L));
            nodeJson.put("lastMessageReceivedTimestamp", (bitcoinNode.getLastMessageReceivedTimestamp() / 1000L));
            nodeJson.put("networkOffset", bitcoinNode.getNetworkTimeOffset());
            nodeJson.put("ping", Util.coalesce(bitcoinNode.getAveragePing(), 0L));
            nodeJson.put("blockHeight", bitcoinNode.getBlockHeight());

            final NodeIpAddress localNodeIpAddress = bitcoinNode.getLocalNodeIpAddress();
            nodeJson.put("localHost", (localNodeIpAddress != null ? localNodeIpAddress.getIp() : null));
            nodeJson.put("localPort", (localNodeIpAddress != null ? localNodeIpAddress.getPort() : null));

            nodeJson.put("handshakeIsComplete", (bitcoinNode.handshakeIsComplete() ? 1 : 0));
            nodeJson.put("id", bitcoinNode.getId());

            final Json featuresJson = new Json(false);
            for (final NodeFeatures.Feature nodeFeature : NodeFeatures.Feature.values()) {
                final Boolean hasFeatureEnabled = (bitcoinNode.hasFeatureEnabled(nodeFeature));
                final String featureKey = nodeFeature.name();
                if (hasFeatureEnabled == null) {
                    featuresJson.put(featureKey, null);
                }
                else {
                    featuresJson.put(featureKey, (hasFeatureEnabled ? 1 : 0));
                }
            }
            featuresJson.put("THIN_PROTOCOL_ENABLED", (bitcoinNode.supportsExtraThinBlocks() ? 1 : 0));
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

    public void setUtxoCacheHandler(final UtxoCacheHandler utxoCacheHandler) {
        _utxoCacheHandler = utxoCacheHandler;
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

    public void setLogLevelSetter(final LogLevelSetter logLevelSetter) {
        _logLevelSetter = logLevelSetter;
    }

    public void setIndexerHandler(final IndexerHandler indexerHandler) {
        _indexerHandler = indexerHandler;
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
                final BlockHeaderDeflater blockHeaderDeflater = _masterInflater.getBlockHeaderDeflater();
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
                            Logger.debug("Dropping HookEvent: " + HookEvent.NEW_BLOCK + " " + jsonSocket.toString());
                        }
                    }
                }
            }
        });
    }

    /**
     * Broadcasts the Transaction to all hook listeners subscribed to the TRANSACTION event.
     *  If the TransactionWithFee.transactionFee is null then the hook will receive a TRANSACTION object even if TRANSACTION_WITH_FEE is requested.
     */
    public void onNewTransaction(final TransactionWithFee transactionWithFee) {
        final Transaction transaction = transactionWithFee.transaction;
        final Long transactionFee = transactionWithFee.transactionFee;

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
                final TransactionDeflater transactionDeflater = _masterInflater.getTransactionDeflater();
                final ByteArray transactionBytes = transactionDeflater.toBytes(transaction);

                final Json json = new Json();
                json.put("objectType", "TRANSACTION");
                json.put("object", transactionBytes);

                return new JsonProtocolMessage(json);
            }
        };

        final LazyProtocolMessage lazyRawProtocolMessageWithFee;
        if (transactionFee != null) {
            lazyRawProtocolMessageWithFee = new LazyProtocolMessage() {
                @Override
                protected ProtocolMessage _createProtocolMessage() {
                    final TransactionDeflater transactionDeflater = _masterInflater.getTransactionDeflater();
                    final ByteArray transactionData = transactionDeflater.toBytes(transaction);

                    final Json transactionJson = new Json();
                    transactionJson.put("transactionData", transactionData);
                    transactionJson.put("transactionFee", transactionFee);

                    final Json json = new Json();
                    json.put("objectType", "TRANSACTION_WITH_FEE");
                    json.put("object", transactionJson);

                    return new JsonProtocolMessage(json);
                }
            };
        }
        else {
            lazyRawProtocolMessageWithFee = lazyRawProtocolMessage;
        }

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

                        final BloomFilter addressFilter = hookListener.addressFilter;
                        if (addressFilter != null) {
                            if (! transaction.matches(addressFilter)) {
                                continue;
                            }
                        }

                        final ProtocolMessage protocolMessage;
                        if (hookListener.rawFormat) {
                            protocolMessage = (hookListener.includeTransactionFees ? lazyRawProtocolMessageWithFee.getProtocolMessage() : lazyRawProtocolMessage.getProtocolMessage());
                        }
                        else {
                            protocolMessage = lazyMetadataProtocolMessage.getProtocolMessage();
                        }
                        jsonSocket.write(protocolMessage);

                        if (! jsonSocket.isConnected()) {
                            iterator.remove();
                            Logger.debug("Dropping HookEvent: " + HookEvent.NEW_TRANSACTION + " " + jsonSocket.toString());
                        }
                    }
                }
            }
        });
    }

    public void onNewDoubleSpendProof(final DoubleSpendProof doubleSpendProof) {
        final Sha256Hash doubleSpendProofHash = doubleSpendProof.getHash();
        final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent = doubleSpendProof.getTransactionOutputIdentifierBeingDoubleSpent();

        final LazyProtocolMessage lazyProtocolMessage = new LazyProtocolMessage() {
            @Override
            protected ProtocolMessage _createProtocolMessage() {
                final Json json = new Json();
                json.put("objectType", "DOUBLE_SPEND_PROOF");

                final Json doubleSpendProofJson = _doubleSpendProofToJson(doubleSpendProof);

                json.put("object", doubleSpendProofJson);

                return new JsonProtocolMessage(json);
            }
        };

        _threadPool.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (_eventHooks) {
                    final MutableList<HookListener> sockets = _eventHooks.get(HookEvent.NEW_DOUBLE_SPEND_PROOF);
                    if (sockets == null) { return; }

                    final Iterator<HookListener> iterator = sockets.mutableIterator();
                    while (iterator.hasNext()) {
                        final HookListener hookListener = iterator.next();
                        final JsonSocket jsonSocket = hookListener.socket;

                        final ProtocolMessage protocolMessage = lazyProtocolMessage.getProtocolMessage();

                        jsonSocket.write(protocolMessage);

                        if (! jsonSocket.isConnected()) {
                            iterator.remove();
                            Logger.debug("Dropping HookEvent: " + HookEvent.NEW_DOUBLE_SPEND_PROOF + " " + jsonSocket.toString());
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
                final JsonProtocolMessage protocolMessage = socketConnection.popMessage();
                final Json message = protocolMessage.getMessage();

                final String method = message.getString("method");
                final String query = message.getString("query");

                final Json response = new Json();
                response.put(WAS_SUCCESS_KEY, 0);
                response.put(ERROR_MESSAGE_KEY, null);

                final Json parameters = message.get("parameters");
                boolean closeConnection = true;

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

                            case "BLOCK_TRANSACTIONS": {
                                _getBlockTransactions(parameters, response);
                            } break;

                            case "TRANSACTION": {
                                _getTransaction(parameters, response);
                            } break;

                            case "DOUBLE_SPEND_PROOFS": {
                                _getDoubleSpendProofs(parameters, response);
                            } break;

                            case "DOUBLE_SPEND_PROOF": {
                                _getDoubleSpendProof(parameters, response);
                            } break;

                            case "BLOCK_HEIGHT": {
                                _queryBlockHeight(parameters, response);
                            } break;

                            case "UTXO_CACHE": {
                                _queryUtxoCache(parameters, response);
                            } break;

                            case "DIFFICULTY": {
                                _calculateNextDifficulty(parameters, response);
                            } break;

                            case "PROTOTYPE_BLOCK": {
                                _getPrototypeBlock(parameters, response);
                            } break;

                            case "BLOCK_REWARD": {
                                _calculateNextBlockReward(parameters, response);
                            } break;

                            case "MEMPOOL":
                            case "UNCONFIRMED_TRANSACTIONS": {
                                _getUnconfirmedTransactions(parameters, response);
                            } break;

                            case "STATUS": {
                                _queryStatus(parameters, response);
                            } break;

                            case "NODES": {
                                _listNodes(parameters, response);
                            } break;

                            case "BALANCE": {
                                _queryBalance(parameters, response);
                            } break;

                            case "ADDRESS": {
                                _queryAddressTransactions(parameters, response);
                            } break;

                            case "BLOCKCHAIN": {
                                _queryBlockchainMetadata(parameters, response);
                            } break;

                            case "IS_SLP_TRANSACTION": {
                                _queryIsSlpTransaction(parameters, response);
                            } break;

                            case "IS_VALID_SLP_TRANSACTION": {
                                _queryIsValidSlpTransaction(parameters, response);
                            } break;

                            case "SLP_TOKEN_ID": {
                                _querySlpTokenId(parameters, response);
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

                            case "COMMIT_UTXO_CACHE": {
                                _commitUtxoCache(parameters, response);
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

                            case "WHITELIST_NODE": {
                                _addIpToWhitelist(parameters, response);
                            } break;

                            case "REMOVE_WHITELIST_NODE": {
                                _removeIpFromWhitelist(parameters, response);
                            } break;

                            case "ADD_HOOK": {
                                final Boolean keepSocketOpen = _addHook(parameters, response, socketConnection);
                                closeConnection = (! keepSocketOpen);
                            } break;

                            case "UPDATE_HOOK": {
                                final Boolean keepSocketOpen = _updateHook(parameters, response, socketConnection);
                                closeConnection = (! keepSocketOpen);
                            } break;

                            case "TRANSACTION": {
                                _receiveTransaction(parameters, response);
                            } break;

                            case "BLOCK": {
                                _receiveBlock(parameters, response);
                            } break;

                            case "VALIDATE_PROTOTYPE_BLOCK": {
                                _validatePrototypeBlock(parameters, response);
                            } break;

                            case "VALIDATE_TRANSACTION": {
                                _validateTransaction(parameters, response);
                            } break;

                            case "SET_LOG_LEVEL": {
                                _setLogLevel(parameters, response);
                            } break;

                            case "RECONSIDER_BLOCK": {
                                _reconsiderBlock(parameters, response);
                            } break;

                            case "CLEAR_TRANSACTION_INDEXES": {
                                _clearTransactionIndexes(parameters, response);
                            } break;

                            case "CLEAR_SLP_VALIDATION": {
                                _clearSlpValidation(parameters, response);
                            } break;

                            // TODO: Add invalidate-block command (see: feature/invalidate-block/master).
                            // TODO: Add rebuild-UTXO set from block-height command.

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

                if (closeConnection) { // TODO: Allow for keeping the connection alive...
                    socketConnection.flush();
                    socketConnection.close();
                }
            }
        });
        socketConnection.beginListening();
    }
}
