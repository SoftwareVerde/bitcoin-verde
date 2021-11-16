package com.softwareverde.bitcoin.rpc;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.rpc.monitor.Monitor;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

public class BitcoinVerdeRpcConnector implements BitcoinMiningRpcConnector, AutoCloseable {
    public static final String IDENTIFIER = "VERDE";

    public static BlockTemplate toBlockTemplate(final Block block, final Long blockHeight, final SystemTime systemTime) {
        final MutableBlockTemplate blockTemplate = new MutableBlockTemplate();

        blockTemplate.setBlockVersion(block.getVersion());
        blockTemplate.setDifficulty(block.getDifficulty());
        blockTemplate.setPreviousBlockHash(block.getPreviousBlockHash());
        blockTemplate.setMinimumBlockTime(block.getTimestamp());
        blockTemplate.setNonceRange(BlockTemplate.DEFAULT_NONCE_RANGE);

        blockTemplate.setBlockHeight(blockHeight);

        Transaction coinbaseTransaction = null;
        for (final Transaction transaction : block.getTransactions()) {
            if (coinbaseTransaction == null) {
                coinbaseTransaction = transaction;
                continue;
            }

            final Long fee = 0L; // Unsupported.
            final Integer signatureOperationCount = 0; // Unsupported.
            blockTemplate.addTransaction(transaction, fee, signatureOperationCount);
        }

        if (coinbaseTransaction != null) {
            blockTemplate.setCoinbaseAmount(coinbaseTransaction.getTotalOutputValue());
        }

        final long maxBlockByteCount = BitcoinConstants.getBlockMaxByteCount();
        final long maximumSignatureOperationCount = (maxBlockByteCount / Block.MIN_BYTES_PER_SIGNATURE_OPERATION);

        final Long now = systemTime.getCurrentTimeInSeconds();
        blockTemplate.setCurrentTime(now);
        blockTemplate.setMaxSignatureOperationCount(maximumSignatureOperationCount);
        blockTemplate.setMaxBlockByteCount(maxBlockByteCount);

        blockTemplate.setTarget(block.getDifficulty().getBytes());

        final String longPollId = (block.getPreviousBlockHash() + "" + now).toLowerCase();
        blockTemplate.setLongPollId(longPollId);

        blockTemplate.setCoinbaseAuxFlags("");
        blockTemplate.addCapability("proposal");
        blockTemplate.addMutableField("time");
        blockTemplate.addMutableField("transactions");
        blockTemplate.addMutableField("prevblock");

        return blockTemplate;
    }

    protected final SystemTime _systemTime;
    protected final CachedThreadPool _threadPool;
    protected final BitcoinNodeRpcAddress _bitcoinNodeRpcAddress;
    protected final RpcCredentials _rpcCredentials;

    protected JsonSocket _socketConnection = null;

    protected String _toString() {
        return (this.getHost() + ":" + this.getPort());
    }

    public BitcoinVerdeRpcConnector(final BitcoinNodeRpcAddress bitcoinNodeRpcAddress, final RpcCredentials rpcCredentials) {
        _systemTime = new SystemTime();
        _bitcoinNodeRpcAddress = bitcoinNodeRpcAddress;
        _rpcCredentials = rpcCredentials;

        _threadPool = new CachedThreadPool(32, 5000L);
        _threadPool.start();
    }

    @Override
    public String getHost() {
        return _bitcoinNodeRpcAddress.getHost();
    }

    @Override
    public Integer getPort() {
        return _bitcoinNodeRpcAddress.getPort();
    }

    @Override
    public Monitor getMonitor() {
        return new BitcoinVerdeRpcMonitor();
    }

    @Override
    public Response handleRequest(final Request request, final Monitor monitor) {
        final String rawPostData = StringUtil.bytesToString(request.getRawPostData());
        final Json requestJson = Json.parse(rawPostData);

        final Integer requestId = requestJson.getInteger("id");
        final String query = requestJson.getString("method").toLowerCase();

        final String host = _bitcoinNodeRpcAddress.getHost();
        final Integer port = _bitcoinNodeRpcAddress.getPort();
        try (final NodeJsonRpcConnection nodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool)) {
            if (monitor instanceof BitcoinVerdeRpcMonitor) {
                ((BitcoinVerdeRpcMonitor) monitor).beforeRequestStart(nodeJsonRpcConnection);
            }

            try {
                switch (query) {
                    default: {
                        Logger.debug("Unsupported command: " + query);
                        final Response response = new Response();
                        response.setCode(Response.Codes.BAD_REQUEST);
                        response.setContent("Unsupported method: " + query);
                        return response;
                    }
                }
            }
            finally {
                if (monitor instanceof BitcoinVerdeRpcMonitor) {
                    ((BitcoinVerdeRpcMonitor) monitor).afterRequestEnd();
                }
            }
        }
    }

    @Override
    public BlockTemplate getBlockTemplate(final Monitor monitor) {
        final String host = _bitcoinNodeRpcAddress.getHost();
        final Integer port = _bitcoinNodeRpcAddress.getPort();

        final Json prototypeBlockJson;
        try (final NodeJsonRpcConnection nodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool)) {
            prototypeBlockJson = nodeJsonRpcConnection.getPrototypeBlock(true);
        }
        if (prototypeBlockJson == null) {
            Logger.warn("Error executing get-prototype from node.");
            return null;
        }

        final BlockInflater blockInflater = new BlockInflater();
        final ByteArray blockData = ByteArray.fromHexString(prototypeBlockJson.getString("block"));
        final Block block = blockInflater.fromBytes(blockData);
        if (block == null) {
            Logger.warn("Error retrieving prototype block.");
            return null;
        }

        final long blockHeight;
        try (final NodeJsonRpcConnection blockHeightNodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool)) {
            final Json responseJson = blockHeightNodeJsonRpcConnection.getBlockHeight();
            if (responseJson == null) {
                Logger.warn("Error executing get-prototype from node.");
                return null;
            }

            blockHeight = (responseJson.getLong("blockHeight") + 1L);
        }

        return BitcoinVerdeRpcConnector.toBlockTemplate(block, blockHeight, _systemTime);
    }

    @Override
    public Boolean validateBlockTemplate(final BlockTemplate blockTemplate, final Monitor monitor) {
        final String host = _bitcoinNodeRpcAddress.getHost();
        final Integer port = _bitcoinNodeRpcAddress.getPort();

        final Block block = blockTemplate.toBlock();

        final Json responseJson;
        try (final NodeJsonRpcConnection nodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool)) {
            responseJson = nodeJsonRpcConnection.validatePrototypeBlock(block);
        }
        if (responseJson == null) {
            Logger.warn("Unable to validate template block.");
            return null;
        }

        final Json validationResult = responseJson.get("blockValidation");
        return validationResult.getBoolean("isValid");
    }

    @Override
    public Boolean submitBlock(final Block block, final Monitor monitor) {
        final String host = _bitcoinNodeRpcAddress.getHost();
        final Integer port = _bitcoinNodeRpcAddress.getPort();

        final Json responseJson;
        try (final NodeJsonRpcConnection nodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool)) {
            responseJson = nodeJsonRpcConnection.submitBlock(block);
        }
        if (responseJson == null) {
            Logger.warn("Unable to validate template block.");
            return false;
        }

        return responseJson.getBoolean("wasSuccess");
    }

    @Override
    public Boolean supportsNotifications() {
        return true;
    }

    @Override
    public Boolean supportsNotification(final RpcNotificationType rpcNotificationType) {
        return (Util.areEqual(RpcNotificationType.BLOCK_HASH, rpcNotificationType) || Util.areEqual(RpcNotificationType.TRANSACTION_HASH, rpcNotificationType));
    }

    @Override
    public void subscribeToNotifications(final RpcNotificationCallback notificationCallback) {
        if (_socketConnection != null) { return; }

        final String host = _bitcoinNodeRpcAddress.getHost();
        final Integer port = _bitcoinNodeRpcAddress.getPort();
        final NodeJsonRpcConnection nodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool);
        nodeJsonRpcConnection.upgradeToAnnouncementHook(new NodeJsonRpcConnection.AnnouncementHookCallback() {
            @Override
            public void onNewBlockHeader(final Json json) {
                final String blockHashString = json.getString("hash");
                final Sha256Hash blockHash = Sha256Hash.fromHexString(blockHashString);
                Logger.trace("Block: " + blockHash + " from " + _toString());

                final RpcNotification rpcNotification = new RpcNotification(RpcNotificationType.BLOCK_HASH, blockHash);
                notificationCallback.onNewNotification(rpcNotification);
            }

            @Override
            public void onNewTransaction(final Json json) {
                final String transactionHashString = json.getString("hash");
                final Sha256Hash transactionHash = Sha256Hash.fromHexString(transactionHashString);
                Logger.trace("Transaction: " + transactionHash + " from " + _toString());

                final RpcNotification rpcNotification = new RpcNotification(RpcNotificationType.TRANSACTION_HASH, transactionHash);
                notificationCallback.onNewNotification(rpcNotification);
            }
        });
        _socketConnection = nodeJsonRpcConnection.getJsonSocket();
    }

    @Override
    public void unsubscribeToNotifications() {
        _socketConnection.close();
    }

    @Override
    public String toString() {
        return _toString();
    }

    @Override
    public void close() throws Exception {
        _threadPool.stop();
    }
}
