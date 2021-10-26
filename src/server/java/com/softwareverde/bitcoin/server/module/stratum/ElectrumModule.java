package com.softwareverde.bitcoin.server.module.stratum;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTree;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTreeNode;
import com.softwareverde.bitcoin.block.merkleroot.MutableMerkleTree;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.configuration.ElectrumProperties;
import com.softwareverde.bitcoin.server.electrum.socket.ElectrumServerSocket;
import com.softwareverde.bitcoin.server.message.type.query.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.server.module.node.sync.bootstrap.HeadersBootstrapper;
import com.softwareverde.bitcoin.server.module.stratum.json.ElectrumJson;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.http.tls.TlsCertificate;
import com.softwareverde.http.tls.TlsFactory;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

import java.io.InputStream;

public class ElectrumModule {
    protected final ElectrumProperties _electrumProperties;
    protected final Boolean _tlsIsEnabled;
    protected final CachedThreadPool _threadPool;
    protected final ElectrumServerSocket _electrumServerSocket;
    protected final MutableList<JsonSocket> _connections = new MutableList<>();

    protected final MutableList<BlockHeader> _cachedBlockHeaders = new MutableList<>(0);
    protected Long _chainHeight = 0L;

    protected final Thread _nodeNotificationConnectionThread;
    protected NodeJsonRpcConnection _nodeNotificationConnection;

    protected NodeJsonRpcConnection _getNodeConnection() {
        final String nodeHost = _electrumProperties.getBitcoinRpcUrl();
        final Integer nodePort = _electrumProperties.getBitcoinRpcPort();
        Logger.trace("Connect: " + nodeHost + ":" + nodePort);
        return new NodeJsonRpcConnection(nodeHost, nodePort, _threadPool);
    }

    protected void _createNodeNotificationConnection() {
        if (_nodeNotificationConnection != null) {
            _nodeNotificationConnection.close();
            _nodeNotificationConnection = null;
        }

        final NodeJsonRpcConnection nodeJsonRpcConnection = _getNodeConnection();
        _nodeNotificationConnection = nodeJsonRpcConnection;

        nodeJsonRpcConnection.upgradeToAnnouncementHook(new NodeJsonRpcConnection.RawAnnouncementHookCallback() {
            @Override
            public void onNewBlockHeader(final BlockHeader blockHeader) {
                final Sha256Hash blockHash = blockHeader.getHash();

                final Long blockHeight;
                try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
                    final Json blockHeightJson = nodeConnection.getBlockHeaderHeight(blockHash);
                    blockHeight = blockHeightJson.getLong("blockHeaderHeight");
                }

                Logger.debug("New Header: " + blockHash + " " + blockHeight);

                synchronized (_connections) {
                    for (final JsonSocket socket : _connections) {
                        _notifyBlockHeader(socket, blockHeader, blockHeight);
                    }
                }
            }

            @Override
            public void onNewTransaction(final Transaction transaction, final Long fee) {

            }
        });
    }

    protected Long _loadBootstrappedHeaders() {
        long blockHeight = -1L;

        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        try (final InputStream inputStream = HeadersBootstrapper.class.getResourceAsStream("/bootstrap/headers.dat")) {
            if (inputStream == null) {
                Logger.warn("Unable to open headers bootstrap file.");
                return blockHeight;
            }

            final MutableByteArray buffer = new MutableByteArray(BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT);
            while (true) {
                int readByteCount = inputStream.read(buffer.unwrap());
                while ((readByteCount >= 0) && (readByteCount < BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT)) {
                    final int nextByte = inputStream.read();
                    if (nextByte < 0) { break; }

                    buffer.setByte(readByteCount, (byte) nextByte);
                    readByteCount += 1;
                }
                if (readByteCount != BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT) { return blockHeight; }

                final BlockHeader blockHeader = blockHeaderInflater.fromBytes(buffer);
                if (blockHeader == null) { return blockHeight; }

                _cachedBlockHeaders.add(blockHeader);
                blockHeight += 1L;
            }
        }
        catch (final Exception exception) {
            Logger.warn(exception);
        }

        return blockHeight;
    }

    protected void _cacheBlockHeaders() {
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            nodeConnection.enableKeepAlive(true);

            final Json blockHeightJson = nodeConnection.getBlockHeight();
            _chainHeight = blockHeightJson.getLong("blockHeight");
            _cachedBlockHeaders.clear();

            final long maxBlockHeight = Math.max(0L, _chainHeight - RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT);
            long blockHeight = (_loadBootstrappedHeaders() + 1L);
            while (blockHeight <= maxBlockHeight) {
                final Json blockHeadersJson = nodeConnection.getBlockHeadersAfter(blockHeight, RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT, true);
                final Json blockHeadersArray = blockHeadersJson.get("blockHeaders");
                final int blockHeaderCount = blockHeadersArray.length();
                Logger.debug("Received " + blockHeaderCount + " headers, starting at: " + blockHeight + " max=" + maxBlockHeight);
                for (int i = 0; i < blockHeaderCount; ++i) {
                    final String blockHeaderString = blockHeadersArray.getString(i);
                    final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString(blockHeaderString));
                    _cachedBlockHeaders.add(blockHeader);
                    blockHeight += 1L;

                    if (blockHeight > maxBlockHeight) { break; } // Include the maxBlockHeight block...
                }
            }
        }
    }

    protected MerkleTree<BlockHeader> _calculateCheckpointMerkle(final Long maxBlockHeight) {
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

        final MutableMerkleTree<BlockHeader> blockHeaderMerkleTree = new MerkleTreeNode<>();
        long blockHeight;
        {
            blockHeight = 0L;
            while (blockHeight < maxBlockHeight) {
                if (blockHeight >= _cachedBlockHeaders.getCount()) { break; }

                final BlockHeader blockHeader = _cachedBlockHeaders.get((int) blockHeight);
                blockHeaderMerkleTree.addItem(blockHeader);
                blockHeight += 1L;
            }
        }

        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            nodeConnection.enableKeepAlive(true);

            while (blockHeight <= maxBlockHeight) {
                final Json blockHeadersJson = nodeConnection.getBlockHeadersAfter(blockHeight, RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT, true);
                final Json blockHeadersArray = blockHeadersJson.get("blockHeaders");
                final int blockHeaderCount = blockHeadersArray.length();
                Logger.debug("Received " + blockHeaderCount + " headers, starting at: " + blockHeight + " max=" + maxBlockHeight);
                for (int i = 0; i < blockHeaderCount; ++i) {
                    final String blockHeaderString = blockHeadersArray.getString(i);
                    final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString(blockHeaderString));
                    blockHeaderMerkleTree.addItem(blockHeader);
                    blockHeight += 1L;

                    if (blockHeight > maxBlockHeight) { break; } // Include the maxBlockHeight block...
                }
            }
        }

        Logger.trace("Calculated: " + blockHeaderMerkleTree.getMerkleRoot());
        return blockHeaderMerkleTree;
    }

    protected TlsCertificate _loadCertificate(final String certificateFile, final String certificateKeyFile) {
        if (Util.isBlank(certificateFile) || Util.isBlank(certificateKeyFile)) { return null; }

        final TlsFactory tlsFactory = new TlsFactory();

        final byte[] certificateBytes = IoUtil.getFileContents(certificateFile);
        final byte[] certificateKeyFileBytes = IoUtil.getFileContents(certificateKeyFile);
        if ( (certificateBytes == null) || (certificateKeyFileBytes == null) ) {
            Logger.error("Error loading certificate: " + certificateFile + ", " + certificateKeyFile);
            return null;
        }

        tlsFactory.addTlsCertificate(StringUtil.bytesToString(certificateBytes), certificateKeyFileBytes);
        return tlsFactory.buildCertificate();
    }

    protected void _handleServerVersionMessage(final JsonSocket jsonSocket, final Json message) {
        final Integer id = message.getInteger("id");

        final Json json = new ElectrumJson(false);
        final Json resultJson = new ElectrumJson(true);
        resultJson.add("ElectrumVerde 1.0.0");
        resultJson.add("1.4");

        json.put("id", id);
        json.put("result", resultJson);
        jsonSocket.write(new JsonProtocolMessage(json));
        Logger.debug("Wrote: " + json);
        jsonSocket.flush();
    }

    protected void _handleBannerMessage(final JsonSocket jsonSocket, final Json message) {
        final Integer id = message.getInteger("id");

        final Json json = new ElectrumJson(false);

        json.put("id", id);
        json.put("result", "ElectrumVerde 1.0.0");
        jsonSocket.write(new JsonProtocolMessage(json));
        Logger.debug("Wrote: " + json);
        jsonSocket.flush();
    }

    protected void _handleDonationAddressMessage(final JsonSocket jsonSocket, final Json message) {
        final Integer id = message.getInteger("id");

        final Json json = new ElectrumJson(false);

        json.put("id", id);
        json.put("result", "qqverdefl9xtryyx8y52m6va5j8s2s4eq59fjdn97e");
        jsonSocket.write(new JsonProtocolMessage(json));
        Logger.debug("Wrote: " + json);
        jsonSocket.flush();
    }

    protected void _handlePingMessage(final JsonSocket jsonSocket, final Json message) {
        final Integer id = message.getInteger("id");

        final Json json = new ElectrumJson(false);

        json.put("id", id);
        json.put("result", null);
        jsonSocket.write(new JsonProtocolMessage(json));
        Logger.debug("Wrote: " + json);
        jsonSocket.flush();
    }

    protected void _handleMinimumRelayFeeMessage(final JsonSocket jsonSocket, final Json message) {
        final Integer id = message.getInteger("id");

        final Json json = new ElectrumJson(false);

        json.put("id", id);
        json.put("result", 1000D / Transaction.SATOSHIS_PER_BITCOIN); // Float; in Bitcoins, not Satoshis...
        jsonSocket.write(new JsonProtocolMessage(json));
        Logger.debug("Wrote: " + json);
        jsonSocket.flush();
    }

    protected void _notifyBlockHeader(final JsonSocket jsonSocket, final BlockHeader blockHeader, final Long blockHeight) {
        final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
        final ByteArray blockHeaderBytes = blockHeaderDeflater.toBytes(blockHeader);

        final Json blockHeaderJson = new ElectrumJson(false);
        blockHeaderJson.put("height", blockHeight);
        blockHeaderJson.put("hex", blockHeaderBytes);

        final Json paramsJson = new ElectrumJson(true);
        paramsJson.add(blockHeaderJson);

        final Json json = new ElectrumJson(false);
        json.put("method", "blockchain.headers.subscribe");
        json.put("params", paramsJson);
        jsonSocket.write(new JsonProtocolMessage(json));
        Logger.debug("Wrote: " + json);
        jsonSocket.flush();
    }

    protected void _handleSubscribeBlockHeadersMessage(final JsonSocket jsonSocket, final Json message) {
        final Integer id = message.getInteger("id");

        final ByteArray blockHeaderBytes;
        final Long blockHeight;
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            nodeConnection.enableKeepAlive(true);

            final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

            final Json blockHeadersJson = nodeConnection.getBlockHeadersBeforeHead(1, true);
            final Json blockHeadersArray = blockHeadersJson.get("blockHeaders");
            final String headerString = blockHeadersArray.getString(0);
            blockHeaderBytes = ByteArray.fromHexString(headerString);
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(blockHeaderBytes);

            final Json blockHeaderHeightJson = nodeConnection.getBlockHeaderHeight(blockHeader.getHash());
            blockHeight = blockHeaderHeightJson.getLong("blockHeight");
        }

        final Json blockHeaderJson = new ElectrumJson(false);
        blockHeaderJson.put("height", blockHeight);
        blockHeaderJson.put("hex", blockHeaderBytes);

        final Json json = new ElectrumJson(false);
        json.put("id", id);
        json.put("result", blockHeaderJson);
        jsonSocket.write(new JsonProtocolMessage(json));
        Logger.debug("Wrote: " + json);
        jsonSocket.flush();
    }

    protected static class GetBlockHeadersResult {
        public final int blockHeaderCount;
        public final String blockHeadersHex;
        public final MerkleRoot blockHeadersMerkleRoot;
        public final List<Sha256Hash> blockHeadersPartialMerkleTree;

        public GetBlockHeadersResult(final int blockHeaderCount, final String blockHeadersHex, final MerkleRoot blockHeadersMerkleRoot, final List<Sha256Hash> blockHeadersPartialMerkleTree) {
            this.blockHeaderCount = blockHeaderCount;
            this.blockHeadersHex = blockHeadersHex;
            this.blockHeadersMerkleRoot = blockHeadersMerkleRoot;
            this.blockHeadersPartialMerkleTree = blockHeadersPartialMerkleTree;
        }
    }

    protected GetBlockHeadersResult _getBlockHeaders(final Long requestedBlockHeight, final Integer requestedBlockCount, final Long checkpointBlockHeight) {
        final int blockHeaderCount;
        final String concatenatedHeadersHexString;
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            final Json blockHeadersJson = nodeConnection.getBlockHeadersAfter(requestedBlockHeight, requestedBlockCount, true);

            final Json blockHeadersArray = blockHeadersJson.get("blockHeaders");
            blockHeaderCount = blockHeadersArray.length();
            final StringBuilder headerStringBuilder = new StringBuilder();
            for (int i = 0; i < blockHeaderCount; ++i) {
                final String blockHeaderHexString = blockHeadersArray.getString(i);
                headerStringBuilder.append(blockHeaderHexString.toLowerCase());
            }

            concatenatedHeadersHexString = headerStringBuilder.toString();
        }

        final MerkleRoot merkleRoot;
        final List<Sha256Hash> partialMerkleTree;
        if (checkpointBlockHeight > 0L) {
            final MerkleTree<BlockHeader> blockHeaderMerkleTree = _calculateCheckpointMerkle(checkpointBlockHeight);
            final int headerIndex = (blockHeaderMerkleTree.getItemCount() - 1);

            merkleRoot = blockHeaderMerkleTree.getMerkleRoot();
            partialMerkleTree = blockHeaderMerkleTree.getPartialTree(headerIndex);

        }
        else {
            merkleRoot = null;
            partialMerkleTree = null;
        }

        return new GetBlockHeadersResult(blockHeaderCount, concatenatedHeadersHexString, merkleRoot, partialMerkleTree);
    }

    protected void _handleBlockHeadersMessage(final JsonSocket jsonSocket, final Json message) {
        final Integer id = message.getInteger("id");

        final Long requestedBlockHeight;
        final int requestedBlockCount;
        final Long checkpointBlockHeight;
        {
            final Json parameters = message.get("params");
            requestedBlockHeight = parameters.getLong(0);
            requestedBlockCount = Math.min(RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT, parameters.getInteger(1));
            checkpointBlockHeight = parameters.getLong(2);
        }

        final GetBlockHeadersResult blockHeadersResult = _getBlockHeaders(requestedBlockHeight, requestedBlockCount, checkpointBlockHeight);

        final Json resultJson = new ElectrumJson(false);
        resultJson.put("count", blockHeadersResult.blockHeaderCount);
        resultJson.put("hex", blockHeadersResult.blockHeadersHex); // Confirmed correct endianness.
        resultJson.put("max", RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT);

        if (checkpointBlockHeight > 0L) {
            resultJson.put("root", blockHeadersResult.blockHeadersMerkleRoot); // Confirmed correct and correct endianness.

            final Json merkleHashesJson = new ElectrumJson(true);
            for (final Sha256Hash merkleHash : blockHeadersResult.blockHeadersPartialMerkleTree) {
                merkleHashesJson.add(merkleHash); // Confirmed correct and correct endianness.
            }
            resultJson.put("branch", merkleHashesJson);
        }

        final Json json = new ElectrumJson(false);
        json.put("id", id);
        json.put("result", resultJson);
        jsonSocket.write(new JsonProtocolMessage(json));
        Logger.debug("Wrote: " + json);
        jsonSocket.flush();
    }

    protected void _handleBlockHeaderMessage(final JsonSocket jsonSocket, final Json message) {
        final Integer id = message.getInteger("id");

        final Long requestedBlockHeight;
        final Long checkpointBlockHeight;
        {
            final Json parameters = message.get("params");
            requestedBlockHeight = parameters.getLong(0);
            checkpointBlockHeight = parameters.getLong(1);
        }

        final GetBlockHeadersResult blockHeadersResult = _getBlockHeaders(requestedBlockHeight, 1, checkpointBlockHeight);

        final Json json = new ElectrumJson(false);
        json.put("id", id);

        if (checkpointBlockHeight > 0L) {
            final Json resultJson = new ElectrumJson(false);
            resultJson.put("header", blockHeadersResult.blockHeadersHex);
            resultJson.put("root", blockHeadersResult.blockHeadersMerkleRoot);

            final Json merkleHashesJson = new ElectrumJson(true);
            for (final Sha256Hash merkleHash : blockHeadersResult.blockHeadersPartialMerkleTree) {
                merkleHashesJson.add(merkleHash);
            }
            resultJson.put("branch", merkleHashesJson);
            json.put("result", resultJson);
        }
        else {
            json.put("result", blockHeadersResult.blockHeadersHex); // Confirmed correct endian.
        }

        jsonSocket.write(new JsonProtocolMessage(json));
        Logger.debug("Wrote: " + json);
        jsonSocket.flush();
    }

    protected void _handleScriptHashMessage(final JsonSocket jsonSocket, final Json message) {
        final Integer id = message.getInteger("id");
        final Json paramsJson = message.get("params");

        for (int i = 0; i < paramsJson.length(); ++i) {
            final String addressString = paramsJson.getString(i);

            {
                final Json json = new ElectrumJson(false);
                json.put("id", id);
                json.put("result", null);

                jsonSocket.write(new JsonProtocolMessage(json));
                Logger.debug("Wrote: " + json);
                jsonSocket.flush();
            }

//            {
//                final Json responseJson = new ElectrumJson(true);
//                responseJson.add(addressString);
//                responseJson.add(null);
//
//                final Json notificationJson = new ElectrumJson(false);
//                notificationJson.put("method", "blockchain.scripthash.subscribe");
//                notificationJson.put("params", responseJson);
//                jsonSocket.write(new JsonProtocolMessage(notificationJson));
//                Logger.debug("Wrote: " + notificationJson);
//                jsonSocket.flush();
//            }
        }
        jsonSocket.flush();
    }

    protected void _handlePeersMessage(final JsonSocket jsonSocket, final Json message) {
        final Integer id = message.getInteger("id");

        final Json json = new ElectrumJson(false);

        json.put("id", id);
        json.put("result", new ElectrumJson(true));
        jsonSocket.write(new JsonProtocolMessage(json));
        Logger.debug("Wrote: " + json);
        jsonSocket.flush();
    }

    protected void _onConnect(final JsonSocket jsonSocket) {
        Logger.info("Electrum Socket Connected: " + jsonSocket.getIp() + ":" + jsonSocket.getPort());

        jsonSocket.setMessageReceivedCallback(new Runnable() {
            @Override
            public synchronized void run() { // Intentionally allow only one-message at a time to force message FIFO processing.
                final JsonProtocolMessage jsonProtocolMessage = jsonSocket.popMessage();
                if (jsonProtocolMessage == null) { return; }

                final Json jsonMessage = jsonProtocolMessage.getMessage();
                Logger.debug("Received: " + jsonMessage);

                final String method = jsonMessage.getString("method");
                switch (method) {
                    case "server.version": {
                        _handleServerVersionMessage(jsonSocket, jsonMessage);
                    } break;
                    case "server.banner": {
                        _handleBannerMessage(jsonSocket, jsonMessage);
                    } break;
                    case "server.donation_address": {
                        _handleDonationAddressMessage(jsonSocket, jsonMessage);
                    } break;
                    case "server.ping": {
                        _handlePingMessage(jsonSocket, jsonMessage);
                    } break;
                    case "blockchain.relayfee": {
                        _handleMinimumRelayFeeMessage(jsonSocket, jsonMessage);
                    } break;
                    case "blockchain.headers.subscribe": {
                        _handleSubscribeBlockHeadersMessage(jsonSocket, jsonMessage);
                    } break;
                    case "server.peers.subscribe": {
                        _handlePeersMessage(jsonSocket, jsonMessage);
                    } break;
                    case "blockchain.scripthash.subscribe": {
                        _handleScriptHashMessage(jsonSocket, jsonMessage);
                    } break;
                    case "blockchain.block.headers": {
                        _handleBlockHeadersMessage(jsonSocket, jsonMessage);
                    } break;
                    case "blockchain.block.header": {
                        _handleBlockHeaderMessage(jsonSocket, jsonMessage);
                    } break;
                    default: {
                        Logger.debug("Received unsupported message: " + jsonMessage);
                    }
                }
            }
        });

        synchronized (_connections) {
            jsonSocket.setOnClosedCallback(new Runnable() {
                @Override
                public void run() {
                    synchronized (_connections) {
                        final int index = _connections.indexOf(jsonSocket);
                        if (index < 0) { return; }

                        _connections.remove(index);
                    }
                }
            });
            _connections.add(jsonSocket);
        }

        jsonSocket.beginListening();
    }

    protected void _onDisconnect(final JsonSocket jsonSocket) {
        Logger.debug("Electrum Socket Disconnected: " + jsonSocket.getIp() + ":" + jsonSocket.getPort());
    }

    public ElectrumModule(final ElectrumProperties electrumProperties) {
        _electrumProperties = electrumProperties;
        _threadPool = new CachedThreadPool(1024, 30000L);

        final Integer port = _electrumProperties.getHttpPort();
        final Integer tlsPort = _electrumProperties.getTlsPort();
        final TlsCertificate tlsCertificate;
        {
            final String certificateFile = _electrumProperties.getTlsCertificateFile();
            final String certificateKeyFile = _electrumProperties.getTlsKeyFile();
            tlsCertificate = _loadCertificate(certificateFile, certificateKeyFile);
        }

        _tlsIsEnabled = ((tlsCertificate != null) && (tlsPort != null));
        _electrumServerSocket = new ElectrumServerSocket(port, tlsPort, tlsCertificate, _threadPool);
        _electrumServerSocket.setSocketEventCallback(new ElectrumServerSocket.SocketEventCallback() {
            @Override
            public void onConnect(final JsonSocket socketConnection) {
                _onConnect(socketConnection);
            }

            @Override
            public void onDisconnect(final JsonSocket socketConnection) {
                _onDisconnect(socketConnection);
            }
        });

        _nodeNotificationConnectionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                final Thread thread = Thread.currentThread();
                while (! thread.isInterrupted()) {
                    try {
                        Thread.sleep(15000L);
                    }
                    catch (final InterruptedException exception) {
                        return;
                    }

                    if ( (_nodeNotificationConnection == null) || (! _nodeNotificationConnection.isConnected()) ) {
                        _createNodeNotificationConnection();
                    }
                }
            }
        });
        _nodeNotificationConnectionThread.setName("Node Notification Connection Maintenance Thread");
        _nodeNotificationConnectionThread.setDaemon(true);
    }

    public void loop() {
        final Thread mainThread = Thread.currentThread();

        _threadPool.start();

        _cacheBlockHeaders();
        _createNodeNotificationConnection();

        _electrumServerSocket.start();

        final Integer port = _electrumProperties.getHttpPort();
        Logger.info("Listening on port: " + port);
        if (_tlsIsEnabled) {
            final Integer tlsPort = _electrumProperties.getTlsPort();
            Logger.info("Listening on port: " + tlsPort);
        }

        while (! mainThread.isInterrupted()) {
            try { Thread.sleep(10000L); } catch (final Exception exception) { }
        }

        _electrumServerSocket.stop();

        synchronized (_connections) {
            for (final JsonSocket socket : _connections) {
                socket.close();
            }
            _connections.clear();
        }

        _nodeNotificationConnectionThread.interrupt();
        try {
            _nodeNotificationConnectionThread.join(30000L);
        }
        catch (final Exception exception) { }

        if (_nodeNotificationConnection != null) {
            _nodeNotificationConnection.close();
            _nodeNotificationConnection = null;
        }

        _threadPool.stop();
    }
}
