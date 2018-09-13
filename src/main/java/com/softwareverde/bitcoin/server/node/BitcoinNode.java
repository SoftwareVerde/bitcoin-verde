package com.softwareverde.bitcoin.server.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.error.ErrorMessage;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddressMessage;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.ping.BitcoinPingMessage;
import com.softwareverde.bitcoin.server.message.type.node.pong.BitcoinPongMessage;
import com.softwareverde.bitcoin.server.message.type.query.block.QueryBlocksMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.QueryResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.block.BlockMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.block.header.BlockHeadersMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHash;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHashType;
import com.softwareverde.bitcoin.server.message.type.query.response.transaction.TransactionMessage;
import com.softwareverde.bitcoin.server.message.type.request.RequestDataMessage;
import com.softwareverde.bitcoin.server.message.type.request.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.server.message.type.version.acknowledge.BitcoinAcknowledgeVersionMessage;
import com.softwareverde.bitcoin.server.message.type.version.synchronize.BitcoinSynchronizeVersionMessage;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.callback.Callback;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
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
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BitcoinNode extends Node {
    public static Boolean LOGGING_ENABLED = false;

    public interface FailableCallback {
        default void onFailure() { }
    }
    public interface QueryCallback extends Callback<List<Sha256Hash>>, FailableCallback { }
    public interface DownloadBlockCallback extends Callback<Block>, FailableCallback { }
    public interface DownloadBlockHeadersCallback extends Callback<List<BlockHeaderWithTransactionCount>>, FailableCallback { }
    public interface DownloadTransactionCallback extends Callback<Transaction>, FailableCallback { }

    public interface BlockAnnouncementCallback extends Callback<Block> { }
    public interface TransactionsAnnouncementCallback extends Callback<List<Sha256Hash>> { }

    public interface SynchronizationStatusHandler {
        Boolean isReadyForTransactions();
        Integer getCurrentBlockHeight();
    }

    public static SynchronizationStatusHandler DEFAULT_STATUS_CALLBACK = new SynchronizationStatusHandler() {
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
        void run(List<DataHash> dataHashes, NodeConnection nodeConnection);
    }

    protected static class BlockHashQueryCallback implements Callback<List<Sha256Hash>> {
        public Sha256Hash afterBlockHash;
        public QueryCallback callback;

        public BlockHashQueryCallback(final Sha256Hash afterBlockHash, final QueryCallback callback) {
            this.afterBlockHash = afterBlockHash;
            this.callback = callback;
        }

        @Override
        public void onResult(final List<Sha256Hash> result) {
            this.callback.onResult(result);
        }
    }

    protected static <U, T, S extends Callback<U>> void _executeAndClearCallbacks(final Map<T, Set<S>> callbackMap, final T key, final U value) {
        final Set<S> callbackSet = callbackMap.remove(key);
        if ( (callbackSet == null) || (callbackSet.isEmpty()) ) { return; }

        for (final S callback : callbackSet) {
            callback.onResult(value);
        }
    }

    protected SynchronizationStatusHandler _synchronizationStatusHandler = DEFAULT_STATUS_CALLBACK;

    protected QueryBlocksCallback _queryBlocksCallback = null;
    protected QueryBlockHeadersCallback _queryBlockHeadersCallback = null;
    protected RequestDataCallback _requestDataMessageCallback = null;
    protected BitcoinSynchronizeVersionMessage _synchronizeVersionMessage = null;

    protected BlockAnnouncementCallback _blockAnnouncementCallback;
    protected TransactionsAnnouncementCallback _transactionsAnnouncementCallback;

    protected final Map<DataHashType, Set<BlockHashQueryCallback>> _queryRequests = new HashMap<DataHashType, Set<BlockHashQueryCallback>>();
    protected final Map<Sha256Hash, Set<DownloadBlockCallback>> _downloadBlockRequests = new HashMap<Sha256Hash, Set<DownloadBlockCallback>>();
    protected final Map<Sha256Hash, Set<DownloadBlockHeadersCallback>> _downloadBlockHeadersRequests = new HashMap<Sha256Hash, Set<DownloadBlockHeadersCallback>>();
    protected final Map<DataHashType, Set<DataHash>> _availableDataHashes = new HashMap<DataHashType, Set<DataHash>>();
    protected final Map<Sha256Hash, Set<DownloadTransactionCallback>> _downloadTransactionRequests = new HashMap<Sha256Hash, Set<DownloadTransactionCallback>>();

    protected Boolean _announceNewBlocksViaHeadersIsEnabled = false;

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

        synchronizeVersionMessage.setRelayIsEnabled(_synchronizationStatusHandler.isReadyForTransactions());
        synchronizeVersionMessage.setCurrentBlockHeight(_synchronizationStatusHandler.getCurrentBlockHeight());

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

                _lastMessageReceivedTimestamp = System.currentTimeMillis();

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

                    case QUERY_RESPONSE: {
                        _onQueryResponseMessageReceived((QueryResponseMessage) message);
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
                        _onQueryBlockHeadersReceived((RequestBlockHeadersMessage) message, _connection);
                    } break;

                    case ENABLE_NEW_BLOCKS_VIA_HEADERS: {
                        _announceNewBlocksViaHeadersIsEnabled = true;
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
            final List<DataHash> dataHashes = new ImmutableList<DataHash>(requestDataMessage.getDataHashes());
            requestDataCallback.run(dataHashes, nodeConnection);
        }
        else {
            Logger.log("NOTICE: No handler set for RequestData message.");
        }
    }

    protected void _onQueryResponseMessageReceived(final QueryResponseMessage queryResponseMessage) {
        final Map<DataHashType, MutableList<Sha256Hash>> dataHashesMap = new HashMap<DataHashType, MutableList<Sha256Hash>>();

        final List<DataHash> dataHashes = queryResponseMessage.getDataHashes();
        for (final DataHash dataHash : dataHashes) {
            final DataHashType dataHashType = dataHash.getDataHashType();
            _storeInMapSet(_availableDataHashes, dataHashType, dataHash);
            _storeInMapList(dataHashesMap, dataHashType, dataHash.getObjectHash());
        }

        for (final DataHashType dataHashType : dataHashesMap.keySet()) {
            final List<Sha256Hash> objectHashes = dataHashesMap.get(dataHashType);
            if (objectHashes.isEmpty()) { continue; }

            if (dataHashType == DataHashType.BLOCK) {
                // NOTE: Since the QueryResponseMessage is not tied to the QueryRequest for Blocks,
                //  in order to tie the callback to the response, the first block within the response is requested.
                //  If the downloaded Block's previousBlockHash matchesByte the requestAfter BlockHash, then the response is
                //  assumed to be for that callback's request.

                final Sha256Hash blockHash = objectHashes.get(0);

                _storeInMapSet(_downloadBlockRequests, blockHash, new DownloadBlockCallback() {
                    @Override
                    public void onResult(final Block block) {
                        final Sha256Hash previousBlockHash = block.getPreviousBlockHash();

                        final Set<BlockHashQueryCallback> blockHashQueryCallbackSet = _queryRequests.get(dataHashType);

                        Boolean queryResponseWasRequested = false;
                        if (blockHashQueryCallbackSet != null) {
                            for (final BlockHashQueryCallback blockHashQueryCallback : Util.copySet(blockHashQueryCallbackSet)) {
                                if (Util.areEqual(previousBlockHash, blockHashQueryCallback.afterBlockHash)) {
                                    blockHashQueryCallbackSet.remove(blockHashQueryCallback);
                                    blockHashQueryCallback.onResult(objectHashes);
                                    queryResponseWasRequested = true;
                                }
                            }
                        }

                        if (! queryResponseWasRequested) {
                            final BlockAnnouncementCallback blockAnnouncementCallback = _blockAnnouncementCallback;
                            if (blockAnnouncementCallback != null) {
                                blockAnnouncementCallback.onResult(block);
                            }
                            else {
                                Logger.log("NOTICE: No handler set for NewBlockAnnouncement.");
                            }
                        }
                    }
                });
                _requestBlock(blockHash); // TODO: Convert to _requestBlockHeader(blockHash);

                continue;
            }
            else if (dataHashType == DataHashType.TRANSACTION) {
                final TransactionsAnnouncementCallback transactionsAnnouncementCallback = _transactionsAnnouncementCallback;
                if (transactionsAnnouncementCallback != null) {
                    transactionsAnnouncementCallback.onResult(objectHashes);
                }
                else {
                    Logger.log("NOTICE: No handler set for TransactionsAnnouncementCallback.");
                }
            }

            _executeAndClearCallbacks(_queryRequests, dataHashType, objectHashes);
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

    protected void _onQueryBlockHeadersReceived(final RequestBlockHeadersMessage requestBlockHeadersMessage, final NodeConnection nodeConnection) {
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

    protected void _queryForBlockHashesAfter(final Sha256Hash blockHash) {
        final QueryBlocksMessage queryBlocksMessage = new QueryBlocksMessage();
        queryBlocksMessage.addBlockHeaderHash(blockHash);
        _queueMessage(queryBlocksMessage);
    }

    protected void _requestBlock(final Sha256Hash blockHash) {
        final RequestDataMessage requestDataMessage = new RequestDataMessage();
        requestDataMessage.addInventoryItem(new DataHash(DataHashType.BLOCK, blockHash));
        _queueMessage(requestDataMessage);
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
            requestTransactionMessage.addInventoryItem(new DataHash(DataHashType.TRANSACTION, transactionHash));
        }
        _queueMessage(requestTransactionMessage);
    }

    public void detectFork(final List<Sha256Hash> blockHashes) {
        final Integer blockHashesCount = blockHashes.getSize();
        final QueryBlocksMessage queryBlocksMessage = new QueryBlocksMessage();
        for (int i = 0; i < blockHashesCount; ++i) {
            final Sha256Hash blockHash = blockHashes.get(blockHashesCount - i - 1);
            queryBlocksMessage.addBlockHeaderHash(blockHash);
        }

        _queueMessage(queryBlocksMessage);
    }

    public void requestBlockHashesAfter(final Sha256Hash blockHash, final QueryCallback queryCallback) {
        _storeInMapSet(_queryRequests, DataHashType.BLOCK, new BlockHashQueryCallback(blockHash, queryCallback));
        _queryForBlockHashesAfter(blockHash);
    }

    public void requestBlock(final Sha256Hash blockHash, final DownloadBlockCallback downloadBlockCallback) {
        _storeInMapSet(_downloadBlockRequests, blockHash, downloadBlockCallback);
        _requestBlock(blockHash);
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

    public void setSynchronizationStatusHandler(final SynchronizationStatusHandler synchronizationStatusHandler) {
        _synchronizationStatusHandler = synchronizationStatusHandler;
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

    public void setBlockAnnouncementCallback(final BlockAnnouncementCallback blockAnnouncementCallback) {
        _blockAnnouncementCallback = blockAnnouncementCallback;
    }

    public void setTransactionsAnnouncementCallback(final TransactionsAnnouncementCallback transactionsAnnouncementCallback) {
        _transactionsAnnouncementCallback = transactionsAnnouncementCallback;
    }

    public Boolean newBlocksViaHeadersIsEnabled() {
        return _announceNewBlocksViaHeadersIsEnabled;
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
        _queryRequests.clear();
        _downloadBlockRequests.clear();
        _downloadBlockHeadersRequests.clear();
    }
}
