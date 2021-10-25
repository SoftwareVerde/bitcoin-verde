package com.softwareverde.bitcoin.server.module.stratum;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTree;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTreeNode;
import com.softwareverde.bitcoin.block.merkleroot.MutableMerkleTree;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.configuration.ElectrumProperties;
import com.softwareverde.bitcoin.server.electrum.socket.ElectrumServerSocket;
import com.softwareverde.bitcoin.server.message.type.query.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.server.module.stratum.json.ElectrumJson;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
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

public class ElectrumModule {
    protected final ElectrumProperties _electrumProperties;
    protected final Boolean _tlsIsEnabled;
    protected final CachedThreadPool _threadPool;
    protected final ElectrumServerSocket _electrumServerSocket;
    protected final MutableList<JsonSocket> _connections = new MutableList<>();

    protected final MerkleTree<BlockHeader> _cachedBlockHeaderMerkleTree;
    protected final Long _cachedBlockHeaderMerkleTreeBlockHeight;

    protected final NodeJsonRpcConnection _getNodeConnection() {
        final String nodeHost = _electrumProperties.getBitcoinRpcUrl();
        final Integer nodePort = _electrumProperties.getBitcoinRpcPort();
        System.out.println("Connect: " + nodeHost + ":" + nodePort);
        return new NodeJsonRpcConnection(nodeHost, nodePort, _threadPool);
    }

    protected MerkleTree<BlockHeader> _calculateCheckpointMerkle(final Long maxBlockHeight) {
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

        final MutableMerkleTree<BlockHeader> blockHeaderMerkleTree;
        long blockHeight;
        {
            blockHeight = 0L;
            blockHeaderMerkleTree = new MerkleTreeNode<>();
            if (_cachedBlockHeaderMerkleTree != null) {
                for (final BlockHeader blockHeader : _cachedBlockHeaderMerkleTree.getItems()) {
                    blockHeaderMerkleTree.addItem(blockHeader);
                }
                blockHeight = (_cachedBlockHeaderMerkleTreeBlockHeight + 1L);
            }
        }

        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            nodeConnection.enableKeepAlive(true);

            while (blockHeight <= maxBlockHeight) {
                final Json blockHeadersJson = nodeConnection.getBlockHeadersAfter(blockHeight, RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT, true);
                final Json blockHeadersArray = blockHeadersJson.get("blockHeaders");
                final int blockHeaderCount = blockHeadersArray.length();
                System.out.println("Received " + blockHeaderCount + " headers, starting at: " + blockHeight + " max=" + maxBlockHeight);
                for (int i = 0; i < blockHeaderCount; ++i) {
                    final String blockHeaderString = blockHeadersArray.getString(i);
                    final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString(blockHeaderString));
                    blockHeaderMerkleTree.addItem(blockHeader);
                    blockHeight += 1L;

                    if (blockHeight > maxBlockHeight) { break; } // Include the maxBlockHeight block...
                }
            }
        }

        System.out.println("Calculated: " + blockHeaderMerkleTree.getMerkleRoot());
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
        jsonSocket.flush();
    }

    protected void _handleBannerMessage(final JsonSocket jsonSocket, final Json message) {
        final Integer id = message.getInteger("id");

        final Json json = new ElectrumJson(false);

        json.put("id", id);
        json.put("result", "ElectrumVerde 1.0.0");
        jsonSocket.write(new JsonProtocolMessage(json));
        jsonSocket.flush();
    }

    protected void _handleDonationAddressMessage(final JsonSocket jsonSocket, final Json message) {
        final Integer id = message.getInteger("id");

        final Json json = new ElectrumJson(false);

        json.put("id", id);
        json.put("result", "qqverdefl9xtryyx8y52m6va5j8s2s4eq59fjdn97e");
        jsonSocket.write(new JsonProtocolMessage(json));
        jsonSocket.flush();
    }

    protected void _handleMinimumRelayFeeMessage(final JsonSocket jsonSocket, final Json message) {
        final Integer id = message.getInteger("id");

        final Json json = new ElectrumJson(false);

        json.put("id", id);
        json.put("result", "0.0"); // Float; in Bitcoins, not Satoshis...
        jsonSocket.write(new JsonProtocolMessage(json));
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
        // BlockHeader MUST be LE...
        blockHeaderJson.put("hex", blockHeaderBytes.toReverseEndian());

        {
            final Json paramsJson = new ElectrumJson(true);
            paramsJson.add(blockHeaderJson);

            final Json json = new ElectrumJson(false);
            // json.put("jsonrpc", "2.0");
            json.put("method", "blockchain.headers.subscribe");
            json.put("params", paramsJson);
            jsonSocket.write(new JsonProtocolMessage(json));
            Logger.debug("Wrote: " + json);
            jsonSocket.flush();
        }
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

        final int blockHeaderCount;
        final String concatenatedHeadersHexString;
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            final Json blockHeadersJson = nodeConnection.getBlockHeadersAfter(requestedBlockHeight, requestedBlockCount, true);

            final Json blockHeadersArray = blockHeadersJson.get("blockHeaders");
            blockHeaderCount = blockHeadersArray.length();
            final StringBuilder headerStringBuilder = new StringBuilder();
            for (int i = 0; i < blockHeaderCount; ++i) {
                final String blockHeaderHexString = blockHeadersArray.getString(i);
                headerStringBuilder.append(blockHeaderHexString);
            }

            concatenatedHeadersHexString = headerStringBuilder.toString();
        }

        final Json resultJson = new ElectrumJson(false);
        resultJson.put("count", blockHeaderCount);
        resultJson.put("hex", concatenatedHeadersHexString);
        resultJson.put("max", RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT);

        if (checkpointBlockHeight > 0L) {
            final MerkleTree<BlockHeader> blockHeaderMerkleTree = _calculateCheckpointMerkle(checkpointBlockHeight);

            final MerkleRoot merkleRoot = blockHeaderMerkleTree.getMerkleRoot();
            resultJson.put("root", merkleRoot);

            final List<Sha256Hash> partialMerkleTree = blockHeaderMerkleTree.getPartialTree(blockHeaderMerkleTree.getItemCount() - 1);
            final Json merkleHashesJson = new ElectrumJson(true);
            for (final Sha256Hash merkleHash : partialMerkleTree) {
                merkleHashesJson.add(merkleHash);
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

    protected void _handleScriptHashMessage(final JsonSocket jsonSocket, final Json message) {
        final Integer id = message.getInteger("id");
        final Json paramsJson = message.get("params");

        for (int i = 0; i < paramsJson.length(); ++i) {
            final String addressString = paramsJson.getString(i);

            final Json notificationJson = new ElectrumJson(false);

            final Json responseJson = new ElectrumJson(true);
            responseJson.add(addressString);
            responseJson.add(null);

            // notificationJson.put("id", id);
            // notificationJson.put("result", responseJson);

            notificationJson.put("method", "blockchain.scripthash.subscribe");
            notificationJson.put("params", responseJson);
            jsonSocket.write(new JsonProtocolMessage(notificationJson));
            Logger.debug("Wrote: " + notificationJson);
        }
        jsonSocket.flush();
    }

    protected void _handlePeersMessage(final JsonSocket jsonSocket, final Json message) {
        final Integer id = message.getInteger("id");

        final Json json = new ElectrumJson(false);

        json.put("id", id);
        json.put("result", new ElectrumJson(true));
        jsonSocket.write(new JsonProtocolMessage(json));
        jsonSocket.flush();
    }

    protected void _onConnect(final JsonSocket jsonSocket) {
        Logger.info("Electrum Socket Connected: " + jsonSocket.getIp() + ":" + jsonSocket.getPort());

        jsonSocket.setMessageReceivedCallback(new Runnable() {
            @Override
            public void run() {
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

        final Long cachedBlockHeaderMerkleTreeBlockHeight = 700000L; // 600000L;
        // _cachedBlockHeaderMerkleTree = _calculateCheckpointMerkle(cachedBlockHeaderMerkleTreeBlockHeight);
        // _cachedBlockHeaderMerkleTreeBlockHeight = cachedBlockHeaderMerkleTreeBlockHeight;
        _cachedBlockHeaderMerkleTree = _calculateCheckpointMerkle(cachedBlockHeaderMerkleTreeBlockHeight);
        _cachedBlockHeaderMerkleTreeBlockHeight = cachedBlockHeaderMerkleTreeBlockHeight;
    }

    public void loop() {
        final Thread mainThread = Thread.currentThread();

        _threadPool.start();
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

        _threadPool.stop();
    }
}
