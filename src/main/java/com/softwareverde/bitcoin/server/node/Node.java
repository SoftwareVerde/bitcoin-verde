package com.softwareverde.bitcoin.server.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.block.BlockMessage;
import com.softwareverde.bitcoin.server.message.type.error.ErrorMessage;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddressMessage;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.ping.PingMessage;
import com.softwareverde.bitcoin.server.message.type.node.pong.PongMessage;
import com.softwareverde.bitcoin.server.message.type.query.block.QueryBlocksMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.QueryResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHash;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHashType;
import com.softwareverde.bitcoin.server.message.type.request.RequestDataMessage;
import com.softwareverde.bitcoin.server.message.type.version.acknowledge.AcknowledgeVersionMessage;
import com.softwareverde.bitcoin.server.message.type.version.synchronize.SynchronizeVersionMessage;
import com.softwareverde.bitcoin.server.socket.ip.Ipv4;
import com.softwareverde.bitcoin.type.callback.Callback;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.util.Util;

import java.util.*;

public class Node extends NodeConnectionDelegate {
    protected static final Object NODE_ID_MUTEX = new Object();
    protected static Long _nextId = 0L;

    public interface QueryCallback extends Callback<List<Hash>> { }
    public interface DownloadBlockCallback extends Callback<Block> { }

    protected static class BlockHashQueryCallback implements Callback<List<Hash>> {
        public Hash afterBlockHash;
        public QueryCallback callback;

        public BlockHashQueryCallback(final Hash afterBlockHash, final QueryCallback callback) {
            this.afterBlockHash = afterBlockHash;
            this.callback = callback;
        }

        @Override
        public void onResult(final List<Hash> result) {
            this.callback.onResult(result);
        }
    }

    protected Long _id;
    protected Boolean _handshakeIsComplete = false;
    protected final List<ProtocolMessage> _postHandshakeMessageQueue = new LinkedList<ProtocolMessage>();
    protected final Map<DataHashType, Set<DataHash>> _availableDataHashes = new HashMap<DataHashType, Set<DataHash>>();

    protected final Map<DataHashType, Set<BlockHashQueryCallback>> _queryRequests = new HashMap<DataHashType, Set<BlockHashQueryCallback>>();
    protected final Map<Hash, Set<DownloadBlockCallback>> _downloadBlockRequests = new HashMap<Hash, Set<DownloadBlockCallback>>();

    protected <T, S> void _storeInMapSet(final Map<T, Set<S>> destinationMap, final T key, final S value) {
        Set<S> destinationSet = destinationMap.get(key);
        if (destinationSet == null) {
            destinationSet = new HashSet<S>();
            destinationMap.put(key, destinationSet);
        }
        destinationSet.add(value);
    }

    protected <T, S> void _storeInMapList(final Map<T, List<S>> destinationList, final T key, final S value) {
        List<S> destinationSet = destinationList.get(key);
        if (destinationSet == null) {
            destinationSet = new ArrayList<S>();
            destinationList.put(key, destinationSet);
        }
        destinationSet.add(value);
    }

    protected <U, T, S extends Callback<U>> void _executeAndClearCallbacks(final Map<T, Set<S>> callbackMap, final T key, final U value) {
        final Set<S> callbackSet = callbackMap.remove(key);
        if (callbackSet == null) { return; }

        for (final S callback : callbackSet) {
            callback.onResult(value);
        }
    }

    protected void _queueMessage(final ProtocolMessage message) {
        if (_handshakeIsComplete) {
            _connection.queueMessage(message);
        }
        else {
            _postHandshakeMessageQueue.add(message);
        }
    }

    @Override
    protected void _onConnect() {
        final SynchronizeVersionMessage synchronizeVersionMessage = new SynchronizeVersionMessage();
        { // Set Remote NodeIpAddress...
            final NodeIpAddress remoteNodeIpAddress = new NodeIpAddress();
            remoteNodeIpAddress.setIp(Ipv4.parse(_connection.getRemoteIp()));
            remoteNodeIpAddress.setPort(_connection.getPort());
            remoteNodeIpAddress.setNodeFeatures(new NodeFeatures());
            synchronizeVersionMessage.setRemoteAddress(remoteNodeIpAddress);
        }
        _connection.queueMessage(synchronizeVersionMessage);
    }

    @Override
    protected void _onDisconnect() {
        System.out.println("Socket disconnected.");
    }

    @Override
    protected void _onPingReceived(final PingMessage pingMessage) {
        final PongMessage pongMessage = new PongMessage();
        pongMessage.setNonce(pingMessage.getNonce());
        _queueMessage(pongMessage);
    }

    @Override
    protected void _onAcknowledgeVersionMessageReceived(final AcknowledgeVersionMessage acknowledgeVersionMessage) {
        _handshakeIsComplete = true;
        while (! _postHandshakeMessageQueue.isEmpty()) {
            _queueMessage(_postHandshakeMessageQueue.remove(0));
        }
    }

    @Override
    protected void _onNodeAddressesReceived(final NodeIpAddressMessage nodeIpAddressMessage) {
        for (final NodeIpAddress nodeIpAddress : nodeIpAddressMessage.getNodeIpAddresses()) {
            System.out.println("Network Address: "+ BitcoinUtil.toHexString(nodeIpAddress.getBytesWithTimestamp()));
        }
    }

    @Override
    protected void _onErrorMessageReceived(final ErrorMessage errorMessage) {
        final ErrorMessage.RejectCode rejectCode = errorMessage.getRejectCode();
        System.out.println("RECEIVED ERROR:"+ rejectCode.getRejectMessageType().getValue() +" "+ BitcoinUtil.toHexString(new byte[] { rejectCode.getCode() }) +" "+ errorMessage.getRejectDescription() +" "+ BitcoinUtil.toHexString(errorMessage.getExtraData()));
    }

    @Override
    protected void _onQueryResponseMessageReceived(final QueryResponseMessage queryResponseMessage) {
        final Map<DataHashType, List<Hash>> dataHashesMap = new HashMap<DataHashType, List<Hash>>();

        final List<DataHash> dataHashes = queryResponseMessage.getDataHashes();
        for (final DataHash dataHash : dataHashes) {
            final DataHashType dataHashType = dataHash.getDataHashType();
            _storeInMapSet(_availableDataHashes, dataHashType, dataHash);
            _storeInMapList(dataHashesMap, dataHashType, dataHash.getObjectHash());
        }

        for (final DataHashType dataHashType : dataHashesMap.keySet()) {
            final List<Hash> objectHashes = dataHashesMap.get(dataHashType);
            if (objectHashes.isEmpty()) { continue; }

            {   // NOTE: Since the QueryResponseMessage is not tied to the QueryRequest for Blocks,
                //  so in order to tie the callback to the response, the first block within the response is requested.
                //  If the downloaded Block's previousBlockHash matches the requestAfter BlockHash, then the response is
                //  assumed to be for that callback's request.

                if (dataHashType == DataHashType.BLOCK) {
                    final Hash blockHash = objectHashes.get(0);
                    _storeInMapSet(_downloadBlockRequests, blockHash, new DownloadBlockCallback() {
                        @Override
                        public void onResult(final Block block) {
                            final Set<BlockHashQueryCallback> blockHashQueryCallbackSet = _queryRequests.get(dataHashType);
                            for (final BlockHashQueryCallback blockHashQueryCallback : Util.copySet(_queryRequests.get(dataHashType))) {
                                if (block.getPreviousBlockHash().equals(blockHashQueryCallback.afterBlockHash)) {
                                    blockHashQueryCallbackSet.remove(blockHashQueryCallback);
                                    blockHashQueryCallback.onResult(objectHashes);
                                }
                            }
                        }
                    });
                    _requestBlock(blockHash); // TODO: Convert to _requestBlockHeader(blockHash);

                    continue;
                }
            }

            _executeAndClearCallbacks(_queryRequests, dataHashType, objectHashes);
        }
    }

    @Override
    protected void _onBlockMessageReceived(final BlockMessage blockMessage) {
        final Block block = blockMessage.getBlock();
        final Boolean blockHeaderIsValid = block.validateBlockHeader();

        final Hash blockHash = block.calculateSha256Hash();
        _executeAndClearCallbacks(_downloadBlockRequests, blockHash, (blockHeaderIsValid ? block : null));
    }

    protected void _queryForBlockHashesAfter(final Hash blockHash) {
        final QueryBlocksMessage queryBlocksMessage = new QueryBlocksMessage();
        queryBlocksMessage.addBlockHeaderHash(blockHash);
        _queueMessage(queryBlocksMessage);
    }

    protected void _requestBlock(final Hash blockHash) {
        final RequestDataMessage requestDataMessage = new RequestDataMessage();
        requestDataMessage.addInventoryItem(new DataHash(DataHashType.BLOCK, blockHash));
        _queueMessage(requestDataMessage);
    }

    public Node(final String host, final Integer port) {
        super(host, port);
        synchronized (NODE_ID_MUTEX) {
            _id = _nextId;
            _nextId += 1;
        }
    }

    public void getBlockHashesAfter(final Hash blockHash, final QueryCallback queryCallback) {
        _storeInMapSet(_queryRequests, DataHashType.BLOCK, new BlockHashQueryCallback(blockHash, queryCallback));
        _queryForBlockHashesAfter(blockHash);
    }

    public void requestBlock(final Hash blockHash, final DownloadBlockCallback downloadBlockCallback) {
        _storeInMapSet(_downloadBlockRequests, blockHash, downloadBlockCallback);
        _requestBlock(blockHash);
    }

    public Long getId() { return _id; }
}
