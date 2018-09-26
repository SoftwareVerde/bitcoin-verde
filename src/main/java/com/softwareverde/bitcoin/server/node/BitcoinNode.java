package com.softwareverde.bitcoin.server.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.compact.EnableCompactBlocksMessage;
import com.softwareverde.bitcoin.server.message.type.error.ErrorMessage;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddressMessage;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.ping.BitcoinPingMessage;
import com.softwareverde.bitcoin.server.message.type.node.pong.BitcoinPongMessage;
import com.softwareverde.bitcoin.server.message.type.query.block.QueryBlocksMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.InventoryMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.block.BlockMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.block.header.BlockHeadersMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemType;
import com.softwareverde.bitcoin.server.message.type.query.response.transaction.TransactionMessage;
import com.softwareverde.bitcoin.server.message.type.request.RequestDataMessage;
import com.softwareverde.bitcoin.server.message.type.request.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.server.message.type.thin.block.ExtraThinBlockMessage;
import com.softwareverde.bitcoin.server.message.type.thin.block.ThinBlockMessage;
import com.softwareverde.bitcoin.server.message.type.thin.request.block.RequestExtraThinBlockMessage;
import com.softwareverde.bitcoin.server.message.type.thin.request.transaction.RequestExtraThinTransactionsMessage;
import com.softwareverde.bitcoin.server.message.type.thin.transaction.ThinTransactionsMessage;
import com.softwareverde.bitcoin.server.message.type.version.acknowledge.BitcoinAcknowledgeVersionMessage;
import com.softwareverde.bitcoin.server.message.type.version.synchronize.BitcoinSynchronizeVersionMessage;
import com.softwareverde.bitcoin.server.module.node.handler.InventoryMessageHandler;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.callback.Callback;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.network.ip.Ipv4;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.message.type.PingMessage;
import com.softwareverde.network.p2p.message.type.PongMessage;
import com.softwareverde.network.p2p.message.type.SynchronizeVersionMessage;
import com.softwareverde.network.p2p.node.Node;
import com.softwareverde.network.p2p.node.NodeConnection;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.util.HexUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BitcoinNode extends Node {
    public static Boolean LOGGING_ENABLED = false;

    public interface FailableCallback {
        default void onFailure() { }
    }
    public interface InventoryMessageCallback extends Callback<List<Sha256Hash>>, FailableCallback { }
    public interface DownloadBlockCallback extends Callback<Block> {
        default void onFailure(Sha256Hash blockHash) { }
    }
    public interface DownloadBlockHeadersCallback extends Callback<List<BlockHeaderWithTransactionCount>>, FailableCallback { }
    public interface DownloadTransactionCallback extends Callback<Transaction>, FailableCallback { }
    public interface DownloadThinBlockCallback extends Callback<ThinBlockParameters>, FailableCallback { }
    public interface DownloadExtraThinBlockCallback extends Callback<ExtraThinBlockParameters>, FailableCallback { }
    public interface DownloadThinTransactionsCallback extends Callback<List<Transaction>>, FailableCallback { }
    public interface TransactionsAnnouncementCallback extends Callback<List<Sha256Hash>> { }

    public static SynchronizationStatus DEFAULT_STATUS_CALLBACK = new SynchronizationStatus() {
        @Override
        public State getState() { return State.ONLINE; }

        @Override
        public Boolean isBlockChainSynchronized() { return false; }

        @Override
        public Boolean isReadyForTransactions() { return false; }

        @Override
        public Integer getCurrentBlockHeight() { return 0; }
    };

    public interface QueryBlocksCallback {
        void run(com.softwareverde.constable.list.List<Sha256Hash> blockHashes, Sha256Hash desiredBlockHash, NodeConnection nodeConnection);
    }

    public interface QueryBlockHeadersCallback {
        void run(com.softwareverde.constable.list.List<Sha256Hash> blockHashes, Sha256Hash desiredBlockHash, NodeConnection nodeConnection);
    }

    public interface RequestDataCallback {
        void run(List<InventoryItem> dataHashes, NodeConnection nodeConnection);
    }

    public interface RequestExtraThinBlockCallback {
        void run(Sha256Hash blockHash, BloomFilter bloomFilter, NodeConnection nodeConnection);
    }

    public interface RequestExtraThinTransactionCallback {
        void run(Sha256Hash blockHash, List<ByteArray> transactionShortHashes, NodeConnection nodeConnection);
    }

    public static class ThinBlockParameters {
        public final BlockHeader blockHeader;
        public final List<Sha256Hash> transactionHashes;
        public final List<Transaction> transactions;

        public ThinBlockParameters(final BlockHeader blockHeader, List<Sha256Hash> transactionHashes, List<Transaction> transactions) {
            this.blockHeader = blockHeader;
            this.transactionHashes = transactionHashes;
            this.transactions = transactions;
        }
    }

    public static class ExtraThinBlockParameters {
        public final BlockHeader blockHeader;
        public final List<ByteArray> transactionHashes;
        public final List<Transaction> transactions;

        public ExtraThinBlockParameters(final BlockHeader blockHeader, List<ByteArray> transactionHashes, List<Transaction> transactions) {
            this.blockHeader = blockHeader;
            this.transactionHashes = transactionHashes;
            this.transactions = transactions;
        }
    }

    protected static <U, T, S extends Callback<U>> void _executeAndClearCallbacks(final Map<T, Set<S>> callbackMap, final T key, final U value) {
        final Set<S> callbackSet = callbackMap.remove(key);
        if ( (callbackSet == null) || (callbackSet.isEmpty()) ) { return; }

        for (final S callback : callbackSet) {
            callback.onResult(value);
        }
    }

    protected SynchronizationStatus _synchronizationStatus = DEFAULT_STATUS_CALLBACK;

    protected QueryBlocksCallback _queryBlocksCallback = null;
    protected QueryBlockHeadersCallback _queryBlockHeadersCallback = null;
    protected RequestDataCallback _requestDataMessageCallback = null;
    protected InventoryMessageCallback _inventoryMessageHandler = null;

    protected RequestExtraThinBlockCallback _requestExtraThinBlockCallback = null;
    protected RequestExtraThinTransactionCallback _requestExtraThinTransactionCallback = null;

    protected BitcoinSynchronizeVersionMessage _synchronizeVersionMessage = null;

    protected TransactionsAnnouncementCallback _transactionsAnnouncementCallback;

    protected final Map<Sha256Hash, Set<DownloadBlockCallback>> _downloadBlockRequests = new HashMap<Sha256Hash, Set<DownloadBlockCallback>>();
    protected final Map<Sha256Hash, Set<DownloadBlockHeadersCallback>> _downloadBlockHeadersRequests = new HashMap<Sha256Hash, Set<DownloadBlockHeadersCallback>>();
    protected final Map<InventoryItemType, Set<InventoryItem>> _availableDataHashes = new HashMap<InventoryItemType, Set<InventoryItem>>();
    protected final Map<Sha256Hash, Set<DownloadTransactionCallback>> _downloadTransactionRequests = new HashMap<Sha256Hash, Set<DownloadTransactionCallback>>();
    protected final Map<Sha256Hash, Set<DownloadThinBlockCallback>> _downloadThinBlockRequests = new HashMap<Sha256Hash, Set<DownloadThinBlockCallback>>();
    protected final Map<Sha256Hash, Set<DownloadExtraThinBlockCallback>> _downloadExtraThinBlockRequests = new HashMap<Sha256Hash, Set<DownloadExtraThinBlockCallback>>();
    protected final Map<Sha256Hash, Set<DownloadThinTransactionsCallback>> _downloadThinTransactionsRequests = new HashMap<Sha256Hash, Set<DownloadThinTransactionsCallback>>();

    protected Boolean _announceNewBlocksViaHeadersIsEnabled = false;
    protected Integer _compactBlocksVersion = null;

    @Override
    protected BitcoinPingMessage _createPingMessage() {
        return new BitcoinPingMessage();
    }

    @Override
    protected BitcoinPongMessage _createPongMessage(final PingMessage pingMessage) {
        final BitcoinPongMessage pongMessage = new BitcoinPongMessage();
        pongMessage.setNonce(pingMessage.getNonce());
        return pongMessage;
    }

    @Override
    protected BitcoinSynchronizeVersionMessage _createSynchronizeVersionMessage() {
        final BitcoinSynchronizeVersionMessage synchronizeVersionMessage = new BitcoinSynchronizeVersionMessage();

        synchronizeVersionMessage.setRelayIsEnabled(_synchronizationStatus.isReadyForTransactions());
        synchronizeVersionMessage.setCurrentBlockHeight(_synchronizationStatus.getCurrentBlockHeight());

        { // Set Remote NodeIpAddress...
            final BitcoinNodeIpAddress remoteNodeIpAddress = new BitcoinNodeIpAddress();
            remoteNodeIpAddress.setIp(Ipv4.parse(_connection.getRemoteIp()));
            remoteNodeIpAddress.setPort(_connection.getPort());
            remoteNodeIpAddress.setNodeFeatures(new NodeFeatures());
            synchronizeVersionMessage.setRemoteAddress(remoteNodeIpAddress);
        }

        { // Set Local NodeIpAddress...
            // TODO
        }

        return synchronizeVersionMessage;
    }

    @Override
    protected BitcoinAcknowledgeVersionMessage _createAcknowledgeVersionMessage(final SynchronizeVersionMessage synchronizeVersionMessage) {
        if (synchronizeVersionMessage instanceof BitcoinSynchronizeVersionMessage) {
            _synchronizeVersionMessage = (BitcoinSynchronizeVersionMessage) synchronizeVersionMessage;
        }
        else {
            Logger.log("NOTICE: Invalid SynchronizeVersionMessage type provided to BitcoinNode._createAcknowledgeVersionMessage.");
        }

        return new BitcoinAcknowledgeVersionMessage();
    }

    @Override
    protected BitcoinNodeIpAddressMessage _createNodeIpAddressMessage() {
        return new BitcoinNodeIpAddressMessage();
    }

    protected void _initConnection() {
        _connection.setMessageReceivedCallback(new NodeConnection.MessageReceivedCallback() {
            @Override
            public void onMessageReceived(final ProtocolMessage protocolMessage) {
                if (! (protocolMessage instanceof BitcoinProtocolMessage)) {
                    Logger.log("NOTICE: Disregarding Non-Bitcoin ProtocolMessage.");
                    return;
                }

                final BitcoinProtocolMessage message = (BitcoinProtocolMessage) protocolMessage;

                if (LOGGING_ENABLED) {
                    Logger.log("Received: " + message.getCommand());
                }

                _lastMessageReceivedTimestamp = _systemTime.getCurrentTimeInMilliSeconds();

                switch (message.getCommand()) {
                    case PING: {
                        _onPingReceived((BitcoinPingMessage) message);
                    } break;

                    case PONG: {
                        _onPongReceived((PongMessage) message);
                    } break;

                    case SYNCHRONIZE_VERSION: {
                        _onSynchronizeVersion((BitcoinSynchronizeVersionMessage) message);
                    } break;

                    case ACKNOWLEDGE_VERSION: {
                        _onAcknowledgeVersionMessageReceived((BitcoinAcknowledgeVersionMessage) message);
                    } break;

                    case NODE_ADDRESSES: {
                        _onNodeAddressesReceived((BitcoinNodeIpAddressMessage) message);
                    } break;

                    case ERROR: {
                        _onErrorMessageReceived((ErrorMessage) message);
                    } break;

                    case INVENTORY: {
                        _onQueryResponseMessageReceived((InventoryMessage) message);
                    } break;

                    case REQUEST_DATA: {
                        _onRequestDataMessageReceived((RequestDataMessage) message, _connection);
                    } break;

                    case BLOCK: {
                        _onBlockMessageReceived((BlockMessage) message);
                    } break;

                    case TRANSACTION: {
                        _onTransactionMessageReceived((TransactionMessage) message);
                    } break;

                    case BLOCK_HEADERS: {
                        _onBlockHeadersMessageReceived((BlockHeadersMessage) message);
                    } break;

                    case QUERY_BLOCKS: {
                        _onQueryBlocksMessageReceived((QueryBlocksMessage) message, _connection);
                    } break;

                    case REQUEST_BLOCK_HEADERS: {
                        _onQueryBlockHeadersMessageReceived((RequestBlockHeadersMessage) message, _connection);
                    } break;

                    case ENABLE_NEW_BLOCKS_VIA_HEADERS: {
                        _announceNewBlocksViaHeadersIsEnabled = true;
                    } break;

                    case ENABLE_COMPACT_BLOCKS: {
                        final EnableCompactBlocksMessage enableCompactBlocksMessage = (EnableCompactBlocksMessage) message;
                        _compactBlocksVersion = (enableCompactBlocksMessage.isEnabled() ? enableCompactBlocksMessage.getVersion() : null);
                    } break;

                    case REQUEST_EXTRA_THIN_BLOCK: {
                        _onRequestExtraThinBlockMessageReceived((RequestExtraThinBlockMessage) message, _connection);
                    } break;

                    case EXTRA_THIN_BLOCK: {
                        _onExtraThinBlockMessageReceived((ExtraThinBlockMessage) message);
                    } break;

                    case THIN_BLOCK: {
                        _onThinBlockMessageReceived((ThinBlockMessage) message);
                    } break;

                    case REQUEST_EXTRA_THIN_TRANSACTIONS: {
                        _onRequestExtraThinTransactionsMessageReceived((RequestExtraThinTransactionsMessage) message, _connection);
                    } break;

                    case THIN_TRANSACTIONS: {
                        _onThinTransactionsMessageReceived((ThinTransactionsMessage) message);
                    } break;

                    default: {
                        Logger.log("NOTICE: Unhandled Message Command: "+ message.getCommand() +": 0x"+ HexUtil.toHexString(message.getHeaderBytes()));
                    } break;
                }
            }
        });

        _connection.setOnConnectCallback(new Runnable() {
            @Override
            public void run() {
                _onConnect();
            }
        });
        if (_connection.isConnected()) {
            _onConnect();
        }

        _connection.setOnConnectFailureCallback(new Runnable() {
            @Override
            public void run() {
                _onDisconnect();
            }
        });

        _connection.setOnDisconnectCallback(new Runnable() {
            @Override
            public void run() {
                _onDisconnect();
            }
        });
    }

    public BitcoinNode(final String host, final Integer port) {
        super(host, port, BitcoinProtocolMessage.BINARY_PACKET_FORMAT);

        _initConnection();
    }

    public BitcoinNode(final BinarySocket binarySocket) {
        super(binarySocket);

        _initConnection();
    }

    protected void _onErrorMessageReceived(final ErrorMessage errorMessage) {
        final ErrorMessage.RejectCode rejectCode = errorMessage.getRejectCode();
        Logger.log("RECEIVED ERROR:"+ rejectCode.getRejectMessageType().getValue() +" "+ HexUtil.toHexString(new byte[] { rejectCode.getCode() }) +" "+ errorMessage.getRejectDescription() +" "+ HexUtil.toHexString(errorMessage.getExtraData()));
    }

    protected void _onRequestDataMessageReceived(final RequestDataMessage requestDataMessage, final NodeConnection nodeConnection) {
        final RequestDataCallback requestDataCallback = _requestDataMessageCallback;

        if (requestDataCallback != null) {
            final List<InventoryItem> dataHashes = new ImmutableList<InventoryItem>(requestDataMessage.getInventoryItems());
            requestDataCallback.run(dataHashes, nodeConnection);
        }
        else {
            Logger.log("NOTICE: No handler set for RequestData message.");
        }
    }

    protected void _onQueryResponseMessageReceived(final InventoryMessage inventoryMessage) {
        final Map<InventoryItemType, MutableList<Sha256Hash>> dataHashesMap = new HashMap<InventoryItemType, MutableList<Sha256Hash>>();

        final List<InventoryItem> dataHashes = inventoryMessage.getInventoryItems();
        for (final InventoryItem inventoryItem : dataHashes) {
            final InventoryItemType inventoryItemType = inventoryItem.getItemType();
            _storeInMapSet(_availableDataHashes, inventoryItemType, inventoryItem);
            _storeInMapList(dataHashesMap, inventoryItemType, inventoryItem.getItemHash());
        }

        for (final InventoryItemType inventoryItemType : dataHashesMap.keySet()) {
            final List<Sha256Hash> objectHashes = dataHashesMap.get(inventoryItemType);
            if (objectHashes.isEmpty()) { continue; }

            if (inventoryItemType == InventoryItemType.BLOCK) {
                final InventoryMessageCallback inventoryMessageHandler = _inventoryMessageHandler;
                if (inventoryMessageHandler != null) {
                    inventoryMessageHandler.onResult(objectHashes);
                }
                else {
                    Logger.log("NOTICE: No handler set for InventoryMessageHandler.");
                }
            }
            else if (inventoryItemType == InventoryItemType.TRANSACTION) {
                final TransactionsAnnouncementCallback transactionsAnnouncementCallback = _transactionsAnnouncementCallback;
                if (transactionsAnnouncementCallback != null) {
                    transactionsAnnouncementCallback.onResult(objectHashes);
                }
                else {
                    Logger.log("NOTICE: No handler set for TransactionsAnnouncementCallback.");
                }
            }
        }
    }

    protected void _onBlockMessageReceived(final BlockMessage blockMessage) {
        final Block block = blockMessage.getBlock();
        final Boolean blockHeaderIsValid = block.isValid();

        final Sha256Hash blockHash = block.getHash();
        _executeAndClearCallbacks(_downloadBlockRequests, blockHash, (blockHeaderIsValid ? block : null));
    }

    protected void _onTransactionMessageReceived(final TransactionMessage transactionMessage) {
        final Transaction transaction = transactionMessage.getTransaction();

        final Sha256Hash transactionHash = transaction.getHash();
        _executeAndClearCallbacks(_downloadTransactionRequests, transactionHash, transaction);
    }

    protected void _onBlockHeadersMessageReceived(final BlockHeadersMessage blockHeadersMessage) {
        final List<BlockHeaderWithTransactionCount> blockHeaders = blockHeadersMessage.getBlockHeaders();

        final Boolean allBlockHeadersAreValid;
        {
            Boolean isValid = true;
            for (final BlockHeader blockHeader : blockHeaders) {
                if (! blockHeader.isValid()) {
                    isValid = false;
                    break;
                }
            }
            allBlockHeadersAreValid = isValid;
        }

        if (blockHeaders.isEmpty()) { return; }

        final BlockHeader firstBlockHeader = blockHeaders.get(0);
        _executeAndClearCallbacks(_downloadBlockHeadersRequests, firstBlockHeader.getPreviousBlockHash(), (allBlockHeadersAreValid ? blockHeaders : null));
    }

    protected void _onQueryBlocksMessageReceived(final QueryBlocksMessage queryBlocksMessage, final NodeConnection nodeConnection) {
        final QueryBlocksCallback queryBlocksCallback = _queryBlocksCallback;

        if (queryBlocksCallback != null) {
            final MutableList<Sha256Hash> blockHeaderHashes = new MutableList<Sha256Hash>(queryBlocksMessage.getBlockHeaderHashes());
            final Sha256Hash desiredBlockHeaderHash = queryBlocksMessage.getStopBeforeBlockHash();
            queryBlocksCallback.run(blockHeaderHashes, desiredBlockHeaderHash, nodeConnection);
        }
        else {
            Logger.log("NOTICE: No handler set for QueryBlocks message.");
        }
    }

    protected void _onQueryBlockHeadersMessageReceived(final RequestBlockHeadersMessage requestBlockHeadersMessage, final NodeConnection nodeConnection) {
        final QueryBlockHeadersCallback queryBlockHeadersCallback = _queryBlockHeadersCallback;

        if (queryBlockHeadersCallback != null) {
            final MutableList<Sha256Hash> blockHeaderHashes = new MutableList<Sha256Hash>(requestBlockHeadersMessage.getBlockHeaderHashes());
            final Sha256Hash desiredBlockHeaderHash = requestBlockHeadersMessage.getStopBeforeBlockHash();
            queryBlockHeadersCallback.run(blockHeaderHashes, desiredBlockHeaderHash, nodeConnection);
        }
        else {
            Logger.log("NOTICE: No handler set for QueryBlockHeaders message.");
        }
    }

    protected void _onRequestExtraThinBlockMessageReceived(final RequestExtraThinBlockMessage requestExtraThinBlockMessage, final NodeConnection nodeConnection) {
        final RequestExtraThinBlockCallback requestExtraThinBlockCallback = _requestExtraThinBlockCallback;

        if (requestExtraThinBlockCallback != null) {
            final InventoryItem inventoryItem = requestExtraThinBlockMessage.getInventoryItem();
            if (inventoryItem.getItemType() != InventoryItemType.EXTRA_THIN_BLOCK) { return; }

            final Sha256Hash blockHash = inventoryItem.getItemHash();
            final BloomFilter bloomFilter = requestExtraThinBlockMessage.getBloomFilter();
            requestExtraThinBlockCallback.run(blockHash, bloomFilter, nodeConnection);
        }
        else {
            Logger.log("NOTICE: No handler set for RequestExtraThinBlock message.");
        }
    }

    protected void _onRequestExtraThinTransactionsMessageReceived(final RequestExtraThinTransactionsMessage requestExtraThinTransactionsMessage, final NodeConnection nodeConnection) {
        final RequestExtraThinTransactionCallback requestExtraThinTransactionCallback = _requestExtraThinTransactionCallback;

        if (requestExtraThinTransactionCallback != null) {
            final Sha256Hash blockHash = requestExtraThinTransactionsMessage.getBlockHash();
            final List<ByteArray> transactionShortHashes = requestExtraThinTransactionsMessage.getTransactionShortHashes();

            requestExtraThinTransactionCallback.run(blockHash, transactionShortHashes, nodeConnection);
        }
        else {
            Logger.log("NOTICE: No handler set for RequestExtraThinBlock message.");
        }
    }

    protected void _onThinBlockMessageReceived(final ThinBlockMessage blockMessage) {
        final BlockHeader blockHeader = blockMessage.getBlockHeader();
        final List<Sha256Hash> transactionHashes = blockMessage.getTransactionHashes();
        final List<Transaction> transactions = blockMessage.getMissingTransactions();
        final Boolean blockHeaderIsValid = blockHeader.isValid();

        final ThinBlockParameters thinBlockParameters = new ThinBlockParameters(blockHeader, transactionHashes, transactions);

        final Sha256Hash blockHash = blockHeader.getHash();
        _executeAndClearCallbacks(_downloadThinBlockRequests, blockHash, (blockHeaderIsValid ? thinBlockParameters : null));
    }

    protected void _onExtraThinBlockMessageReceived(final ExtraThinBlockMessage blockMessage) {
        final BlockHeader blockHeader = blockMessage.getBlockHeader();
        final List<ByteArray> transactionHashes = blockMessage.getTransactionShortHashes();
        final List<Transaction> transactions = blockMessage.getMissingTransactions();
        final Boolean blockHeaderIsValid = blockHeader.isValid();

        final ExtraThinBlockParameters extraThinBlockParameters = new ExtraThinBlockParameters(blockHeader, transactionHashes, transactions);

        final Sha256Hash blockHash = blockHeader.getHash();
        _executeAndClearCallbacks(_downloadExtraThinBlockRequests, blockHash, (blockHeaderIsValid ? extraThinBlockParameters : null));
    }

    protected void _onThinTransactionsMessageReceived(final ThinTransactionsMessage transactionsMessage) {
        final Sha256Hash blockHash = transactionsMessage.getBlockHash();
        final List<Transaction> transactions = transactionsMessage.getTransactions();

        _executeAndClearCallbacks(_downloadThinTransactionsRequests, blockHash, transactions);
    }

    protected void _queryForBlockHashesAfter(final Sha256Hash blockHash) {
        final QueryBlocksMessage queryBlocksMessage = new QueryBlocksMessage();
        queryBlocksMessage.addBlockHeaderHash(blockHash);
        _queueMessage(queryBlocksMessage);
    }

    protected void _requestBlock(final Sha256Hash blockHash) {
        final RequestDataMessage requestDataMessage = new RequestDataMessage();
        requestDataMessage.addInventoryItem(new InventoryItem(InventoryItemType.BLOCK, blockHash));
        _queueMessage(requestDataMessage);
    }

    protected void _requestThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter) {
        final InventoryItem inventoryItem = new InventoryItem(InventoryItemType.COMPACT_BLOCK, blockHash);

        final RequestExtraThinBlockMessage requestExtraThinBlockMessage = new RequestExtraThinBlockMessage();
        requestExtraThinBlockMessage.setInventoryItem(inventoryItem);
        requestExtraThinBlockMessage.setBloomFilter(knownTransactionsFilter);

        _queueMessage(requestExtraThinBlockMessage);
    }

    protected void _requestExtraThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter) {
        final InventoryItem inventoryItem = new InventoryItem(InventoryItemType.EXTRA_THIN_BLOCK, blockHash);

        final RequestExtraThinBlockMessage requestExtraThinBlockMessage = new RequestExtraThinBlockMessage();
        requestExtraThinBlockMessage.setInventoryItem(inventoryItem);
        requestExtraThinBlockMessage.setBloomFilter(knownTransactionsFilter);

        _queueMessage(requestExtraThinBlockMessage);
    }

    protected void _requestThinTransactions(final Sha256Hash blockHash, final List<ByteArray> transactionShortHashes) {
        final RequestExtraThinTransactionsMessage requestThinTransactionsMessage = new RequestExtraThinTransactionsMessage();
        requestThinTransactionsMessage.setBlockHash(blockHash);
        requestThinTransactionsMessage.setTransactionShortHashes(transactionShortHashes);

        _queueMessage(requestThinTransactionsMessage);
    }

    protected void _requestBlockHeaders(final List<Sha256Hash> blockHashes) {
        final RequestBlockHeadersMessage requestBlockHeadersMessage = new RequestBlockHeadersMessage();
        for (final Sha256Hash blockHash : blockHashes) {
            requestBlockHeadersMessage.addBlockHeaderHash(blockHash);
        }
        _queueMessage(requestBlockHeadersMessage);
    }

    protected void _requestTransactions(final List<Sha256Hash> transactionHashes) {
        final RequestDataMessage requestTransactionMessage = new RequestDataMessage();
        for (final Sha256Hash transactionHash: transactionHashes) {
            requestTransactionMessage.addInventoryItem(new InventoryItem(InventoryItemType.TRANSACTION, transactionHash));
        }
        _queueMessage(requestTransactionMessage);
    }

    public void transmitBlockFinder(final List<Sha256Hash> blockHashes) {
        final Integer blockHashesCount = blockHashes.getSize();
        final QueryBlocksMessage queryBlocksMessage = new QueryBlocksMessage();
        for (int i = 0; i < blockHashesCount; ++i) {
            final Sha256Hash blockHash = blockHashes.get(blockHashesCount - i - 1);
            queryBlocksMessage.addBlockHeaderHash(blockHash);
        }

        _queueMessage(queryBlocksMessage);
    }

    public void requestBlockHashesAfter(final Sha256Hash blockHash) {
        _queryForBlockHashesAfter(blockHash);
    }

    public void requestBlock(final Sha256Hash blockHash, final DownloadBlockCallback downloadBlockCallback) {
        _storeInMapSet(_downloadBlockRequests, blockHash, downloadBlockCallback);
        _requestBlock(blockHash);
    }

    public void requestThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter, final DownloadThinBlockCallback downloadThinBlockCallback) {
        _storeInMapSet(_downloadThinBlockRequests, blockHash, downloadThinBlockCallback);
        _requestThinBlock(blockHash, knownTransactionsFilter);
    }

    public void requestExtraThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter, final DownloadExtraThinBlockCallback downloadThinBlockCallback) {
        _storeInMapSet(_downloadExtraThinBlockRequests, blockHash, downloadThinBlockCallback);
        _requestExtraThinBlock(blockHash, knownTransactionsFilter);
    }

    public void requestThinTransactions(final Sha256Hash blockHash, final List<Sha256Hash> transactionHashes, final DownloadThinTransactionsCallback downloadThinBlockCallback) {
        final ImmutableListBuilder<ByteArray> shortTransactionHashesBuilder = new ImmutableListBuilder<ByteArray>(transactionHashes.getSize());
        for (final Sha256Hash transactionHash : transactionHashes) {
            final ByteArray shortTransactionHash = MutableByteArray.wrap(transactionHash.getBytes(0, 8));
            shortTransactionHashesBuilder.add(shortTransactionHash);
        }
        final List<ByteArray> shortTransactionHashes = shortTransactionHashesBuilder.build();

        _storeInMapSet(_downloadThinTransactionsRequests, blockHash, downloadThinBlockCallback);
        _requestThinTransactions(blockHash, shortTransactionHashes);
    }

    public void requestBlockHeaders(final List<Sha256Hash> blockHashes, final DownloadBlockHeadersCallback downloadBlockHeaderCallback) {
        if (blockHashes.isEmpty()) { return; }

        final Sha256Hash firstBlockHash = blockHashes.get(0);
        _storeInMapSet(_downloadBlockHeadersRequests, firstBlockHash, downloadBlockHeaderCallback);
        _requestBlockHeaders(blockHashes);
    }

    public void requestTransactions(final List<Sha256Hash> transactionHashes, final DownloadTransactionCallback downloadTransactionCallback) {
        if (transactionHashes.isEmpty()) { return; }

        for (final Sha256Hash transactionHash : transactionHashes) {
            _storeInMapSet(_downloadTransactionRequests, transactionHash, downloadTransactionCallback);
        }
        _requestTransactions(transactionHashes);
    }

    public void setSynchronizationStatusHandler(final SynchronizationStatus synchronizationStatus) {
        _synchronizationStatus = synchronizationStatus;
    }

    public void setQueryBlocksCallback(final QueryBlocksCallback queryBlocksCallback) {
        _queryBlocksCallback = queryBlocksCallback;
    }

    public void setQueryBlockHeadersCallback(final QueryBlockHeadersCallback queryBlockHeadersCallback) {
        _queryBlockHeadersCallback = queryBlockHeadersCallback;
    }

    public void setRequestDataCallback(final RequestDataCallback requestDataCallback) {
        _requestDataMessageCallback = requestDataCallback;
    }

    public void setInventoryMessageHandler(final InventoryMessageCallback inventoryMessageHandler) {
        _inventoryMessageHandler = inventoryMessageHandler;
    }

    public void setRequestExtraThinBlockCallback(final RequestExtraThinBlockCallback requestExtraThinBlockCallback) {
        _requestExtraThinBlockCallback = requestExtraThinBlockCallback;
    }

    public void setTransactionsAnnouncementCallback(final TransactionsAnnouncementCallback transactionsAnnouncementCallback) {
        _transactionsAnnouncementCallback = transactionsAnnouncementCallback;
    }

    public Boolean newBlocksViaHeadersIsEnabled() {
        return _announceNewBlocksViaHeadersIsEnabled;
    }

    public Boolean supportsExtraThinBlocks() {
        if (_synchronizeVersionMessage == null) { return false; }

        final NodeFeatures nodeFeatures = _synchronizeVersionMessage.getNodeFeatures();
        return nodeFeatures.hasFeatureFlagEnabled(NodeFeatures.Feature.XTHIN_PROTOCOL_ENABLED);
    }

    @Override
    public BitcoinNodeIpAddress getLocalNodeIpAddress() {
        return ((BitcoinNodeIpAddress) _localNodeIpAddress).copy();
    }

    @Override
    public BitcoinNodeIpAddress getRemoteNodeIpAddress() {
        final NodeIpAddress nodeIpAddress = super.getRemoteNodeIpAddress();
        if (nodeIpAddress == null) { return null; }
        if (_synchronizeVersionMessage == null) { return null; }

        final NodeFeatures nodeFeatures = _synchronizeVersionMessage.getNodeFeatures();

        final BitcoinNodeIpAddress bitcoinNodeIpAddress = new BitcoinNodeIpAddress();
        bitcoinNodeIpAddress.setIp(nodeIpAddress.getIp());
        bitcoinNodeIpAddress.setPort(nodeIpAddress.getPort());
        bitcoinNodeIpAddress.setNodeFeatures(nodeFeatures);
        return bitcoinNodeIpAddress;
    }

    @Override
    public void disconnect() {
        super.disconnect();

        _availableDataHashes.clear();
        _downloadBlockRequests.clear();
        _downloadBlockHeadersRequests.clear();
    }
}
