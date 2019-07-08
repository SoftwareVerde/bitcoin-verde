package com.softwareverde.bitcoin.server.module.node.rpc;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.util.HexUtil;

public class NodeJsonRpcConnection implements AutoCloseable {
    public interface AnnouncementHookCallback {
        void onNewBlockHeader(Json blockHeaderJson);
        void onNewTransaction(Json transactionJson);
    }

    public interface RawAnnouncementHookCallback {
        void onNewBlockHeader(BlockHeader blockHeader);
        void onNewTransaction(Transaction transaction, Long fee);
    }

    public static class InflaterManager {
        public final BlockHeaderInflater blockHeaderInflater;
        public final BlockDeflater blockDeflater;
        public final TransactionInflater transactionInflater;
        public final TransactionDeflater transactionDeflater;

        public InflaterManager(final BlockHeaderInflater blockHeaderInflater, final BlockDeflater blockDeflater, final TransactionInflater transactionInflater, final TransactionDeflater transactionDeflater) {
            this.blockHeaderInflater = blockHeaderInflater;
            this.blockDeflater = blockDeflater;
            this.transactionInflater = transactionInflater;
            this.transactionDeflater = transactionDeflater;
        }
    }

    public static final Long RPC_DURATION_TIMEOUT_MS = 30000L;

    protected final BlockHeaderInflater _blockHeaderInflater;
    protected final BlockDeflater _blockDeflater;
    protected final TransactionInflater _transactionInflater;
    protected final TransactionDeflater _transactionDeflater;

    protected final JsonSocket _jsonSocket;
    protected final Object _newMessageNotifier = new Object();

    protected final Runnable _onNewMessageCallback = new Runnable() {
        @Override
        public void run() {
            synchronized (_newMessageNotifier) {
                _newMessageNotifier.notifyAll();
            }
        }
    };

    protected Boolean _isUpgradedToHook = false;

    protected Json _executeJsonRequest(final Json rpcRequestJson) {
        if (_isUpgradedToHook) { throw new RuntimeException("Attempted to invoke Json request to hook-upgraded socket."); }

        _jsonSocket.write(new JsonProtocolMessage(rpcRequestJson));
        _jsonSocket.beginListening();

        JsonProtocolMessage jsonProtocolMessage;
        synchronized (_newMessageNotifier) {
            jsonProtocolMessage = _jsonSocket.popMessage();
            if (jsonProtocolMessage == null) {
                try {
                    _newMessageNotifier.wait(RPC_DURATION_TIMEOUT_MS);
                }
                catch (final InterruptedException exception) { }

                jsonProtocolMessage = _jsonSocket.popMessage();
            }
        }

        return (jsonProtocolMessage != null ? jsonProtocolMessage.getMessage() : null);
    }

    protected Json _createRegisterHookRpcJson(final Boolean returnRawData, final Boolean includeTransactionFees) {
        final Json eventTypesJson = new Json(true);
        eventTypesJson.add("NEW_BLOCK");
        eventTypesJson.add("NEW_TRANSACTION");

        final Json parametersJson = new Json();
        parametersJson.put("events", eventTypesJson);
        parametersJson.put("rawFormat", (returnRawData ? 1 : 0));
        parametersJson.put("includeTransactionFees", (includeTransactionFees ? 1 : 0));

        final Json registerHookRpcJson = new Json();
        registerHookRpcJson.put("method", "POST");
        registerHookRpcJson.put("query", "ADD_HOOK");
        registerHookRpcJson.put("parameters", parametersJson);

        return registerHookRpcJson;
    }

    public NodeJsonRpcConnection(final String hostname, final Integer port, final ThreadPool threadPool) {
        this(
            hostname,
            port,
            threadPool,
            new InflaterManager(
                new BlockHeaderInflater(),
                new BlockDeflater(),
                new TransactionInflater(),
                new TransactionDeflater()
            )
        );
    }

    public NodeJsonRpcConnection(final String hostname, final Integer port, final ThreadPool threadPool, final InflaterManager inflaterManager) {
        _blockHeaderInflater = inflaterManager.blockHeaderInflater;
        _blockDeflater = inflaterManager.blockDeflater;
        _transactionInflater = inflaterManager.transactionInflater;
        _transactionDeflater = inflaterManager.transactionDeflater;

        final java.net.Socket javaSocket;
        {
            java.net.Socket socket = null;
            try { socket = new java.net.Socket(hostname, port); }
            catch (final Exception exception) { Logger.log(exception); }
            javaSocket = socket;
        }

        _jsonSocket = ((javaSocket != null) ? new JsonSocket(javaSocket, threadPool) : null);

        if (_jsonSocket != null) {
            _jsonSocket.setMessageReceivedCallback(_onNewMessageCallback);
        }
    }

    public NodeJsonRpcConnection(final java.net.Socket javaSocket, final ThreadPool threadPool) {
        this(
            javaSocket,
            threadPool,
            new InflaterManager(
                new BlockHeaderInflater(),
                new BlockDeflater(),
                new TransactionInflater(),
                new TransactionDeflater()
            )
        );
    }

    public NodeJsonRpcConnection(final java.net.Socket socket, final ThreadPool threadPool, final InflaterManager inflaterManager) {
        _blockHeaderInflater = inflaterManager.blockHeaderInflater;
        _blockDeflater = inflaterManager.blockDeflater;
        _transactionInflater = inflaterManager.transactionInflater;
        _transactionDeflater = inflaterManager.transactionDeflater;

        _jsonSocket = ((socket != null) ? new JsonSocket(socket, threadPool) : null);

        if (_jsonSocket != null) {
            _jsonSocket.setMessageReceivedCallback(_onNewMessageCallback);
        }
    }

    public Json getBlockHeaders(final Long blockHeight, final Integer maxBlockCount, final Boolean returnRawFormat) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("blockHeight", blockHeight);
        rpcParametersJson.put("maxBlockCount", maxBlockCount);
        rpcParametersJson.put("rawFormat", (returnRawFormat ? 1 : 0));

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BLOCK_HEADERS");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getBlockHeaders(final Integer maxBlockCount, final Boolean returnRawFormat) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("maxBlockCount", maxBlockCount);
        rpcParametersJson.put("rawFormat", (returnRawFormat ? 1 : 0));

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BLOCK_HEADERS");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getDifficulty() {
        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "DIFFICULTY");

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getBlockReward() {
        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BLOCK_REWARD");

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getUnconfirmedTransactions(final Boolean returnRawFormat) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("rawFormat", (returnRawFormat ? 1 : 0));

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "UNCONFIRMED_TRANSACTIONS");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getBlockHeight() {
        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BLOCK_HEIGHT");

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getBlockchainMetadata() {
        final Json rpcRequestJson = new Json();
        {
            rpcRequestJson.put("method", "GET");
            rpcRequestJson.put("query", "BLOCKCHAIN");
        }

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getNodes() {
        final Json rpcRequestJson = new Json();
        {
            rpcRequestJson.put("method", "GET");
            rpcRequestJson.put("query", "NODES");
        }

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getAddressTransactions(final Address address) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("address", address.toBase58CheckEncoded());

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "ADDRESS");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getBlock(final Sha256Hash blockHash) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("hash", blockHash);

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BLOCK");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getBlock(final Long blockHeight) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("blockHeight", blockHeight);

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BLOCK");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getTransaction(final Sha256Hash transactionHash) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("hash", transactionHash);

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "TRANSACTION");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getStatus() {
        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "STATUS");

        return _executeJsonRequest(rpcRequestJson);
    }

    /**
     * Subscribes to the Node for new Block/Transaction announcements.
     *  The NodeJsonRpcConnection is consumed by this operation and cannot be used for additional API calls.
     *  The underlying JsonSocket remains connected and must be closed when announcements are no longer desired.
     */
    public Boolean upgradeToAnnouncementHook(final AnnouncementHookCallback announcementHookCallback) {
        if (announcementHookCallback == null) { throw new NullPointerException("Null AnnouncementHookCallback found."); }

        final Json registerHookRpcJson = _createRegisterHookRpcJson(false, true);

        final Json upgradeResponseJson = _executeJsonRequest(registerHookRpcJson);
        if (! upgradeResponseJson.getBoolean("wasSuccess")) { return false; }

        _jsonSocket.setMessageReceivedCallback(new Runnable() {
            @Override
            public void run() {
                final JsonProtocolMessage message = _jsonSocket.popMessage();
                final Json json = message.getMessage();

                final String objectType = json.getString("objectType");
                final Json object = json.get("object");
                switch (objectType) {
                    case "BLOCK": {
                        announcementHookCallback.onNewBlockHeader(object);
                    } break;

                    case "TRANSACTION": {
                        announcementHookCallback.onNewTransaction(object);
                    } break;

                    default: { } break;
                }
            }
        });

        _isUpgradedToHook = true;

        return true;
    }

    public Boolean upgradeToAnnouncementHook(final RawAnnouncementHookCallback announcementHookCallback) {
        if (announcementHookCallback == null) { throw new NullPointerException("Null AnnouncementHookCallback found."); }

        final Json registerHookRpcJson = _createRegisterHookRpcJson(true, true);

        final Json upgradeResponseJson = _executeJsonRequest(registerHookRpcJson);
        if (! upgradeResponseJson.getBoolean("wasSuccess")) { return false; }

        _jsonSocket.setMessageReceivedCallback(new Runnable() {
            @Override
            public void run() {
                final JsonProtocolMessage message = _jsonSocket.popMessage();
                final Json json = message.getMessage();

                final String objectType = json.getString("objectType");

                switch (objectType) {
                    case "BLOCK": {
                        final String objectData = json.getString("object");
                        final BlockHeader blockHeader = _blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray(objectData));
                        if (blockHeader == null) {
                            Logger.log("Error inflating block: " + objectData);
                            return;
                        }

                        announcementHookCallback.onNewBlockHeader(blockHeader);
                    } break;

                    case "TRANSACTION": {
                        final String objectData = json.getString("object");
                        final Transaction transaction = _transactionInflater.fromBytes(HexUtil.hexStringToByteArray(objectData));
                        if (transaction == null) {
                            Logger.log("Error inflating transaction: " + objectData);
                            return;
                        }

                        announcementHookCallback.onNewTransaction(transaction, null);
                    } break;

                    case "TRANSACTION_WITH_FEE": {
                        final Json object = json.get("object");
                        final String transactionData = object.getString("transactionData");
                        final Long fee = object.getLong("transactionFee");
                        final Transaction transaction = _transactionInflater.fromBytes(HexUtil.hexStringToByteArray(transactionData));
                        if (transaction == null) {
                            Logger.log("Error inflating transaction: " + transactionData);
                            return;
                        }

                        announcementHookCallback.onNewTransaction(transaction, fee);
                    } break;

                    default: { } break;
                }
            }
        });

        _isUpgradedToHook = true;

        return true;
    }

    public Json validatePrototypeBlock(final Block block) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("blockData", _blockDeflater.toBytes(block));

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "POST");
        rpcRequestJson.put("query", "VALIDATE_PROTOTYPE_BLOCK");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json submitTransaction(final Transaction transaction) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("transactionData", _transactionDeflater.toBytes(transaction));

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "POST");
        rpcRequestJson.put("query", "TRANSACTION");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json submitBlock(final Block block) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("blockData", _blockDeflater.toBytes(block));

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "POST");
        rpcRequestJson.put("query", "BLOCK");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public JsonSocket getJsonSocket() {
        return _jsonSocket;
    }

    @Override
    public void close() {
        _jsonSocket.close();
    }
}
