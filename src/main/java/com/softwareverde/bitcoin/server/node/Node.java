package com.softwareverde.bitcoin.server.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.database.BlockTracker;
import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.block.BlockMessage;
import com.softwareverde.bitcoin.server.message.type.error.ErrorMessage;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddressMessage;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.ping.PingMessage;
import com.softwareverde.bitcoin.server.message.type.node.pong.PongMessage;
import com.softwareverde.bitcoin.server.message.type.query.block.GetBlocksMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.QueryResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHash;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHashType;
import com.softwareverde.bitcoin.server.message.type.request.RequestDataMessage;
import com.softwareverde.bitcoin.server.message.type.version.acknowledge.AcknowledgeVersionMessage;
import com.softwareverde.bitcoin.server.message.type.version.synchronize.SynchronizeVersionMessage;
import com.softwareverde.bitcoin.server.socket.ip.Ipv4;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;

import java.util.*;

public class Node extends NodeConnectionDelegate {
    protected static final Object NODE_ID_MUTEX = new Object();
    protected static Long _nextId = 0L;

    public interface DownloadBlockCallback {
        void onBlockDownloaded(Block block);
    }

    protected Long _id;
    protected final List<ProtocolMessage> _postHandshakeMessageQueue = new LinkedList<ProtocolMessage>();
    protected Boolean _handshakeIsComplete = false;
    protected final Map<DataHashType, Set<DataHash>> _availableDataHashes = new HashMap<DataHashType, Set<DataHash>>();
    protected final Map<Hash, Set<DownloadBlockCallback>> _downloadBlockRequests = new HashMap<Hash, Set<DownloadBlockCallback>>();

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
        final List<DataHash> dataHashes = queryResponseMessage.getDataHashes();
        for (final DataHash dataHash : dataHashes) {
            final DataHashType dataHashType = dataHash.getDataHashType();
            if (! _availableDataHashes.containsKey(dataHashType)) {
                _availableDataHashes.put(dataHashType, new HashSet<DataHash>());
            }

            _availableDataHashes.get(dataHashType).add(dataHash);
        }
    }

    @Override
    protected void _onBlockMessageReceived(final BlockMessage blockMessage) {
        final Block block = blockMessage.getBlock();
        final Boolean blockHeaderIsValid = block.validateBlockHeader();

        final Hash blockHash = block.calculateSha256Hash();
        final Set<DownloadBlockCallback> downloadBlockCallbacks = _downloadBlockRequests.remove(blockHash);
        if (downloadBlockCallbacks == null) { return; }

        for (DownloadBlockCallback downloadBlockCallback : downloadBlockCallbacks) {
            downloadBlockCallback.onBlockDownloaded((blockHeaderIsValid ? block : null));
        }
    }

    protected void _requestBlock(final ImmutableHash blockHash) {
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

    public void requestBlock(final ImmutableHash blockHash, final DownloadBlockCallback downloadBlockCallback) {
        if (!_downloadBlockRequests.containsKey(blockHash)) {
            _downloadBlockRequests.put(blockHash, new HashSet<DownloadBlockCallback>());
        }

        _downloadBlockRequests.get(blockHash).add(downloadBlockCallback);

        _requestBlock(blockHash);
    }

    public Long getId() { return _id; }
}
