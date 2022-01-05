package com.softwareverde.bitcoin.server.module.electrum;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
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
import com.softwareverde.bitcoin.server.main.NetworkType;
import com.softwareverde.bitcoin.server.message.type.query.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.server.module.electrum.json.ElectrumJson;
import com.softwareverde.bitcoin.server.module.electrum.json.ElectrumJsonProtocolMessage;
import com.softwareverde.bitcoin.server.module.node.sync.bootstrap.HeadersBootstrapper;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.ScriptInflater;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.concurrent.ConcurrentHashSet;
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
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ElectrumModule {
    public static final String SERVER_VERSION = "Electrum Verde 1.0.3";
    public static final String BANNER = ElectrumModule.SERVER_VERSION;
    public static final String PROTOCOL_VERSION = "1.4.4";

    protected static Json createErrorJson(final Object requestId, final String errorMessage, final Integer errorCode) {
        final Json errorJson = new ElectrumJson(false);
        errorJson.put("message", errorMessage);
        errorJson.put("code", errorCode);

        final Json json = new ElectrumJson(false);
        json.put("id", requestId);
        json.put("result", null);
        json.put("error", errorJson);

        return json;
    }

    protected static Object getRequestId(final Json json) {
        final String idString = json.getOrNull("id", Json.Types.STRING);
        if (Util.isInt(idString)) {
            return Util.parseInt(idString);
        }

        return idString;
    }

    protected final Long _minTransactionFeePerByte;

    protected final ElectrumProperties _electrumProperties;
    protected final Boolean _tlsIsEnabled;
    protected final CachedThreadPool _threadPool;
    protected final ElectrumServerSocket _electrumServerSocket;
    protected final ConcurrentHashMap<Long, JsonSocket> _connections = new ConcurrentHashMap<>();
    protected final HashMap<AddressSubscriptionKey, LinkedList<ConnectionAddress>> _connectionAddresses = new HashMap<>();
    protected final ConcurrentHashMap<Long, ElectrumPeer> _peers = new ConcurrentHashMap<>();
    protected final ConcurrentHashSet<Ip> _bannedConnections = new ConcurrentHashSet<>();
    protected final ConcurrentHashMap<Ip, WorkerThread> _workerThreads = new ConcurrentHashMap<>();

    protected final ReentrantReadWriteLock.WriteLock _blockHeaderCacheWriteLock;
    protected final ReentrantReadWriteLock.ReadLock _blockHeaderCacheReadLock;
    protected final MutableList<BlockHeader> _cachedBlockHeaders = new MutableList<>(0);
    protected Long _chainHeight = 0L;

    protected final Thread _maintenanceThread;
    protected NodeJsonRpcConnection _nodeNotificationConnection;

    protected void _debugWriteMessage(final JsonSocket jsonSocket, final Json json) {
        if (Logger.isDebugEnabled()) {
            Logger.debug("[To " + jsonSocket + "] " + json);
        }
    }

    protected void _debugReceivedMessage(final JsonSocket jsonSocket, final Json json) {
        if (Logger.isDebugEnabled()) {
            Logger.debug("[From " + jsonSocket + "] " + json);
        }
    }

    protected final ConcurrentHashMap<Sha256Hash, Json> _cachedTransactionBlockHeights = new ConcurrentHashMap<>();
    protected Json _getTransactionBlockHeight(final Sha256Hash transactionHash, final NodeJsonRpcConnection nodeConnection) {
        final Json cachedTransactionBlockHeight = _cachedTransactionBlockHeights.get(transactionHash);
        if (cachedTransactionBlockHeight != null) { return cachedTransactionBlockHeight; }

        final Json transactionBlockHeightJson = nodeConnection.getTransactionBlockHeight(transactionHash);
        if (transactionBlockHeightJson == null) { return null; }

        _cachedTransactionBlockHeights.put(transactionHash, transactionBlockHeightJson);
        if (_cachedTransactionBlockHeights.size() > (1024 * 2)) {
            // NOTE: From testing, removing items via the EntrySet Iterator appears to remove the items in FIFO order...
            final Set<Map.Entry<Sha256Hash, Json>> entrySet = _cachedTransactionBlockHeights.entrySet();
            final Iterator<Map.Entry<Sha256Hash, Json>> iterator = entrySet.iterator();

            for (int i = 0; i < 128; ++i) {
                if (! iterator.hasNext()) { break; }

                iterator.next();
                iterator.remove();
            }
        }

        return transactionBlockHeightJson;
    }

    protected final Integer _maxCachedConnectionCount = 32;
    protected final Long _maxCachedConnectionLifespan = 10000L;
    protected final AtomicInteger _cachedConnectionCount = new AtomicInteger(0);
    protected final ConcurrentLinkedDeque<CachedNodeJsonRpcConnection> _cachedConnections = new ConcurrentLinkedDeque<>();
    protected NodeJsonRpcConnection _getNodeConnection() {
        while (true) { // Attempt to serve a cached connection...
            final CachedNodeJsonRpcConnection cachedConnection = _cachedConnections.pollFirst();
            if (cachedConnection == null) { break; }
            _cachedConnectionCount.getAndDecrement();

            if (! cachedConnection.isConnected()) {
                cachedConnection.superClose();
                continue;
            }

            if (cachedConnection.getConnectionDuration() > _maxCachedConnectionLifespan) {
                cachedConnection.superClose();
                continue;
            }

            return cachedConnection;
        }

        final String nodeHost = _electrumProperties.getBitcoinRpcUrl();
        final Integer nodePort = _electrumProperties.getBitcoinRpcPort();
        final CachedNodeJsonRpcConnection nodeConnection = new CachedNodeJsonRpcConnection(nodeHost, nodePort, _threadPool);
        nodeConnection.enableKeepAlive(true);
        nodeConnection.setOnCloseCallback(new Runnable() {
            @Override
            public void run() {
                if ( (! nodeConnection.isConnected()) || (_cachedConnectionCount.get() >= _maxCachedConnectionCount) ) {
                    nodeConnection.superClose();
                    return;
                }

                final Long ping = nodeConnection.ping();
                if (ping == null) {
                    nodeConnection.superClose();
                    return;
                }

                _cachedConnections.addLast(nodeConnection);
                _cachedConnectionCount.getAndIncrement();
            }
        });

        if (! nodeConnection.isConnected()) {
            throw new RuntimeException("Unable to connect to node.");
        }

        return nodeConnection;
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
                _onNewHeader(blockHeader);
            }

            @Override
            public void onNewTransaction(final Transaction transaction, final Long fee) {
                _onNewTransaction(transaction, fee);
            }
        });
    }

    protected void _onNewHeader(final BlockHeader blockHeader) {
        final Sha256Hash blockHash = blockHeader.getHash();

        final Long blockHeight;
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            final Json blockHeightJson = nodeConnection.getBlockHeaderHeight(blockHash);
            blockHeight = blockHeightJson.getLong("blockHeight");
        }

        Logger.debug("New Header: " + blockHash + " " + blockHeight);

        for (final JsonSocket socket : _connections.values()) {
            _notifyBlockHeader(socket, blockHeader, blockHeight);
        }

        // Invalidate cached transaction heights...
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            final BlockInflater blockInflater = new BlockInflater();
            final Json blockJson = nodeConnection.getBlock(blockHash, true);
            final String blockHexString = blockJson.getString("block");

            final Block block = blockInflater.fromBytes(ByteArray.fromHexString(blockHexString));
            if (block == null) {
                _cachedTransactionBlockHeights.clear();
                Logger.info("Unable to inflate new block; clearing cache.");
            }
            else {
                for (final Transaction transaction : block.getTransactions()) {
                    final Sha256Hash transactionHash = transaction.getHash();
                    _cachedTransactionBlockHeights.remove(transactionHash);
                }
            }

        }

        synchronized (_connectionAddresses) {
            for (final Map.Entry<AddressSubscriptionKey, LinkedList<ConnectionAddress>> entry : _connectionAddresses.entrySet()) {
                for (final ConnectionAddress connectionAddress : entry.getValue()) {
                    final JsonSocket jsonSocket = connectionAddress.connection.get();
                    final boolean isConnected = ((jsonSocket != null) && jsonSocket.isConnected());
                    if (! isConnected) {
                        connectionAddress.status = null;
                        continue;
                    }

                    Logger.trace(connectionAddress.subscriptionKey + " = " + connectionAddress.status);

                    final Sha256Hash addressStatus = _calculateAddressStatus(connectionAddress.subscriptionKey);
                    if (! Util.areEqual(addressStatus, connectionAddress.status)) {
                        connectionAddress.status = addressStatus;
                        Logger.debug("Updated Status: " + connectionAddress.subscriptionKey + " = " + connectionAddress.status);

                        if (connectionAddress.subscriptionKey.isScriptHash) {
                            _notifyScriptHashStatus(jsonSocket, connectionAddress.subscriptionKey, addressStatus);
                        }
                        else {
                            _notifyAddressStatus(jsonSocket, connectionAddress.subscriptionKey, addressStatus);
                        }
                    }
                }
            }
        }
    }

    protected void _onNewTransaction(final Transaction transaction, final Long nullableFee) {
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
        final AddressInflater addressInflater = new AddressInflater();

        final HashSet<Address> matchedAddresses = new HashSet<>();
        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            final LockingScript lockingScript = transactionOutput.getLockingScript();

            final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
            if (scriptType == null) { continue; }

            final Address address = scriptPatternMatcher.extractAddress(scriptType, lockingScript);
            if (address == null) { continue; }

            final AddressSubscriptionKey addressSubscriptionKey = new AddressSubscriptionKey(address, null);
            synchronized (_connectionAddresses) {
                final boolean addressIsConnected = _connectionAddresses.containsKey(addressSubscriptionKey);
                if (addressIsConnected) {
                    matchedAddresses.add(address);
                }
            }
        }
        boolean allInputsWerePayToPublicKeyHash = true;
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final UnlockingScript unlockingScript = transactionInput.getUnlockingScript();

            final Address address = scriptPatternMatcher.extractAddressFromPayToPublicKeyHash(unlockingScript);
            if (address == null) {
                allInputsWerePayToPublicKeyHash = false;
                continue;
            }

            final AddressSubscriptionKey addressSubscriptionKey = new AddressSubscriptionKey(address, null);
            synchronized (_connectionAddresses) {
                final boolean addressIsConnected = _connectionAddresses.containsKey(addressSubscriptionKey);
                if (addressIsConnected) {
                    matchedAddresses.add(address);
                }
            }
        }

        if (! allInputsWerePayToPublicKeyHash) {
            final Sha256Hash transactionHash = transaction.getHash();
            try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
                final Json getTransactionJson = nodeConnection.getTransaction(transactionHash, false);
                if (getTransactionJson != null) {
                    final Json transactionJson = getTransactionJson.get("transaction");
                    final Json transactionInputsJson = transactionJson.get("inputs");
                    for (int i = 0; i < transactionInputsJson.length(); ++i) {
                        final Json transactionInputJson = transactionInputsJson.get(i);
                        final String addressString = transactionInputJson.getString("address");

                        final Address address = addressInflater.fromBase58Check(addressString);
                        if (address == null) { continue; }

                        final AddressSubscriptionKey addressSubscriptionKey = new AddressSubscriptionKey(address, null);
                        synchronized (_connectionAddresses) {
                            final boolean addressIsConnected = _connectionAddresses.containsKey(addressSubscriptionKey);
                            if (addressIsConnected) {
                                matchedAddresses.add(address);
                            }
                        }
                    }
                }
            }
        }

        for (final Address address : matchedAddresses) {
            synchronized (_connectionAddresses) {
                final LinkedList<ConnectionAddress> connectionAddresses = _connectionAddresses.get(new AddressSubscriptionKey(address, null));
                for (final ConnectionAddress connectionAddress : connectionAddresses) {
                    final JsonSocket jsonSocket = connectionAddress.connection.get();
                    final boolean jsonSocketIsConnected = ((jsonSocket != null) && jsonSocket.isConnected());
                    if (! jsonSocketIsConnected) { continue; }

                    final Sha256Hash status = _calculateAddressStatus(connectionAddress.subscriptionKey);
                    connectionAddress.status = status;
                    Logger.debug("Updated Status: " + connectionAddress);

                    if (connectionAddress.subscriptionKey.isScriptHash) {
                        _notifyScriptHashStatus(jsonSocket, connectionAddress.subscriptionKey, status);
                    }
                    else {
                        _notifyAddressStatus(jsonSocket, connectionAddress.subscriptionKey, status);
                    }
                }
            }
        }
    }

    protected Long _readHeadersFromStream(final InputStream inputStream) throws IOException {
        long headerCount = 0L;

        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        final MutableByteArray buffer = new MutableByteArray(BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT);
        while (true) {
            int readByteCount = inputStream.read(buffer.unwrap());
            while ((readByteCount >= 0) && (readByteCount < BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT)) {
                final int nextByte = inputStream.read();
                if (nextByte < 0) { break; }

                buffer.setByte(readByteCount, (byte) nextByte);
                readByteCount += 1;
            }
            if (readByteCount != BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT) { return headerCount; }

            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(buffer);
            if (blockHeader == null) { return headerCount; }

            _cachedBlockHeaders.add(blockHeader);
            headerCount += 1L;
        }
    }

    protected Long _loadBootstrappedHeaders() {
        final NetworkType networkType = _electrumProperties.getNetworkType();
        if (networkType != NetworkType.MAIN_NET) {
            return -1L;
        }

        try (final InputStream inputStream = HeadersBootstrapper.class.getResourceAsStream("/bootstrap/headers.dat")) {
            if (inputStream == null) {
                Logger.warn("Unable to open headers bootstrap file.");
                return -1L;
            }

            return (_readHeadersFromStream(inputStream) - 1L);
        }
        catch (final Exception exception) {
            Logger.warn(exception);
        }

        return -1L;
    }

    protected void _cacheBlockHeaders() {
        final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
        final File dataDirectory = _electrumProperties.getDataDirectory();
        final File headersCacheFile = new File(dataDirectory, "headers.dat");
        final boolean shouldWriteToHeadersFile;
        if (headersCacheFile.exists()) {
            shouldWriteToHeadersFile = true;
        }
        else {
            boolean dataFileCreatedSuccessfully;
            try {
                dataFileCreatedSuccessfully = headersCacheFile.createNewFile();
            }
            catch (final Exception exception) {
                dataFileCreatedSuccessfully = false;
            }

            if (! dataFileCreatedSuccessfully) {
                Logger.info("Unable to create headers data file: " + headersCacheFile);
            }

            shouldWriteToHeadersFile = dataFileCreatedSuccessfully;
        }

        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        _blockHeaderCacheWriteLock.lock();
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            final Json blockHeightJson = nodeConnection.getBlockHeight();
            _chainHeight = blockHeightJson.getLong("blockHeight");
            _cachedBlockHeaders.clear();

            final long maxBlockHeight = Math.max(0L, _chainHeight - RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT);
            long blockHeight = (_loadBootstrappedHeaders() + 1L);
            try (final FileInputStream inputStream = new FileInputStream(headersCacheFile)) {
                blockHeight += _readHeadersFromStream(inputStream);
            }

            try (final BufferedOutputStream headersOutputStream = (shouldWriteToHeadersFile ? new BufferedOutputStream(new FileOutputStream(headersCacheFile, true)) : null)) {
                while (blockHeight <= maxBlockHeight) {
                    final Json blockHeadersJson = nodeConnection.getBlockHeadersAfter(blockHeight, RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT, true);
                    final Json blockHeadersArray = blockHeadersJson.get("blockHeaders");
                    final int blockHeaderCount = blockHeadersArray.length();
                    Logger.debug("Received " + blockHeaderCount + " headers, starting at: " + blockHeight + " max=" + maxBlockHeight);
                    for (int i = 0; i < blockHeaderCount; ++i) {
                        final String blockHeaderString = blockHeadersArray.getString(i);
                        final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString(blockHeaderString));
                        _cachedBlockHeaders.add(blockHeader);

                        if (headersOutputStream != null) {
                            final ByteArray blockHeaderBytes = blockHeaderDeflater.toBytes(blockHeader);
                            headersOutputStream.write(blockHeaderBytes.getBytes());
                        }

                        blockHeight += 1L;

                        if (blockHeight > maxBlockHeight) { break; } // Include the maxBlockHeight block...
                    }
                }

                if (headersOutputStream != null) {
                    headersOutputStream.flush();
                }
            }
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }
        finally {
            _blockHeaderCacheWriteLock.unlock();
        }
    }

    protected MerkleTree<BlockHeader> _calculateBlockHeadersMerkle(final Long checkpointBlockHeight) {
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

        final MutableMerkleTree<BlockHeader> blockHeaderMerkleTree = new MerkleTreeNode<>();
        long blockHeight;
        {
            blockHeight = 0L;
            _blockHeaderCacheReadLock.lock();
            try {
                while ( (blockHeight < _cachedBlockHeaders.getCount()) && (blockHeight <= checkpointBlockHeight) ) {
                    final BlockHeader blockHeader = _cachedBlockHeaders.get((int) blockHeight);
                    blockHeaderMerkleTree.addItem(blockHeader);
                    blockHeight += 1L;
                }
            }
            finally {
                _blockHeaderCacheReadLock.unlock();
            }
        }

        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            final Long chainHeight;
            {
                final Json blockHeightJson = nodeConnection.getBlockHeight();
                chainHeight = blockHeightJson.getLong("blockHeight");
            }

            while ( (blockHeight <= chainHeight) && (blockHeight <= checkpointBlockHeight) ) {
                final Json blockHeadersJson = nodeConnection.getBlockHeadersAfter(blockHeight, RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT, true);
                final Json blockHeadersArray = blockHeadersJson.get("blockHeaders");
                final int blockHeaderCount = blockHeadersArray.length();
                Logger.debug("Received " + blockHeaderCount + " headers, starting at: " + blockHeight);
                if (blockHeaderCount < 1) { break; }

                for (int i = 0; i < blockHeaderCount; ++i) {
                    final String blockHeaderString = blockHeadersArray.getString(i);
                    final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString(blockHeaderString));
                    if (blockHeight <= checkpointBlockHeight) {
                        blockHeaderMerkleTree.addItem(blockHeader);
                    }

                    // Keep the cache up-to-date...
                    if (blockHeight < (chainHeight - RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT)) {
                        _blockHeaderCacheWriteLock.lock();
                        try {
                            _cachedBlockHeaders.add(blockHeader);
                        }
                        finally {
                            _blockHeaderCacheWriteLock.unlock();
                        }
                    }

                    blockHeight += 1L;
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
        final Object id = ElectrumModule.getRequestId(message);

        final Json json = new ElectrumJson(false);
        final Json resultJson = new ElectrumJson(true);
        resultJson.add(ElectrumModule.SERVER_VERSION);
        resultJson.add(ElectrumModule.PROTOCOL_VERSION);

        json.put("id", id);
        json.put("result", resultJson);
        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handleBannerMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);

        final Json json = new ElectrumJson(false);

        json.put("id", id);
        json.put("result", ElectrumModule.BANNER);
        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handleDonationAddressMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);

        final Address donationAddress = _electrumProperties.getDonationAddress();

        final Json json = new ElectrumJson(false);

        json.put("id", id);
        json.put("result", (donationAddress != null ? donationAddress.toBase32CheckEncoded() : ""));
        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handlePingMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);

        final Json json = new ElectrumJson(false);

        json.put("id", id);
        json.put("result", null);
        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handleMinimumRelayFeeMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);

        final Json json = new ElectrumJson(false);

        final double minRelayFee = ((_minTransactionFeePerByte * 1000L) / Transaction.SATOSHIS_PER_BITCOIN.doubleValue());

        json.put("id", id);
        json.put("result", minRelayFee); // Float; in Bitcoins, not Satoshis...
        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handleEstimateFeeMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);

        final Long blockCount;
        {
            final Json parameters = message.get("params");
            blockCount = parameters.getLong(0);
        }

        final double minRelayFee = ((_minTransactionFeePerByte * 1000L) / Transaction.SATOSHIS_PER_BITCOIN.doubleValue());

        final Json json = new ElectrumJson(false);
        json.put("id", id);
        json.put("result", minRelayFee); // Float; in Bitcoins, not Satoshis...
        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
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
        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handleSubscribeBlockHeadersMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);

        final ByteArray blockHeaderBytes;
        final Long blockHeight;
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

            final Json blockHeadersJson = nodeConnection.getBlockHeadersBeforeHead(1, true);
            final Json blockHeadersArray = blockHeadersJson.get("blockHeaders");
            final String headerString = blockHeadersArray.getString(0);
            blockHeaderBytes = ByteArray.fromHexString(headerString);
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(blockHeaderBytes);
            if (blockHeader == null) {
                Logger.debug("Unable to get head block hash.");
                return;
            }

            final Json blockHeaderHeightJson = nodeConnection.getBlockHeaderHeight(blockHeader.getHash());
            blockHeight = blockHeaderHeightJson.getLong("blockHeight");
        }

        final Json blockHeaderJson = new ElectrumJson(false);
        blockHeaderJson.put("height", blockHeight);
        blockHeaderJson.put("hex", blockHeaderBytes);

        final Json json = new ElectrumJson(false);
        json.put("id", id);
        json.put("result", blockHeaderJson);
        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
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

    protected Json _createFeaturesJson() {
        final Json json = new ElectrumJson(false);

        final Json hostsJson;
        {
            hostsJson = new ElectrumJson(false);
            for (final ElectrumPeer electrumPeer : _peers.values()) {
                final Json hostPortsJson = new ElectrumJson(false);
                hostPortsJson.put("tcp_port", electrumPeer.tcpPort);
                hostPortsJson.put("ssl_port", electrumPeer.tlsPort);

                hostsJson.put(electrumPeer.host, hostPortsJson);
            }
        }

        json.put("hosts", hostsJson);
        json.put("genesis_hash", BlockHeader.GENESIS_BLOCK_HASH);
        json.put("hash_function", "sha256");
        json.put("server_version", ElectrumModule.SERVER_VERSION);
        json.put("protocol_max", ElectrumModule.PROTOCOL_VERSION);
        json.put("protocol_min", "1.0");
        json.put("pruning", null);

        return json;
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
            final MerkleTree<BlockHeader> blockHeaderMerkleTree = _calculateBlockHeadersMerkle(checkpointBlockHeight);
            final int headerIndex = (blockHeaderMerkleTree.getItemCount() - 1);

            merkleRoot = blockHeaderMerkleTree.getMerkleRoot();
            partialMerkleTree = blockHeaderMerkleTree.getPartialTree(headerIndex, true);
        }
        else {
            merkleRoot = null;
            partialMerkleTree = null;
        }

        return new GetBlockHeadersResult(blockHeaderCount, concatenatedHeadersHexString, merkleRoot, partialMerkleTree);
    }

    protected void _handleSubmitTransactionMessage(final JsonSocket jsonSocket, final Json message) {
        final TransactionInflater transactionInflater = new TransactionInflater();
        final Object id = ElectrumModule.getRequestId(message);

        final Transaction transaction;
        {
            final Json parameters = message.get("params");
            final String transactionHex = parameters.getString(0);
            transaction = transactionInflater.fromBytes(ByteArray.fromHexString(transactionHex));

            if (transaction == null) {
                final Json json = ElectrumModule.createErrorJson(id, "Invalid Transaction hex.", null);
                jsonSocket.write(new ElectrumJsonProtocolMessage(json));
                _debugWriteMessage(jsonSocket, json);
                return;
            }
        }

        final Sha256Hash transactionHash;
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            nodeConnection.submitTransaction(transaction);
            transactionHash = transaction.getHash();
        }

        final Json json = new ElectrumJson(false);
        json.put("id", id);
        json.put("result", transactionHash);
        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        _debugWriteMessage(jsonSocket, json);
        jsonSocket.flush();
    }

    protected void _handleBlockHeadersMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);

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
        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handleBlockHeaderMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);

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

        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handleGetTransactionMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);

        final Boolean verboseFormat;
        final Sha256Hash transactionHash;
        {
            final Json parameters = message.get("params");
            final String transactionHashString = parameters.getString(0);
            transactionHash = Sha256Hash.fromHexString(transactionHashString);
            if (transactionHash == null) {
                final String errorMessage = "Invalid Transaction Hash: " + transactionHashString;
                final Json json = ElectrumModule.createErrorJson(id, errorMessage, null);
                jsonSocket.write(new ElectrumJsonProtocolMessage(json));
                jsonSocket.flush();

                _debugWriteMessage(jsonSocket, json);
                return;
            }

            verboseFormat = parameters.getBoolean(1);
            if (verboseFormat) {
                final Json json = ElectrumModule.createErrorJson(id, "Unsupported Get-Transaction mode.", null);
                jsonSocket.write(new ElectrumJsonProtocolMessage(json));
                jsonSocket.flush();

                _debugWriteMessage(jsonSocket, json);
                return;
            }
        }

        final String transactionHexString;
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            final Json transactionJson = nodeConnection.getTransaction(transactionHash, true);
            final String transactionHexUppercaseString = transactionJson.getString("transaction");
            transactionHexString = transactionHexUppercaseString.toLowerCase();
        }

        if (Util.isBlank(transactionHexString)) {
            final Json json = ElectrumModule.createErrorJson(id, "Unable to load transaction.", null);
            jsonSocket.write(new ElectrumJsonProtocolMessage(json));
            jsonSocket.flush();

            _debugWriteMessage(jsonSocket, json);
            return;
        }

        final Json json = new ElectrumJson(false);
        json.put("id", id);
        json.put("result", transactionHexString);

        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handleGetTransactionMerkleProofMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);

        final Long blockHeight;
        final Sha256Hash transactionHash;
        {
            final Json parameters = message.get("params");
            final String transactionHashString = parameters.getString(0);
            transactionHash = Sha256Hash.fromHexString(transactionHashString);
            if (transactionHash == null) {
                final String errorMessage = "Invalid Transaction Hash: " + transactionHashString;
                final Json json = ElectrumModule.createErrorJson(id, errorMessage, null);
                jsonSocket.write(new ElectrumJsonProtocolMessage(json));
                jsonSocket.flush();

                _debugWriteMessage(jsonSocket, json);
                return;
            }

            blockHeight = parameters.getLong(1);
        }

        final Json resultJson = new ElectrumJson(false);
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            final Json transactionBlockHeightJson = _getTransactionBlockHeight(transactionHash, nodeConnection);
            final Long actualBlockHeight = transactionBlockHeightJson.getOrNull("blockHeight", Json.Types.LONG);
            final Integer transactionIndex = transactionBlockHeightJson.getOrNull("transactionIndex", Json.Types.INTEGER);
            final Boolean hasUnconfirmedInputs = transactionBlockHeightJson.getBoolean("hasUnconfirmedInputs");

            if ( (actualBlockHeight == null) || (! Util.areEqual(actualBlockHeight, blockHeight))) {
                final String errorMessage = "Transaction not found: " + transactionHash + ":" + blockHeight;
                final Json json = ElectrumModule.createErrorJson(id, errorMessage, null);
                jsonSocket.write(new ElectrumJsonProtocolMessage(json));
                jsonSocket.flush();

                _debugWriteMessage(jsonSocket, json);
                return;
            }

            final BlockInflater blockInflater = new BlockInflater();
            final Json blockJson = nodeConnection.getBlock(blockHeight, true);
            final Block block = blockInflater.fromBytes(ByteArray.fromHexString(blockJson.getString("block")));

            final List<Sha256Hash> partialMerkleTree = block.getPartialMerkleTree(transactionIndex, false);
            final Json partialMerkleTreeJson = new ElectrumJson(true);
            for (final Sha256Hash item : partialMerkleTree) {
                partialMerkleTreeJson.add(item);
            }

            resultJson.put("block_height", actualBlockHeight);
            resultJson.put("merkle", partialMerkleTreeJson);
            resultJson.put("pos", transactionIndex);
        }

        final Json json = new ElectrumJson(false);
        json.put("id", id);
        json.put("result", resultJson);


        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handleConvertAddressToScriptHashMessage(final JsonSocket jsonSocket, final Json message) {
        final AddressInflater addressInflater = new AddressInflater();

        final Object id = ElectrumModule.getRequestId(message);
        final Json paramsJson = message.get("params");

        final Address address;
        {
            final String addressString = paramsJson.getString(0);
            address = Util.coalesce(addressInflater.fromBase32Check(addressString), addressInflater.fromBase58Check(addressString));
            if (address == null) {
                final String errorMessage = "Invalid address.";
                final Json json = ElectrumModule.createErrorJson(id, errorMessage, null);
                jsonSocket.write(new ElectrumJsonProtocolMessage(json));
                jsonSocket.flush();

                _debugWriteMessage(jsonSocket, json);
                return;
            }
        }

        final AddressSubscriptionKey addressKey = new AddressSubscriptionKey(address, null);

        final Json json = new ElectrumJson(false);
        json.put("id", id);
        json.put("result", addressKey.scriptHash);
        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handleGetTransactionFromBlockPositionMessage(final JsonSocket jsonSocket, final Json message) {
        final BlockInflater blockInflater = new BlockInflater();

        final Object id = ElectrumModule.getRequestId(message);
        final Json paramsJson = message.get("params");

        final Sha256Hash blockHash;
        final Integer transactionIndex;
        final Boolean includePartialMerkleTree;
        {
            blockHash = Sha256Hash.fromHexString(paramsJson.getString(0));
            transactionIndex = paramsJson.getInteger(1);
            includePartialMerkleTree = paramsJson.getBoolean(2);

            if ( (blockHash == null) || (transactionIndex < 0) ) {
                final String errorMessage = "Invalid block position.";
                final Json json = ElectrumModule.createErrorJson(id, errorMessage, null);
                jsonSocket.write(new ElectrumJsonProtocolMessage(json));
                jsonSocket.flush();

                _debugWriteMessage(jsonSocket, json);
                return;
            }
        }

        final Sha256Hash transactionHash;
        final List<Sha256Hash> partialMerkleTree;
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            final Json blockJson = nodeConnection.getBlock(blockHash, true);
            final Block block = blockInflater.fromBytes(ByteArray.fromHexString(blockJson.getString("block")));

            final List<Transaction> transactions = block.getTransactions();
            if (transactionIndex >= transactions.getCount()) {
                final String errorMessage = "Invalid block position.";
                final Json json = ElectrumModule.createErrorJson(id, errorMessage, null);
                jsonSocket.write(new ElectrumJsonProtocolMessage(json));
                jsonSocket.flush();

                _debugWriteMessage(jsonSocket, json);
                return;
            }

            final Transaction transaction = transactions.get(transactionIndex);

            transactionHash = transaction.getHash();
            partialMerkleTree = (includePartialMerkleTree ? block.getPartialMerkleTree(transactionIndex, false) : null);
        }

        final Json json = new ElectrumJson(false);
        json.put("id", id);


        if (includePartialMerkleTree) {
            final Json resultJson = new Json();
            resultJson.put("tx_hash", transactionHash);

            final Json merkleTreeJson = new ElectrumJson(true);
            for (final Sha256Hash itemHash : partialMerkleTree) {
                merkleTreeJson.add(itemHash);
            }
            resultJson.put("merkle", merkleTreeJson);

            json.put("result", resultJson);
        }
        else {
            json.put("result", transactionHash);
        }

        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handleGetUtxoInfoMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);
        final Json paramsJson = message.get("params");

        final Sha256Hash transactionHash;
        final Integer transactionOutputIndex;
        {
            transactionHash = Sha256Hash.fromHexString(paramsJson.getString(0));
            transactionOutputIndex = paramsJson.getInteger(1);
            if (transactionOutputIndex < 0) {
                final String errorMessage = "Invalid transaction output.";
                final Json json = ElectrumModule.createErrorJson(id, errorMessage, null);
                jsonSocket.write(new ElectrumJsonProtocolMessage(json));
                jsonSocket.flush();

                _debugWriteMessage(jsonSocket, json);
                return;
            }
        }

        final Long amount;
        final Long blockHeight;
        final Sha256Hash scriptHash;
        final Address address;
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            final Json getTransactionJson = nodeConnection.getTransaction(transactionHash, false);
            final Json transactionJson = getTransactionJson.get("transaction");
            final Json transactionOutputsJson = transactionJson.get("outputs");
            if (transactionOutputIndex >= transactionOutputsJson.length()) {
                final String errorMessage = "Invalid outputIndex: " + transactionHash + ":" + transactionOutputIndex;
                final Json json = ElectrumModule.createErrorJson(id, errorMessage, null);
                jsonSocket.write(new ElectrumJsonProtocolMessage(json));
                jsonSocket.flush();

                _debugWriteMessage(jsonSocket, json);
                return;
            }

            Long transactionBlockHeight = null;
            final Json minedInBlocksJson = transactionJson.get("blocks");
            if (minedInBlocksJson.length() > 0) {
                for (int i = 0; i < minedInBlocksJson.length(); ++i) {
                    final Sha256Hash blockHash = Sha256Hash.fromHexString(minedInBlocksJson.getString(i));
                    final Json getBlockHeightJson = nodeConnection.getBlockHeaderHeight(blockHash);

                    final boolean isConnectedToMainChain = (! getBlockHeightJson.getBoolean("isOrphan"));
                    if (! isConnectedToMainChain) { continue; }

                    final Long jsonBlockHeight = getBlockHeightJson.getLong("blockHeight");
                    if ( (transactionBlockHeight == null) || (jsonBlockHeight >= transactionBlockHeight) ) {
                        transactionBlockHeight = jsonBlockHeight;
                    }
                }
            }

            final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
            final ScriptInflater scriptInflater = new ScriptInflater();
            final Json transactionOutputJson = transactionOutputsJson.get(transactionOutputIndex);
            final Json lockingScriptJson = transactionOutputJson.get("lockingScript");
            final LockingScript lockingScript = LockingScript.castFrom(
                scriptInflater.fromBytes(
                    ByteArray.fromHexString(lockingScriptJson.getString("bytes"))
                )
            );
            final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);

            amount = transactionOutputJson.getLong("amount");
            blockHeight = transactionBlockHeight;
            address = scriptPatternMatcher.extractAddress(scriptType, lockingScript);
            scriptHash = ScriptBuilder.computeScriptHash(lockingScript);
        }

        final Json json = new ElectrumJson(false);
        json.put("id", id);

        final Json resultJson = new Json();
        resultJson.put("confirmed_height", blockHeight);
        resultJson.put("scripthash", scriptHash);
        resultJson.put("address", (address != null ? address.toBase32CheckEncoded() : null));
        resultJson.put("value", amount);

        json.put("result", resultJson);

        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handleGetMempoolFeesMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);

        final Json feeHistogramJson = new ElectrumJson(true);
        // TODO

        final Json json = new ElectrumJson(false);
        json.put("id", id);
        json.put("result", feeHistogramJson);
        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected Sha256Hash _getCachedAddressStatus(final AddressSubscriptionKey addressKey) {
        synchronized (_connectionAddresses) {
            final LinkedList<ConnectionAddress> connectionAddresses = _connectionAddresses.get(addressKey);
            if (connectionAddresses == null) { return null; }

            for (final ConnectionAddress connectionAddress : connectionAddresses) {
                final Sha256Hash status = connectionAddress.status;
                if (status == null) { continue; }

                return status;
            }

            return null;
        }
    }

    protected Sha256Hash _calculateAddressStatus(final AddressSubscriptionKey addressKey) {
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            final MutableList<TransactionPosition> transactionPositions;
            {
                final Json addressTransaction = nodeConnection.getAddressTransactionHashes(addressKey.scriptHash);
                final Json transactionsArray = addressTransaction.get("transactions");
                final Integer transactionCount = transactionsArray.length();
                if (transactionCount < 1) { return null; }

                transactionPositions = new MutableList<>(transactionCount);
                for (int i = 0; i < transactionCount; ++i) {
                    final Sha256Hash transactionHash;
                    {
                        final String transactionHashString = transactionsArray.getString(i);
                        transactionHash = Sha256Hash.fromHexString(transactionHashString);
                    }

                    final Json transactionBlockHeightJson = _getTransactionBlockHeight(transactionHash, nodeConnection);
                    final Long blockHeight = transactionBlockHeightJson.getOrNull("blockHeight", Json.Types.LONG);
                    final Integer transactionIndex = transactionBlockHeightJson.getOrNull("transactionIndex", Json.Types.INTEGER);
                    final Boolean hasUnconfirmedInputs = transactionBlockHeightJson.getBoolean("hasUnconfirmedInputs");

                    final TransactionPosition transactionPosition = new TransactionPosition(blockHeight, transactionIndex, hasUnconfirmedInputs, transactionHash);
                    transactionPositions.add(transactionPosition);
                }
            }
            transactionPositions.sort(TransactionPosition.COMPARATOR);

            try {
                final MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
                for (final TransactionPosition transactionPosition : transactionPositions) {
                    final String statusString = transactionPosition.toString();
                    messageDigest.update(StringUtil.stringToBytes(statusString));
                }
                return Sha256Hash.wrap(messageDigest.digest());
            }
            catch (final Exception exception) {
                Logger.debug(exception);
                return null;
            }
        }
    }

    protected void _notifyScriptHashStatus(final JsonSocket jsonSocket, final AddressSubscriptionKey addressKey, final Sha256Hash addressStatus) {
        final String addressString = addressKey.subscriptionString;

        final Json responseJson = new ElectrumJson(true);
        responseJson.add(addressString);
        responseJson.add(addressStatus);

        final Json json = new ElectrumJson(false);
        json.put("method", "blockchain.scripthash.subscribe");
        json.put("params", responseJson);
        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _notifyAddressStatus(final JsonSocket jsonSocket, final AddressSubscriptionKey addressKey) {
        final Sha256Hash addressStatus = _calculateAddressStatus(addressKey);
        _notifyAddressStatus(jsonSocket, addressKey, addressStatus);
    }

    protected void _notifyAddressStatus(final JsonSocket jsonSocket, final AddressSubscriptionKey addressKey, final Sha256Hash addressStatus) {
        final String addressString = addressKey.subscriptionString;

        final Json responseJson = new ElectrumJson(true);
        responseJson.add(addressString);
        responseJson.add(addressStatus);

        final Json json = new ElectrumJson(false);
        json.put("method", "blockchain.address.subscribe");
        json.put("params", responseJson);
        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _addAddressSubscription(final AddressSubscriptionKey addressKey, final JsonSocket jsonSocket, final Sha256Hash addressStatus) {
        synchronized (_connectionAddresses) {
            final LinkedList<ConnectionAddress> connectionAddresses;
            if (_connectionAddresses.containsKey(addressKey)) {
                connectionAddresses = _connectionAddresses.get(addressKey);
            }
            else {
                connectionAddresses = new LinkedList<>();
                _connectionAddresses.put(addressKey, connectionAddresses);
            }

            { // Add the JsonSocket as a WeakReference if the set does not contain it...
                boolean socketExists = false;
                for (final ConnectionAddress connectionAddress : connectionAddresses) {
                    if (addressStatus != null && (! Util.areEqual(connectionAddress.status, addressStatus))) {
                        connectionAddress.status = addressStatus;
                        Logger.debug("Updated Status: " + connectionAddress);
                    }

                    if (Util.areEqual(jsonSocket, connectionAddress.connection.get())) {
                        socketExists = true;
                    }
                }

                if (! socketExists) {
                    final ConnectionAddress connectionAddress = new ConnectionAddress(addressKey, jsonSocket);
                    connectionAddress.status = addressStatus;

                    connectionAddresses.add(connectionAddress);
                }
            }
        }
    }

    protected Boolean _removeAddressSubscription(final AddressSubscriptionKey addressKey, final JsonSocket jsonSocket) {
        synchronized (_connectionAddresses) {
            final LinkedList<ConnectionAddress> connectionAddresses = _connectionAddresses.get(addressKey);
            if (connectionAddresses == null) { return false; }

            final Iterator<ConnectionAddress> iterator = connectionAddresses.iterator();
            while (iterator.hasNext()) {
                final ConnectionAddress connectionAddress = iterator.next();
                if (Util.areEqual(jsonSocket, connectionAddress.connection.get())) {
                    iterator.remove();
                    return true;
                }
            }

            return false;
        }
    }

    protected void _handleSubscribeScriptHashMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);
        final Json paramsJson = message.get("params");

        final String addressHashString = paramsJson.getString(0);
        final Sha256Hash scriptHash = Sha256Hash.fromHexString(BitcoinUtil.reverseEndianString(addressHashString));
        if (scriptHash == null) {
            final String errorMessage = "Invalid Address hash: " + addressHashString;
            final Json json = ElectrumModule.createErrorJson(id, errorMessage, null);
            jsonSocket.write(new ElectrumJsonProtocolMessage(json));
            jsonSocket.flush();

            _debugWriteMessage(jsonSocket, json);
            return;
        }

        final AddressSubscriptionKey addressKey = new AddressSubscriptionKey(scriptHash, addressHashString);
        final Sha256Hash addressStatus;
        {
            final Sha256Hash cachedStatus = _getCachedAddressStatus(addressKey);
            if (cachedStatus != null) {
                addressStatus = cachedStatus;
            }
            else {
                addressStatus = _calculateAddressStatus(addressKey);
            }
        }

        _addAddressSubscription(addressKey, jsonSocket, addressStatus);

        final Json json = new ElectrumJson(false);
        json.put("id", id);
        json.put("result", addressStatus);

        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handleUnsubscribeScriptHashMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);
        final Json paramsJson = message.get("params");

        final String addressHashString = paramsJson.getString(0);
        final Sha256Hash scriptHash = Sha256Hash.fromHexString(BitcoinUtil.reverseEndianString(addressHashString));
        if (scriptHash == null) {
            final String errorMessage = "Invalid Address hash: " + addressHashString;
            final Json json = ElectrumModule.createErrorJson(id, errorMessage, null);
            jsonSocket.write(new ElectrumJsonProtocolMessage(json));
            jsonSocket.flush();

            _debugWriteMessage(jsonSocket, json);
            return;
        }

        final AddressSubscriptionKey addressKey = new AddressSubscriptionKey(scriptHash, addressHashString);
        final Boolean addressExisted = _removeAddressSubscription(addressKey, jsonSocket);

        {
            final Json json = new ElectrumJson(false);
            json.put("id", id);
            json.put("result", addressExisted);

            jsonSocket.write(new ElectrumJsonProtocolMessage(json));
            jsonSocket.flush();

            _debugWriteMessage(jsonSocket, json);
        }
    }

    protected void _handleSubscribeAddressMessage(final JsonSocket jsonSocket, final Json message) {
        final AddressInflater addressInflater = new AddressInflater();

        final Object id = ElectrumModule.getRequestId(message);
        final Json paramsJson = message.get("params");

        final String addressString = paramsJson.getString(0);
        final Address address = Util.coalesce(addressInflater.fromBase32Check(addressString), addressInflater.fromBase58Check(addressString));
        if (address == null) {
            final String errorMessage = "Invalid address: " + addressString;
            final Json json = ElectrumModule.createErrorJson(id, errorMessage, null);
            jsonSocket.write(new ElectrumJsonProtocolMessage(json));
            jsonSocket.flush();

            _debugWriteMessage(jsonSocket, json);
            return;
        }

        final AddressSubscriptionKey addressKey = new AddressSubscriptionKey(address, addressString);
        final Sha256Hash addressStatus;
        {
            final Sha256Hash cachedStatus = _getCachedAddressStatus(addressKey);
            if (cachedStatus != null) {
                addressStatus = cachedStatus;
            }
            else {
                addressStatus = _calculateAddressStatus(addressKey);
            }
        }

        _addAddressSubscription(addressKey, jsonSocket, addressStatus);

        {
            final Json json = new ElectrumJson(false);
            json.put("id", id);
            json.put("result", addressStatus);

            jsonSocket.write(new ElectrumJsonProtocolMessage(json));
            jsonSocket.flush();

            _debugWriteMessage(jsonSocket, json);
        }
    }

    protected void _handleGetBalanceMessage(final JsonSocket jsonSocket, final AddressSubscriptionKey addressKey, final Object requestId) {
        final Long confirmedBalance;
        final Long unconfirmedBalance;
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            final Json balanceJson = nodeConnection.getAddressBalance(addressKey.scriptHash);
            unconfirmedBalance = balanceJson.getLong("balance");
            confirmedBalance = balanceJson.getLong("confirmedBalance");
        }

        final Json balanceJson = new ElectrumJson(false);
        balanceJson.put("confirmed", confirmedBalance);
        balanceJson.put("unconfirmed", unconfirmedBalance);

        final Json json = new ElectrumJson(false);
        json.put("id", requestId);
        json.put("result", balanceJson);

        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handleGetScriptHashBalanceMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);
        final Json paramsJson = message.get("params");

        final String addressHashString = paramsJson.getString(0);
        final Sha256Hash scriptHash = Sha256Hash.fromHexString(BitcoinUtil.reverseEndianString(addressHashString));
        if (scriptHash == null) {
            final String errorMessage = "Invalid Address hash: " + addressHashString;
            final Json json = ElectrumModule.createErrorJson(id, errorMessage, null);
            jsonSocket.write(new ElectrumJsonProtocolMessage(json));
            jsonSocket.flush();

            _debugWriteMessage(jsonSocket, json);
            return;
        }

        final AddressSubscriptionKey addressKey = new AddressSubscriptionKey(scriptHash, addressHashString);
        _handleGetBalanceMessage(jsonSocket, addressKey, id);
    }

    protected void _handleGetAddressBalanceMessage(final JsonSocket jsonSocket, final Json message) {
        final AddressInflater addressInflater = new AddressInflater();

        final Object id = ElectrumModule.getRequestId(message);
        final Json paramsJson = message.get("params");

        final String addressString = paramsJson.getString(0);
        final Address address = Util.coalesce(addressInflater.fromBase32Check(addressString), addressInflater.fromBase58Check(addressString));
        if (address == null) {
            final String errorMessage = "Invalid address: " + addressString;
            final Json json = ElectrumModule.createErrorJson(id, errorMessage, null);
            jsonSocket.write(new ElectrumJsonProtocolMessage(json));
            jsonSocket.flush();

            _debugWriteMessage(jsonSocket, json);
            return;
        }

        final AddressSubscriptionKey addressKey = new AddressSubscriptionKey(address, addressString);
        _handleGetBalanceMessage(jsonSocket, addressKey, id);
    }

    protected void _handleUnsubscribeAddressMessage(final JsonSocket jsonSocket, final Json message) {
        final AddressInflater addressInflater = new AddressInflater();

        final Object id = ElectrumModule.getRequestId(message);
        final Json paramsJson = message.get("params");

        final String addressString = paramsJson.getString(0);
        final Address address = Util.coalesce(addressInflater.fromBase32Check(addressString), addressInflater.fromBase58Check(addressString));
        if (address == null) {
            final String errorMessage = "Invalid address: " + addressString;
            final Json json = ElectrumModule.createErrorJson(id, errorMessage, null);
            jsonSocket.write(new ElectrumJsonProtocolMessage(json));
            jsonSocket.flush();

            _debugWriteMessage(jsonSocket, json);
            return;
        }

        final AddressSubscriptionKey addressKey = new AddressSubscriptionKey(address, addressString);
        final Boolean addressExisted = _removeAddressSubscription(addressKey, jsonSocket);

        {
            final Json json = new ElectrumJson(false);
            json.put("id", id);
            json.put("result", addressExisted);

            jsonSocket.write(new ElectrumJsonProtocolMessage(json));
            jsonSocket.flush();

            _debugWriteMessage(jsonSocket, json);
            jsonSocket.flush();
        }
    }

    protected void _handleGetAddressHistory(final JsonSocket jsonSocket, final Json message, final Boolean includeConfirmedTransactions, final Boolean includeUnconfirmedTransactions, final Boolean includeTransactionFees) {
        final AddressInflater addressInflater = new AddressInflater();

        final Object id = ElectrumModule.getRequestId(message);
        final Json paramsJson = message.get("params");

        final Sha256Hash scriptHash;
        final Address address;
        {
            final String addressString = paramsJson.getString(0);
            scriptHash = Sha256Hash.fromHexString(BitcoinUtil.reverseEndianString(addressString));
            address = Util.coalesce(addressInflater.fromBase32Check(addressString), addressInflater.fromBase58Check(addressString));
            if ((scriptHash == null) && (address == null)) {
                final String errorMessage = "Invalid Address/Hash: " + addressString;
                final Json json = ElectrumModule.createErrorJson(id, errorMessage, null);
                jsonSocket.write(new ElectrumJsonProtocolMessage(json));
                jsonSocket.flush();

                _debugWriteMessage(jsonSocket, json);
                return;
            }
        }

        final Json resultJson = new ElectrumJson(true);
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            final Json addressTransactionsJson;
            if (address != null) {
                addressTransactionsJson = nodeConnection.getAddressTransactionHashes(address);
            }
            else {
                addressTransactionsJson = nodeConnection.getAddressTransactionHashes(scriptHash);
            }

            final Json transactionsJson = addressTransactionsJson.get("transactions");
            final int transactionCount = transactionsJson.length();
            final MutableList<TransactionPosition> transactionPositions = new MutableList<>(transactionCount);
            for (int i = 0; i < transactionCount; ++i) {
                final String transactionHashString = transactionsJson.getString(i);
                final Sha256Hash transactionHash = Sha256Hash.fromHexString(transactionHashString);

                final Json transactionBlockHeightJson = _getTransactionBlockHeight(transactionHash, nodeConnection);
                final Long blockHeight = transactionBlockHeightJson.getOrNull("blockHeight", Json.Types.LONG);
                final Integer transactionIndex = transactionBlockHeightJson.getOrNull("transactionIndex", Json.Types.INTEGER);
                final Boolean hasUnconfirmedInputs = transactionBlockHeightJson.getBoolean("hasUnconfirmedInputs");

                final TransactionPosition transactionPosition = new TransactionPosition(blockHeight, transactionIndex, hasUnconfirmedInputs, transactionHash);

                if ( transactionPosition.isUnconfirmedTransaction() && (! includeUnconfirmedTransactions) ) { continue; }
                if ( (! transactionPosition.isUnconfirmedTransaction()) && (! includeConfirmedTransactions) ) { continue; }

                transactionPositions.add(transactionPosition);
            }
            transactionPositions.sort(TransactionPosition.COMPARATOR);

            if (includeTransactionFees) {
                for (final TransactionPosition transactionPosition : transactionPositions) {
                    final Json getTransactionJson = nodeConnection.getTransaction(transactionPosition.transactionHash, false);
                    final Json transactionJson = getTransactionJson.get("transaction");
                    transactionPosition.transactionFee = transactionJson.getLong("fee");
                }
            }

            for (final TransactionPosition transactionPosition : transactionPositions) {
                resultJson.add(transactionPosition);
            }
        }

        final Json json = new ElectrumJson(false);
        json.put("id", id);
        json.put("result", resultJson);

        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handleGetUnspentOutputs(final JsonSocket jsonSocket, final Json message) {
        final AddressInflater addressInflater = new AddressInflater();

        final Object id = ElectrumModule.getRequestId(message);
        final Json paramsJson = message.get("params");

        final Sha256Hash scriptHash;
        final Address address;
        {
            final String addressString = paramsJson.getString(0);
            address = Util.coalesce(addressInflater.fromBase32Check(addressString), addressInflater.fromBase58Check(addressString));
            if (address != null) {
                scriptHash = ScriptBuilder.computeScriptHash(address);
            }
            else {
                scriptHash = Sha256Hash.fromHexString(BitcoinUtil.reverseEndianString(addressString));
                if (scriptHash == null) {
                    final String errorMessage = "Invalid Address/Hash: " + addressString;
                    final Json json = ElectrumModule.createErrorJson(id, errorMessage, null);
                    jsonSocket.write(new ElectrumJsonProtocolMessage(json));
                    jsonSocket.flush();

                    _debugWriteMessage(jsonSocket, json);
                    return;
                }
            }
        }

        final Json resultJson = new ElectrumJson(true);
        try (final NodeJsonRpcConnection nodeConnection = _getNodeConnection()) {
            final Json addressTransactionsJson;
            if (address != null) {
                addressTransactionsJson = nodeConnection.getAddressTransactions(address, false);
            }
            else {
                addressTransactionsJson = nodeConnection.getAddressTransactions(scriptHash, false);
            }

            final Json transactionsJson = addressTransactionsJson.get("transactions");
            final int transactionCount = transactionsJson.length();
            final MutableList<TransactionPosition> transactionPositions = new MutableList<>(transactionCount);
            final HashMap<TransactionPosition, MutableList<Json>> transactionPositionJsons = new HashMap<>();
            for (int i = 0; i < transactionCount; ++i) {
                final Json transactionJson = transactionsJson.get(i);
                final Sha256Hash transactionHash = Sha256Hash.fromHexString(transactionJson.getString("hash"));

                final Json transactionBlockHeightJson = _getTransactionBlockHeight(transactionHash, nodeConnection);
                final Long blockHeight = transactionBlockHeightJson.getOrNull("blockHeight", Json.Types.LONG);
                final Integer transactionIndex = transactionBlockHeightJson.getOrNull("transactionIndex", Json.Types.INTEGER);
                final Boolean hasUnconfirmedInputs = transactionBlockHeightJson.getBoolean("hasUnconfirmedInputs");

                final TransactionPosition transactionPosition = new TransactionPosition(blockHeight, transactionIndex, hasUnconfirmedInputs, transactionHash);
                transactionPositions.add(transactionPosition);

                final Json transactionOutputsJson = transactionJson.get("outputs");
                final int transactionOutputCount = transactionOutputsJson.length();
                for (int outputIndex = 0; outputIndex < transactionOutputCount; ++outputIndex) {
                    final Json transactionOutputJson = transactionOutputsJson.get(outputIndex);
                    final Long amount = transactionOutputJson.getLong("amount");
                    final Sha256Hash spentByTransaction = Sha256Hash.fromHexString(transactionOutputJson.getOrNull("spentByTransaction", Json.Types.STRING));
                    final Sha256Hash transactionOutputScriptHash = Sha256Hash.fromHexString(transactionOutputJson.getOrNull("scriptHash", Json.Types.STRING));

                    if (! Util.areEqual(scriptHash, transactionOutputScriptHash)) { continue; }

                    final boolean isUnspent = (spentByTransaction == null);
                    if (! isUnspent) { continue; }

                    final Json json = transactionPosition.toJson();
                    json.put("tx_pos", outputIndex);
                    json.put("value", amount);

                    MutableList<Json> jsonList = transactionPositionJsons.get(transactionPosition);
                    if (jsonList == null) {
                        jsonList = new MutableList<>();
                        transactionPositionJsons.put(transactionPosition, jsonList);
                    }

                    jsonList.add(json);
                }
            }
            transactionPositions.sort(TransactionPosition.COMPARATOR);

            // Add the TransactionOutput Json objects in sorted order by their associated TransactionPosition...
            for (final TransactionPosition transactionPosition : transactionPositions) {
                final List<Json> jsonList = transactionPositionJsons.get(transactionPosition);
                if (jsonList == null) { continue; }

                for (final Json transactionPositionJson : jsonList) {
                    resultJson.add(transactionPositionJson);
                }
            }
        }

        final Json json = new ElectrumJson(false);
        json.put("id", id);
        json.put("result", resultJson);

        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handlePeersMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);

        final Json peerListJson = new ElectrumJson(true);
        // P2P unsupported.

        final Json json = new ElectrumJson(false);
        json.put("id", id);
        json.put("result", peerListJson);

        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handleAddPeerMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);

        final Json json = new ElectrumJson(true);
        json.put("id", id);
        json.put("result", true); // P2P unsupported.

        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _handlePeerFeaturesMessage(final JsonSocket jsonSocket, final Json message) {
        final Object id = ElectrumModule.getRequestId(message);

        final Json resultJson = _createFeaturesJson();

        final Json json = new ElectrumJson(false);
        json.put("id", id);
        json.put("result", resultJson);

        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected void _sendAddPeerMessage(final JsonSocket jsonSocket) {
        final Json paramsJson;
        {
            final Json featuresJson = _createFeaturesJson();

            paramsJson = new ElectrumJson(true);
            paramsJson.add(featuresJson);
        }

        final Json json = new ElectrumJson(false);
        json.put("method", "server.add_peer");
        json.put("params", paramsJson);

        jsonSocket.write(new ElectrumJsonProtocolMessage(json));
        jsonSocket.flush();

        _debugWriteMessage(jsonSocket, json);
    }

    protected final CachedThreadPool _requestBooster = new CachedThreadPool(10, 30000L);
    protected void _handleRequest(final Json json, final JsonSocket jsonSocket) {
        final Integer idleThreadCount = _requestBooster.getIdleThreadCount();
        if (idleThreadCount > 0) {
            _requestBooster.execute(new Runnable() {
                @Override
                public void run() {
                    if (! jsonSocket.isConnected()) { return; }

                    final NanoTimer nanoTimer = new NanoTimer();
                    nanoTimer.start();

                    _handleMessage(json, jsonSocket);

                    nanoTimer.stop();
                    Logger.trace("Executed boosted request in: " + nanoTimer.getMillisecondsElapsed() + "ms.");
                }
            });
        }
        else {
            if (! jsonSocket.isConnected()) { return; }

            final NanoTimer nanoTimer = new NanoTimer();
            nanoTimer.start();

            _handleMessage(json, jsonSocket);

            nanoTimer.stop();
            Logger.trace("Executed request in: " + nanoTimer.getMillisecondsElapsed() + "ms.");
        }
    }

    protected void _handleMessage(final Json jsonMessage, final JsonSocket jsonSocket) {
        if (! jsonSocket.isConnected()) { return; }

        _debugReceivedMessage(jsonSocket, jsonMessage);

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
            case "blockchain.estimatefee": {
                _handleEstimateFeeMessage(jsonSocket, jsonMessage);
            } break;
            case "blockchain.headers.subscribe": {
                _handleSubscribeBlockHeadersMessage(jsonSocket, jsonMessage);
            } break;
            case "server.peers.subscribe": {
                _handlePeersMessage(jsonSocket, jsonMessage);
            } break;
            case "server.add_peer": {
                _handleAddPeerMessage(jsonSocket, jsonMessage);
            } break;
            case "server.features": {
                _handlePeerFeaturesMessage(jsonSocket, jsonMessage);
            } break;
            case "blockchain.scripthash.subscribe": {
                _handleSubscribeScriptHashMessage(jsonSocket, jsonMessage);
            } break;
            case "blockchain.address.subscribe": {
                _handleSubscribeAddressMessage(jsonSocket, jsonMessage);
            } break;
            case "blockchain.scripthash.unsubscribe": {
                _handleUnsubscribeScriptHashMessage(jsonSocket, jsonMessage);
            } break;
            case "blockchain.scripthash.get_balance": {
                _handleGetScriptHashBalanceMessage(jsonSocket, jsonMessage);
            } break;
            case "blockchain.address.get_balance": {
                _handleGetAddressBalanceMessage(jsonSocket, jsonMessage);
            } break;
            case "blockchain.address.unsubscribe": {
                _handleUnsubscribeAddressMessage(jsonSocket, jsonMessage);
            } break;
            case "blockchain.address.get_history":
            case "blockchain.scripthash.get_history": {
                _handleGetAddressHistory(jsonSocket, jsonMessage, true, true, false);
            } break;
            case "blockchain.address.get_mempool":
            case "blockchain.scripthash.get_mempool": {
                _handleGetAddressHistory(jsonSocket, jsonMessage, false, true, true);
            } break;
            case "blockchain.address.listunspent":
            case "blockchain.scripthash.listunspent": {
                _handleGetUnspentOutputs(jsonSocket, jsonMessage);
            } break;
            case "blockchain.transaction.broadcast": {
                _handleSubmitTransactionMessage(jsonSocket, jsonMessage);
            } break;
            case "blockchain.block.headers": {
                _handleBlockHeadersMessage(jsonSocket, jsonMessage);
            } break;
            case "blockchain.block.header": {
                _handleBlockHeaderMessage(jsonSocket, jsonMessage);
            } break;
            case "blockchain.transaction.get": {
                _handleGetTransactionMessage(jsonSocket, jsonMessage);
            } break;
            case "blockchain.transaction.get_merkle": {
                _handleGetTransactionMerkleProofMessage(jsonSocket, jsonMessage);
            } break;
            case "blockchain.address.get_scripthash": {
                _handleConvertAddressToScriptHashMessage(jsonSocket, jsonMessage);
            } break;
            case "blockchain.transaction.id_from_pos": {
                _handleGetTransactionFromBlockPositionMessage(jsonSocket, jsonMessage);
            } break;
            case "blockchain.utxo.get_info": {
                _handleGetUtxoInfoMessage(jsonSocket, jsonMessage);
            } break;
            case "mempool.get_fee_histogram": {
                _handleGetMempoolFeesMessage(jsonSocket, jsonMessage);
            } break;
            default: {
                Logger.debug("Received unsupported message: " + jsonMessage);
            }
        }
    }

    protected void _onConnect(final JsonSocket jsonSocket) {
        Logger.info("Electrum Socket Connected: " + jsonSocket);
        final Long socketId = jsonSocket.getId();

        final Ip ip = jsonSocket.getIp();
        if (_bannedConnections.contains(ip)) {
            Logger.debug("Refusing connection to banned socket: " + jsonSocket);
            jsonSocket.close();
            return;
        }

        _connections.put(socketId, jsonSocket);
        final WorkerThread workerThread;
        synchronized (_workerThreads) {
            if (_workerThreads.containsKey(ip)) {
                workerThread = _workerThreads.get(ip);
            }
            else {
                workerThread = new WorkerThread(new WorkerThread.RequestHandler() {
                    @Override
                    public void handleRequest(final Json json, final JsonSocket jsonSocket) {
                        _handleRequest(json, jsonSocket);
                    }

                    @Override
                    public void handleError(final Json json, final JsonSocket jsonSocket) {
                        final Object requestId = ElectrumModule.getRequestId(json);

                        final Json errorJson = ElectrumModule.createErrorJson(requestId, "Internal error.", null);
                        jsonSocket.write(new ElectrumJsonProtocolMessage(errorJson));

                        _debugWriteMessage(jsonSocket, errorJson);
                    }
                });
                workerThread.start();
                _workerThreads.put(ip, workerThread);
            }

            workerThread.addSocket(jsonSocket);
        }

        jsonSocket.setMessageReceivedCallback(new Runnable() {
            @Override
            public void run() {
                final JsonProtocolMessage jsonProtocolMessage = jsonSocket.popMessage();
                if (jsonProtocolMessage == null) { return; }

                final Json jsonMessage = jsonProtocolMessage.getMessage();
                workerThread.enqueue(jsonMessage, jsonSocket);
            }
        });

        jsonSocket.setOnClosedCallback(new Runnable() {
            @Override
            public void run() {
                _onDisconnect(jsonSocket);
            }
        });

        jsonSocket.beginListening();
    }

    protected void _onDisconnect(final JsonSocket jsonSocket) {
        final Long socketId = jsonSocket.getId();
        final Ip ip = jsonSocket.getIp();
        Logger.info("Electrum Socket Disconnected: " + jsonSocket);
        _connections.remove(socketId);

        synchronized (_workerThreads) {
            final WorkerThread workerThread = _workerThreads.get(ip);
            if (workerThread != null) {
                workerThread.removeSocket(jsonSocket);
                if (! workerThread.hasActiveSockets()) {
                    _workerThreads.remove(ip);
                    workerThread.interrupt();
                }
            }
        }
    }

    public ElectrumModule(final ElectrumProperties electrumProperties) {
        _electrumProperties = electrumProperties;
        _threadPool = new CachedThreadPool(1024, 30000L);

        {
            final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
            _blockHeaderCacheWriteLock = readWriteLock.writeLock();
            _blockHeaderCacheReadLock = readWriteLock.readLock();
        }

        _minTransactionFeePerByte = 1L;

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

        _maintenanceThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int iterationsSinceAddressCleanup = 0;
                final Thread thread = Thread.currentThread();
                while (! thread.isInterrupted()) {
                    try {
                        Thread.sleep(15000L);
                    }
                    catch (final InterruptedException exception) { return; }

                    iterationsSinceAddressCleanup += 1;

                    if (iterationsSinceAddressCleanup >= 20) {
                        synchronized (_connectionAddresses) {
                            final HashSet<AddressSubscriptionKey> connectionsToRemove = new HashSet<>();
                            for (final Map.Entry<AddressSubscriptionKey, LinkedList<ConnectionAddress>> entry : _connectionAddresses.entrySet()) {
                                boolean queueHasActiveSocket = false;

                                final AddressSubscriptionKey addressSubscriptionKey = entry.getKey();
                                final LinkedList<ConnectionAddress> connectionAddresses = entry.getValue();
                                final Iterator<ConnectionAddress> iterator = connectionAddresses.iterator();
                                while (iterator.hasNext()) {
                                    final ConnectionAddress connectionAddress = iterator.next();

                                    final JsonSocket jsonSocket = connectionAddress.connection.get();
                                    final boolean jsonSocketIsConnected = ( (jsonSocket != null) && jsonSocket.isConnected() );

                                    if (! jsonSocketIsConnected) {
                                        iterator.remove();
                                    }
                                    else {
                                        queueHasActiveSocket = true;
                                    }
                                }

                                if (! queueHasActiveSocket) {
                                    connectionsToRemove.add(addressSubscriptionKey);
                                }
                            }

                            for (final AddressSubscriptionKey addressSubscriptionKey : connectionsToRemove) {
                                _connectionAddresses.remove(addressSubscriptionKey);
                            }
                        }

                        iterationsSinceAddressCleanup = 0;
                    }

                    if ( (_nodeNotificationConnection == null) || (! _nodeNotificationConnection.isConnected()) ) {
                        _createNodeNotificationConnection();
                    }

                    while (true) { // Attempt to serve a cached connection...
                        final CachedNodeJsonRpcConnection cachedConnection = _cachedConnections.pollFirst();
                        if (cachedConnection == null) { break; }
                        _cachedConnectionCount.getAndDecrement();

                        if (! cachedConnection.isConnected()) {
                            cachedConnection.superClose();
                            continue;
                        }

                        if (cachedConnection.getConnectionDuration() > _maxCachedConnectionLifespan) {
                            cachedConnection.superClose();
                            continue;
                        }

                        if (_cachedConnectionCount.get() < _maxCachedConnectionCount) {
                            _cachedConnections.addLast(cachedConnection);
                            _cachedConnectionCount.getAndDecrement();
                        }
                    }
                }
            }
        });
        _maintenanceThread.setName("Electrum Maintenance Thread");
        _maintenanceThread.setDaemon(true);
    }

    public void loop() {
        final Thread mainThread = Thread.currentThread();

        final File dataDirectory = _electrumProperties.getDataDirectory();
        if (! dataDirectory.exists()) {
            final boolean dataDirectoryCreated = dataDirectory.mkdirs();
            if (! dataDirectoryCreated) {
                Logger.warn("Unable to create data directory: " + dataDirectory);
            }
        }

        _threadPool.start();
        _requestBooster.start();

        _cacheBlockHeaders();
        _createNodeNotificationConnection();

        _electrumServerSocket.start();

        final Integer port = _electrumProperties.getHttpPort();
        Logger.info("Listening on port: " + port);
        if (_tlsIsEnabled) {
            final Integer tlsPort = _electrumProperties.getTlsPort();
            Logger.info("Listening on port: " + tlsPort);
        }

        _maintenanceThread.start();

        while (! mainThread.isInterrupted()) {
            try { Thread.sleep(10000L); } catch (final Exception exception) { }
        }

        _electrumServerSocket.stop();

        for (final JsonSocket socket : _connections.values()) {
            socket.close();
        }
        _connections.clear();

        _maintenanceThread.interrupt();
        try {
            _maintenanceThread.join(30000L);
        }
        catch (final Exception exception) { }

        if (_nodeNotificationConnection != null) {
            _nodeNotificationConnection.close();
            _nodeNotificationConnection = null;
        }

        _threadPool.stop();
        _requestBooster.stop();
    }
}
