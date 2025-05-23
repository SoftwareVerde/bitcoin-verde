package com.softwareverde.bitcoin.rpc;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.address.TypedAddress;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProofInflater;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.util.timer.NanoTimer;

public class NodeJsonRpcConnection implements AutoCloseable {
    protected enum BlockHeaderDirection {
        BEFORE, AFTER
    }

    public interface AnnouncementHookCallback {
        void onNewBlockHeader(Json blockHeaderJson);
        void onNewTransaction(Json transactionJson);
        void onNewDoubleSpendProof(Json doubleSpendProofJson);
    }

    public interface RawAnnouncementHookCallback {
        void onNewBlockHeader(BlockHeader blockHeader);
        void onNewTransaction(Transaction transaction, Long fee);
        void onNewDoubleSpendProof(DoubleSpendProof doubleSpendProofJson);
    }

    public static final Long DEFAULT_RPC_DURATION_TIMEOUT_MS = 30000L;

    protected final MasterInflater _masterInflater;
    protected final JsonSocket _jsonSocket;
    protected final Object _newMessageNotifier = new Object();
    protected Long _rpcDurationTimeoutMs = NodeJsonRpcConnection.DEFAULT_RPC_DURATION_TIMEOUT_MS;

    protected final Runnable _onNewMessageCallback = new Runnable() {
        @Override
        public void run() {
            synchronized (_newMessageNotifier) {
                _newMessageNotifier.notifyAll();
            }
        }
    };

    protected Boolean _isUpgradedToHook = false;
    protected Boolean _announcementHookExpectsRawTransactionData = null;

    protected Json _executeJsonRequest(final Json rpcRequestJson) {
        if (_isUpgradedToHook) { throw new RuntimeException("Attempted to invoke Json request to a hook-upgraded socket."); }
        if ( (_jsonSocket == null) || (! _jsonSocket.isConnected()) ) { throw new RuntimeException("Attempted to invoke Json request to a closed socket."); }

        _jsonSocket.write(new JsonProtocolMessage(rpcRequestJson));
        _jsonSocket.beginListening();

        double totalWaitTimeMs = 0L;
        JsonProtocolMessage jsonProtocolMessage;
        {
            jsonProtocolMessage = _jsonSocket.popMessage();
            while ((jsonProtocolMessage == null) && (totalWaitTimeMs < _rpcDurationTimeoutMs)) {
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();
                try {
                    synchronized (_newMessageNotifier) {
                        _newMessageNotifier.wait(100L);
                    }
                }
                catch (final InterruptedException exception) { break; }

                nanoTimer.stop();
                final Double msElapsed = nanoTimer.getMillisecondsElapsed();
                totalWaitTimeMs += Math.max(1L, msElapsed.longValue());

                jsonProtocolMessage = _jsonSocket.popMessage();
            }
        }

        Logger.trace("Finished JSON request in " + totalWaitTimeMs + "ms. - " + rpcRequestJson);
        return (jsonProtocolMessage != null ? jsonProtocolMessage.getMessage() : null);
    }

    protected Json _createRegisterHookRpcJson(final Boolean returnRawData, final Boolean includeTransactionFees, final List<TypedAddress> addressFilter) {
        final Json eventTypesJson = new Json(true);
        eventTypesJson.add("NEW_BLOCK");
        eventTypesJson.add("NEW_TRANSACTION");
        eventTypesJson.add("NEW_DOUBLE_SPEND_PROOF");

        final Json parametersJson = new Json();
        parametersJson.put("events", eventTypesJson);
        parametersJson.put("rawFormat", (returnRawData ? 1 : 0));
        parametersJson.put("includeTransactionFees", (includeTransactionFees ? 1 : 0));

        if (addressFilter != null) {
            final Json addressFilterJson = new Json(true);
            for (final TypedAddress address : addressFilter) {
                final String addressString = address.toString();
                addressFilterJson.add(addressString);
            }
            parametersJson.put("addressFilter", addressFilterJson);
        }

        final Json registerHookRpcJson = new Json();
        registerHookRpcJson.put("method", "POST");
        registerHookRpcJson.put("query", "ADD_HOOK");
        registerHookRpcJson.put("parameters", parametersJson);

        return registerHookRpcJson;
    }

    protected Json _getBlock(final Sha256Hash blockHash, final Boolean hexFormat) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("hash", blockHash);
        if (hexFormat != null) {
            rpcParametersJson.put("rawFormat", (hexFormat ? 1 : 0));
        }

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BLOCK");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    protected Json _getBlock(final Long blockHeight, final Boolean hexFormat) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("blockHeight", blockHeight);
        if (hexFormat != null) {
            rpcParametersJson.put("rawFormat", (hexFormat ? 1 : 0));
        }

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BLOCK");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    protected Json _getBlockHeader(final Sha256Hash blockHash, final Boolean hexFormat) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("hash", blockHash);
        if (hexFormat != null) {
            rpcParametersJson.put("rawFormat", (hexFormat ? 1 : 0));
        }

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BLOCK_HEADER");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    protected Json _getBlockHeader(final Long blockHeight, final Boolean hexFormat) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("blockHeight", blockHeight);
        if (hexFormat != null) {
            rpcParametersJson.put("rawFormat", (hexFormat ? 1 : 0));
        }

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BLOCK_HEADER");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    protected Json _getBlockTransactions(final Sha256Hash blockHash, final Integer pageSize, final Integer pageNumber) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("hash", blockHash);
        if (pageSize != null) {
            rpcParametersJson.put("pageSize", pageSize);
        }
        if (pageNumber != null) {
            rpcParametersJson.put("pageNumber", pageNumber);
        }

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BLOCK_TRANSACTIONS");
        rpcRequestJson.put("parameters", rpcParametersJson);
        return _executeJsonRequest(rpcRequestJson);
    }

    protected Json _getBlockTransactions(final Long blockHeight, final Integer pageSize, final Integer pageNumber) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("blockHeight", blockHeight);
        if (pageSize != null) {
            rpcParametersJson.put("pageSize", pageSize);
        }
        if (pageNumber != null) {
            rpcParametersJson.put("pageNumber", pageNumber);
        }

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BLOCK_TRANSACTIONS");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    protected Json _getTransaction(final Sha256Hash transactionHash, final Boolean hexFormat) {
        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("hash", transactionHash);
        if (hexFormat != null) {
            rpcParametersJson.put("rawFormat", (hexFormat ? 1 : 0));
        }

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "TRANSACTION");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    protected Json _getBlockHeaders(final Long blockHeight, final Integer maxBlockCount, final Boolean returnRawFormat, final BlockHeaderDirection direction) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcParametersJson = new Json();
        if (blockHeight != null) {
            rpcParametersJson.put("blockHeight", blockHeight);
        }
        rpcParametersJson.put("maxBlockCount", maxBlockCount);
        rpcParametersJson.put("rawFormat", (returnRawFormat ? 1 : 0));

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", (direction == BlockHeaderDirection.BEFORE ? "BLOCK_HEADERS_BEFORE" : "BLOCK_HEADERS_AFTER"));
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    protected Json _getAddressTransactions(final TypedAddress address, final Sha256Hash scriptHash, final Boolean hexFormat, final Boolean shouldReturnTransactionHashes) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcParametersJson = new Json();
        if (address != null) {
            rpcParametersJson.put("address", address);
        }
        else {
            rpcParametersJson.put("scriptHash", scriptHash);
        }
        if (shouldReturnTransactionHashes != null) {
            rpcParametersJson.put("transactionHashesOnly", (shouldReturnTransactionHashes ? 1 : 0));
        }
        else if (hexFormat != null) {
            rpcParametersJson.put("rawFormat", (hexFormat ? 1 : 0));
        }

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "ADDRESS");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    protected Json _getAddressBalance(final TypedAddress address, final Sha256Hash scriptHash) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcParametersJson = new Json();
        if (address != null) {
            rpcParametersJson.put("address", address);
        }
        else {
            rpcParametersJson.put("scriptHash", scriptHash);
        }

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BALANCE");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public NodeJsonRpcConnection(final String hostname, final Integer port) {
        this(
            hostname,
            port,
            new CoreInflater()
        );
    }

    public NodeJsonRpcConnection(final String hostname, final Integer port, final MasterInflater masterInflater) {
        _masterInflater = masterInflater;

        final java.net.Socket javaSocket;
        {
            java.net.Socket socket = null;
            try {
                socket = new java.net.Socket(hostname, port);
            }
            catch (final Exception exception) {
                Logger.debug("Unable to connect to endpoint: " + hostname + ":" + port);
            }
            javaSocket = socket;
        }

        _jsonSocket = ((javaSocket != null) ? new JsonSocket(javaSocket) : null);

        if (_jsonSocket != null) {
            _jsonSocket.setMessageReceivedCallback(_onNewMessageCallback);
        }
    }

    public NodeJsonRpcConnection(final java.net.Socket javaSocket) {
        this(
            javaSocket,
            new CoreInflater()
        );
    }

    public NodeJsonRpcConnection(final java.net.Socket socket, final MasterInflater masterInflater) {
        _masterInflater = masterInflater;

        _jsonSocket = ((socket != null) ? new JsonSocket(socket) : null);

        if (_jsonSocket != null) {
            _jsonSocket.setMessageReceivedCallback(_onNewMessageCallback);
        }
    }

    public void setRpcDurationTimeoutMs(final Long maxRequestDurationMs) {
        _rpcDurationTimeoutMs = maxRequestDurationMs;
    }

    public Json getBlockHeadersBefore(final Long blockHeight, final Integer maxBlockCount, final Boolean returnRawFormat) {
        return _getBlockHeaders(blockHeight, maxBlockCount, returnRawFormat, BlockHeaderDirection.BEFORE);
    }

    public Json getBlockHeadersBeforeHead(final Integer maxBlockCount, final Boolean returnRawFormat) {
        return _getBlockHeaders(null, maxBlockCount, returnRawFormat, BlockHeaderDirection.BEFORE);
    }

    public Json getBlockHeadersAfter(final Long blockHeight, final Integer maxBlockCount, final Boolean returnRawFormat) {
        return _getBlockHeaders(blockHeight, maxBlockCount, returnRawFormat, BlockHeaderDirection.AFTER);
    }

    public Json getBlockHeadersAfterGenesis(final Integer maxBlockCount, final Boolean returnRawFormat) {
        return _getBlockHeaders(null, maxBlockCount, returnRawFormat, BlockHeaderDirection.AFTER);
    }

    public Json getDifficulty() {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "DIFFICULTY");

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getBlockReward() {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BLOCK_REWARD");

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getUnconfirmedTransactions(final Boolean returnRawFormat) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("rawFormat", (returnRawFormat ? 1 : 0));

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "UNCONFIRMED_TRANSACTIONS");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getBlockHeight() {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BLOCK_HEIGHT");

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getTransactionBlockHeight(final Sha256Hash transactionHash) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("transactionHash", transactionHash);

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BLOCK_HEIGHT");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getBlockHeaderHeight(final Sha256Hash blockHash) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("hash", blockHash);

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "BLOCK_HEIGHT");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getBlockchainMetadata() {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcRequestJson = new Json();
        {
            rpcRequestJson.put("method", "GET");
            rpcRequestJson.put("query", "BLOCKCHAIN");
        }

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getNodes() {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcRequestJson = new Json();
        {
            rpcRequestJson.put("method", "GET");
            rpcRequestJson.put("query", "NODES");
        }

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getAddressTransactions(final TypedAddress TypedAddress) {
        return this.getAddressTransactions(TypedAddress, null); // NOTE: null omits parameter (uses server defaults)
    }

    public Json getAddressTransactions(final TypedAddress TypedAddress, final Boolean hexFormat) {
        return _getAddressTransactions(TypedAddress, null, hexFormat, false);
    }

    public Json getAddressTransactions(final Sha256Hash scriptHash, final Boolean hexFormat) {
        return _getAddressTransactions(null, scriptHash, hexFormat, false);
    }

    public Json getAddressTransactionHashes(final TypedAddress address) {
        return _getAddressTransactions(address, null, null, true);
    }

    public Json getAddressTransactionHashes(final Sha256Hash scriptHash) {
        return _getAddressTransactions(null, scriptHash, null, true);
    }

    public Json getAddressBalance(final TypedAddress address) {
        return _getAddressBalance(address, null);
    }

    public Json getAddressBalance(final Sha256Hash scriptHash) {
        return _getAddressBalance(null, scriptHash);
    }

    public Json getBlock(final Sha256Hash blockHash) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        return _getBlock(blockHash, null);
    }

    public Json getBlock(final Sha256Hash blockHash, final Boolean hexFormat) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        return _getBlock(blockHash, hexFormat);
    }

    public Json getBlock(final Long blockHeight) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        return _getBlock(blockHeight, null);
    }

    public Json getBlock(final Long blockHeight, final Boolean hexFormat) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        return _getBlock(blockHeight, hexFormat);
    }

    public Json getBlockHeader(final Sha256Hash blockHash) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        return _getBlockHeader(blockHash, null);
    }

    public Json getBlockHeader(final Sha256Hash blockHash, final Boolean hexFormat) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        return _getBlockHeader(blockHash, hexFormat);
    }

    public Json getBlockHeader(final Long blockHeight) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        return _getBlockHeader(blockHeight, null);
    }

    public Json getBlockHeader(final Long blockHeight, final Boolean hexFormat) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        return _getBlockHeader(blockHeight, hexFormat);
    }

    public Json getBlockTransactions(final Sha256Hash blockHash, final Integer pageSize, final Integer pageNumber) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        return _getBlockTransactions(blockHash, pageSize, pageNumber);
    }

    public Json getBlockTransactions(final Long blockHeight, final Integer pageSize, final Integer pageNumber) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        return _getBlockTransactions(blockHeight, pageSize, pageNumber);
    }

    public Json getTransaction(final Sha256Hash transactionHash) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        return _getTransaction(transactionHash, null);
    }

    public Json getTransaction(final Sha256Hash transactionHash, final Boolean hexFormat) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        return _getTransaction(transactionHash, hexFormat);
    }

    public Json getDoubleSpendProofs() {
        final Json rpcParametersJson = new Json(false);

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "DOUBLE_SPEND_PROOFS");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getDoubleSpendProof(final Sha256Hash doubleSpendProofHash) {
        final Json rpcParametersJson = new Json(false);
        rpcParametersJson.put("hash", doubleSpendProofHash);

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "DOUBLE_SPEND_PROOF");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getDoubleSpendProof(final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent) {
        final Json rpcParametersJson = new Json(false);
        rpcParametersJson.put("transactionOutputIdentifier", transactionOutputIdentifierBeingSpent);

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "DOUBLE_SPEND_PROOF");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getTransactionDoubleSpendProofs(final Sha256Hash transactionHash) {
        final Json rpcParametersJson = new Json(false);
        rpcParametersJson.put("transactionHash", transactionHash);

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "DOUBLE_SPEND_PROOF");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getStatus() {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "STATUS");

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getUtxoCacheStatus() {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "UTXO_CACHE");

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json commitUtxoCache() {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "POST");
        rpcRequestJson.put("query", "COMMIT_UTXO_CACHE");

        return _executeJsonRequest(rpcRequestJson);
    }

    /**
     * Subscribes to the Node for new Block/Transaction announcements.
     *  The NodeJsonRpcConnection is consumed by this operation and cannot be used for additional API calls.
     *  The underlying JsonSocket remains connected and must be closed when announcements are no longer desired.
     */
    public Boolean upgradeToAnnouncementHook(final AnnouncementHookCallback announcementHookCallback) {
        return this.upgradeToAnnouncementHook(announcementHookCallback, null);
    }

    public Boolean upgradeToAnnouncementHook(final AnnouncementHookCallback announcementHookCallback, final List<TypedAddress> addressesFilter) {
        if (announcementHookCallback == null) { throw new NullPointerException("Attempted to create AnnouncementHook without a callback."); }
        if (_jsonSocket == null) { return false; } // Socket was unable to connect.

        final Json registerHookRpcJson = _createRegisterHookRpcJson(false, true, addressesFilter);

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

                    case "TRANSACTION_WITH_FEE":
                    case "TRANSACTION": {
                        announcementHookCallback.onNewTransaction(object);
                    } break;

                    case "DOUBLE_SPEND_PROOF": {
                        announcementHookCallback.onNewDoubleSpendProof(object);
                    } break;

                    default: { } break;
                }
            }
        });

        _isUpgradedToHook = true;
        _announcementHookExpectsRawTransactionData = false;

        return true;
    }

    public Boolean replaceAnnouncementHookAddressFilter(final List<TypedAddress> addressesFilter) {
        if (! _isUpgradedToHook) { return false; }
        if (_jsonSocket == null) { return false; } // Socket was unable to connect.

        final boolean returnRawData = _announcementHookExpectsRawTransactionData;
        final Json rpcRequestJson = _createRegisterHookRpcJson(returnRawData, true, addressesFilter);
        rpcRequestJson.put("query", "UPDATE_HOOK");
        _jsonSocket.write(new JsonProtocolMessage(rpcRequestJson));
        return true;
    }

    public Boolean upgradeToAnnouncementHook(final RawAnnouncementHookCallback announcementHookCallback) {
        return this.upgradeToAnnouncementHook(announcementHookCallback, null);
    }
    public Boolean upgradeToAnnouncementHook(final RawAnnouncementHookCallback announcementHookCallback, final List<TypedAddress> addressesFilter) {
        if (announcementHookCallback == null) { throw new NullPointerException("Null AnnouncementHookCallback found."); }
        if (_jsonSocket == null) { return false; } // Socket was unable to connect.

        final Json registerHookRpcJson = _createRegisterHookRpcJson(true, true, addressesFilter);

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
                        final BlockHeaderInflater blockHeaderInflater = _masterInflater.getBlockHeaderInflater();
                        final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString(objectData));
                        if (blockHeader == null) {
                            Logger.warn("Error inflating block: " + objectData);
                            return;
                        }

                        announcementHookCallback.onNewBlockHeader(blockHeader);
                    } break;

                    case "TRANSACTION": {
                        final String objectData = json.getString("object");
                        final TransactionInflater transactionInflater = _masterInflater.getTransactionInflater();
                        final Transaction transaction = transactionInflater.fromBytes(ByteArray.fromHexString(objectData));
                        if (transaction == null) {
                            Logger.warn("Error inflating transaction: " + objectData);
                            return;
                        }

                        announcementHookCallback.onNewTransaction(transaction, null);
                    } break;

                    case "TRANSACTION_WITH_FEE": {
                        final Json object = json.get("object");
                        final String transactionData = object.getString("transactionData");
                        final Long fee = object.getLong("transactionFee");
                        final TransactionInflater transactionInflater = _masterInflater.getTransactionInflater();
                        final Transaction transaction = transactionInflater.fromBytes(ByteArray.fromHexString(transactionData));
                        if (transaction == null) {
                            Logger.warn("Error inflating transaction: " + transactionData);
                            return;
                        }

                        announcementHookCallback.onNewTransaction(transaction, fee);
                    } break;

                    case "DOUBLE_SPEND_PROOF": {
                        final String objectData = json.getString("object");
                        final DoubleSpendProofInflater doubleSpendProofInflater = _masterInflater.getDoubleSpendProofInflater();
                        final DoubleSpendProof doubleSpendProof = doubleSpendProofInflater.fromBytes(ByteArray.fromHexString(objectData));
                        if (doubleSpendProof == null) {
                            Logger.warn("Error inflating double spend proof: " + objectData);
                            return;
                        }

                        announcementHookCallback.onNewDoubleSpendProof(doubleSpendProof);
                    } break;

                    default: { } break;
                }
            }
        });

        _isUpgradedToHook = true;
        _announcementHookExpectsRawTransactionData = true;

        return true;
    }

    public Json validatePrototypeBlock(final Block block) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcParametersJson = new Json();
        final BlockDeflater blockDeflater = _masterInflater.getBlockDeflater();
        rpcParametersJson.put("blockData", blockDeflater.toBytes(block));

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "POST");
        rpcRequestJson.put("query", "VALIDATE_PROTOTYPE_BLOCK");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getPrototypeBlock(final Boolean returnRawData) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("rawFormat", (returnRawData ? 1 : 0));

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "PROTOTYPE_BLOCK");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json submitTransaction(final Transaction transaction) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcParametersJson = new Json();
        final TransactionDeflater transactionDeflater = _masterInflater.getTransactionDeflater();
        rpcParametersJson.put("transactionData", transactionDeflater.toBytes(transaction));

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "POST");
        rpcRequestJson.put("query", "TRANSACTION");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json submitBlock(final Block block) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcParametersJson = new Json();
        final BlockDeflater blockDeflater = _masterInflater.getBlockDeflater();
        rpcParametersJson.put("blockData", blockDeflater.toBytes(block));

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "POST");
        rpcRequestJson.put("query", "BLOCK");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json isSlpTransaction(final Sha256Hash transactionHash) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("hash", transactionHash);

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "IS_SLP_TRANSACTION");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json isValidSlpTransaction(final Sha256Hash transactionHash) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("hash", transactionHash);

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "IS_VALID_SLP_TRANSACTION");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json validateTransaction(final Transaction transaction, final Boolean enableSlpValidation) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        final ByteArray transactionBytes = transactionDeflater.toBytes(transaction);

        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("transactionData", transactionBytes);
        rpcParametersJson.put("enableSlpValidation", (enableSlpValidation ? 1 : 0));

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "POST");
        rpcRequestJson.put("query", "IS_VALID_SLP_TRANSACTION");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public Json getSlpTokenId(final Sha256Hash transactionHash) {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("hash", transactionHash);

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "GET");
        rpcRequestJson.put("query", "SLP_TOKEN_ID");
        rpcRequestJson.put("parameters", rpcParametersJson);

        return _executeJsonRequest(rpcRequestJson);
    }

    public JsonSocket getJsonSocket() {
        return _jsonSocket;
    }

    public Boolean isConnected() {
        return ( (_jsonSocket != null) && _jsonSocket.isConnected() );
    }

    public void enableKeepAlive(final Boolean keepAliveIsEnabled) {
        if (_jsonSocket == null) { return; } // Socket was unable to connect.

        final Json rpcParametersJson = new Json();
        rpcParametersJson.put("enableKeepAlive", keepAliveIsEnabled);

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "POST");
        rpcRequestJson.put("query", "KEEP_ALIVE");
        rpcRequestJson.put("parameters", rpcParametersJson);

        _executeJsonRequest(rpcRequestJson);
    }

    public Long ping() {
        if (_jsonSocket == null) { return null; } // Socket was unable to connect.

        final NanoTimer nanoTimer = new NanoTimer();

        final Json rpcRequestJson = new Json();
        rpcRequestJson.put("method", "POST");
        rpcRequestJson.put("query", "PING");

        nanoTimer.start();
        final Json response = _executeJsonRequest(rpcRequestJson);
        nanoTimer.stop();

        if (response == null || (! response.getBoolean("wasSuccess"))) {
            return null;
        }

        return nanoTimer.getMillisecondsElapsed().longValue();
    }

    @Override
    public void close() {
        if (_jsonSocket != null) {
            _jsonSocket.close();
        }
    }
}
