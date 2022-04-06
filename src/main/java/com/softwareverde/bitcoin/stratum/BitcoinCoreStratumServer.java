package com.softwareverde.bitcoin.stratum;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.rpc.BitcoinMiningRpcConnector;
import com.softwareverde.bitcoin.rpc.BitcoinNodeRpcAddress;
import com.softwareverde.bitcoin.rpc.BlockTemplate;
import com.softwareverde.bitcoin.rpc.RpcCredentials;
import com.softwareverde.bitcoin.rpc.RpcNotification;
import com.softwareverde.bitcoin.rpc.RpcNotificationCallback;
import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.bitcoin.rpc.core.BitcoinCoreRpcConnector;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.bitcoin.server.stratum.message.ResponseMessage;
import com.softwareverde.bitcoin.server.stratum.message.server.MinerSubmitBlockResponseMessage;
import com.softwareverde.bitcoin.server.stratum.message.server.MinerSubscribeResponseMessage;
import com.softwareverde.bitcoin.server.stratum.message.server.SetDifficultyMessage;
import com.softwareverde.bitcoin.stratum.callback.BlockFoundCallback;
import com.softwareverde.bitcoin.stratum.callback.WorkerShareCallback;
import com.softwareverde.bitcoin.stratum.message.server.MinerNotifyMessage;
import com.softwareverde.bitcoin.stratum.socket.StratumServerSocket;
import com.softwareverde.bitcoin.stratum.task.MutableStratumMineBlockTaskBuilder;
import com.softwareverde.bitcoin.stratum.task.StratumMineBlockTask;
import com.softwareverde.bitcoin.stratum.task.StratumMineBlockTaskBuilderCore;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.timer.NanoTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


public class BitcoinCoreStratumServer implements StratumServer {
    protected static final Boolean PROXY_VIABTC = false;

    protected final MasterInflater _masterInflater;
    protected final StratumProperties _stratumProperties;
    protected final StratumServerSocket _stratumServerSocket;
    protected final ThreadPool _threadPool;

    protected final Boolean _blockTemplateValidationIsEnabled = true;
    protected final Integer _extraNonceByteCount = 4;
    protected final Integer _extraNonce2ByteCount = 4;
    protected final Integer _totalExtraNonceByteCount = (_extraNonceByteCount + _extraNonce2ByteCount);
    protected final ByteArray _seedBytes;

    protected final SystemTime _systemTime = new SystemTime();

    // Multi buffered maps to gradually purge old tasks between block template regeneration...
    protected final ConcurrentHashMap<Long, StratumMineBlockTask> _mineBlockTasks = new ConcurrentHashMap<>(); // Map: JobId -> MineBlockTask
    protected final ConcurrentHashMap<Long, StratumMineBlockTask> _olderMineBlockTasks0 = new ConcurrentHashMap<>(); // Newer
    protected final ConcurrentHashMap<Long, StratumMineBlockTask> _olderMineBlockTasks1 = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<Long, StratumMineBlockTask> _olderMineBlockTasks2 = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<Long, StratumMineBlockTask> _olderMineBlockTasks3 = new ConcurrentHashMap<>(); // Older

    protected Address _coinbaseAddress;
    protected final Thread _rebuildBlockTemplateThread;
    protected BlockTemplate _blockTemplate;
    protected final NanoTimer _timeSinceLastTemplateValidation = new NanoTimer();

    protected final Long _startTime = _systemTime.getCurrentTimeInSeconds();
    protected Long _currentBlockStartTime = _systemTime.getCurrentTimeInSeconds();
    protected final AtomicLong _shareCount = new AtomicLong(0L);

    protected final ConcurrentHashMap<Long, JsonSocket> _connections = new ConcurrentHashMap<>(); // ConnectionId -> Connection

    protected WorkerShareCallback _workerShareCallback;
    protected BlockFoundCallback _blockFoundCallback;

    protected Boolean _invertDifficultyEnabled = false;
    protected Long _baseShareDifficulty = 2048L;

    protected Address _getCoinbaseAddress() {
        return _coinbaseAddress;
    }

    protected synchronized void _rebuildBlockTemplate() {
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final BlockTemplate blockTemplate;
        try (final BitcoinMiningRpcConnector rpcConnection = _getBitcoinRpcConnector()) {
            blockTemplate = rpcConnection.getBlockTemplate();
        }
        nanoTimer.stop();

        if (blockTemplate == null) {
            Logger.info("Unable to acquire new block template. (" + nanoTimer.getMillisecondsElapsed() + "ms)");
            return;
        }

        _blockTemplate = blockTemplate;
        Logger.trace("Acquired new block template in " + nanoTimer.getMillisecondsElapsed() + "ms.");
    }

    protected void _abandonMiningTasks() {
        _mineBlockTasks.clear();
        _olderMineBlockTasks0.clear();
        _olderMineBlockTasks1.clear();
        _olderMineBlockTasks2.clear();
        _olderMineBlockTasks3.clear();
    }

    protected synchronized void _deprecateMiningTasks() {
        _olderMineBlockTasks3.clear();
        _olderMineBlockTasks3.putAll(_olderMineBlockTasks2);

        _olderMineBlockTasks2.clear();
        _olderMineBlockTasks2.putAll(_olderMineBlockTasks1);

        _olderMineBlockTasks1.clear();
        _olderMineBlockTasks1.putAll(_olderMineBlockTasks0);

        _olderMineBlockTasks0.clear();
        _olderMineBlockTasks0.putAll(_mineBlockTasks);

        _mineBlockTasks.clear();
    }

    protected StratumMineBlockTask _getMineBlockTask(final Long taskId) {
        StratumMineBlockTask mineBlockTask;

        mineBlockTask = _mineBlockTasks.get(taskId);
        if (mineBlockTask != null) { return mineBlockTask; }

        mineBlockTask = _olderMineBlockTasks0.get(taskId);
        if (mineBlockTask != null) { return mineBlockTask; }

        mineBlockTask = _olderMineBlockTasks1.get(taskId);
        if (mineBlockTask != null) { return mineBlockTask; }

        mineBlockTask = _olderMineBlockTasks2.get(taskId);
        if (mineBlockTask != null) { return mineBlockTask; }

        mineBlockTask = _olderMineBlockTasks3.get(taskId);
        if (mineBlockTask != null) { return mineBlockTask; }

        return null;
    }

    protected void _broadcastNewTask(final Boolean abandonOldJobs) {
        for (final JsonSocket jsonSocket : _connections.values()) {
            if (jsonSocket == null) { continue; }
            if (! jsonSocket.isConnected()) { continue; }

            _sendWork(jsonSocket, abandonOldJobs);
        }
    }

    protected ByteArray _createRandomBytes(final int byteCount) {
        int i = 0;
        final MutableByteArray mutableByteArray = new MutableByteArray(byteCount);
        while (i < byteCount) {
            final byte[] randomBytes = ByteUtil.integerToBytes((int) (Math.random() * Integer.MAX_VALUE));
            for (byte b : randomBytes) {
                mutableByteArray.setByte(i, b);
                i += 1;
                if (i >= byteCount) { break; }
            }
        }
        return mutableByteArray;
    }

    protected ByteArray _getExtraNonce(final Long jsonSocketId) {
        final ByteArray socketIdBytes = ByteArray.wrap(ByteUtil.longToBytes(jsonSocketId));
        final MutableByteArray extraNonce = new MutableByteArray(_seedBytes);
        final int byteCount = Math.min(socketIdBytes.getByteCount(), extraNonce.getByteCount());
        for (int i = 0; i < byteCount; ++i) {
            final byte byteA = extraNonce.getByte(i);
            final byte byteB = socketIdBytes.getByte(i);
            extraNonce.setByte(i, (byte) (byteA ^ byteB));
        }
        return extraNonce;
    }

    protected BitcoinMiningRpcConnector _getBitcoinRpcConnector() {
        final String bitcoinRpcUrl = _stratumProperties.getBitcoinRpcUrl();
        final Integer bitcoinRpcPort = _stratumProperties.getBitcoinRpcPort();

        final RpcCredentials rpcCredentials = _stratumProperties.getRpcCredentials();
        final BitcoinNodeRpcAddress bitcoinNodeRpcAddress = new BitcoinNodeRpcAddress(bitcoinRpcUrl, bitcoinRpcPort, false);

        return new BitcoinCoreRpcConnector(bitcoinNodeRpcAddress, rpcCredentials);
    }

    protected void _resetHashCountMonitoring() {
        _currentBlockStartTime = _systemTime.getCurrentTimeInSeconds();
        _shareCount.set(0L);
    }

    protected synchronized StratumMineBlockTask _buildNewMiningTask(final Long jsonSocketId) {
        final TransactionInflater transactionInflater = _masterInflater.getTransactionInflater();
        final TransactionDeflater transactionDeflater = _masterInflater.getTransactionDeflater();

        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final ByteArray extraNonce = _getExtraNonce(jsonSocketId);
        final MutableStratumMineBlockTaskBuilder stratumMineBlockTaskBuilder = new StratumMineBlockTaskBuilderCore(_totalExtraNonceByteCount, transactionDeflater);

        final String coinbaseMessage = BitcoinConstants.getCoinbaseMessage();
        final List<ByteArray> extraBytes = new ImmutableList<>(
            _createRandomBytes(8) // Creates a unique job for each task, preventing duplicate shares from being created under high workload.
        );

        final Address address = _getCoinbaseAddress();

        final BlockTemplate blockTemplate = _blockTemplate;
        if (blockTemplate == null) {
            Logger.debug("Block template not available.");
            return null;
        }

        final Long blockHeight = blockTemplate.getBlockHeight();
        final Sha256Hash previousBlockHash = blockTemplate.getPreviousBlockHash();
        final Difficulty difficulty = blockTemplate.getDifficulty();
        final List<Transaction> transactions = blockTemplate.getTransactions();
        final Long coinbaseAmount = blockTemplate.getCoinbaseAmount();

        // NOTE: Coinbase is mutated by the StratumMineTaskFactory to include the Transaction Fees...
        final Transaction coinbaseTransaction = transactionInflater.createCoinbaseTransactionWithExtraNonce(blockHeight, coinbaseMessage, extraBytes, _totalExtraNonceByteCount, address, coinbaseAmount);

        stratumMineBlockTaskBuilder.setBlockVersion(BlockHeader.VERSION);
        stratumMineBlockTaskBuilder.setPreviousBlockHash(previousBlockHash);
        stratumMineBlockTaskBuilder.setDifficulty(difficulty);
        stratumMineBlockTaskBuilder.setCoinbaseTransaction(coinbaseTransaction);
        stratumMineBlockTaskBuilder.setExtraNonce(extraNonce);
        stratumMineBlockTaskBuilder.setBlockHeight(blockHeight);

        stratumMineBlockTaskBuilder.setTransactions(transactions);

        final StratumMineBlockTask mineBlockTask = stratumMineBlockTaskBuilder.buildMineBlockTask();

        nanoTimer.stop();
        Logger.trace("Built mining task from prototype block in " + nanoTimer.getMillisecondsElapsed() + "ms.");

        // NOTE: Validating the block template is not a quick process, and generating new jobs happens frequently, so validation is sporadic.
        final boolean validateTemplateWaitThresholdIsExceeded;
        {
            _timeSinceLastTemplateValidation.stop();
            final Double msSinceLastTemplateValidation = _timeSinceLastTemplateValidation.getMillisecondsElapsed();
            validateTemplateWaitThresholdIsExceeded = (msSinceLastTemplateValidation >= 60000L); // Validate template every ~60s.
        }
        if (_blockTemplateValidationIsEnabled && validateTemplateWaitThresholdIsExceeded) { // Validate template...
            final NanoTimer validateTimer = new NanoTimer();
            validateTimer.start();
            final Block block = mineBlockTask.assembleBlockTemplate(_extraNonceByteCount, _extraNonce2ByteCount);
            final Boolean isValid;
            try (final BitcoinMiningRpcConnector rpcConnection = _getBitcoinRpcConnector()) {
                isValid = rpcConnection.validateBlockTemplate(block);
            }
            validateTimer.stop();
            Logger.trace("Validated block template (" + isValid + ") in " + validateTimer.getMillisecondsElapsed() + "ms.");

            _timeSinceLastTemplateValidation.reset();
            _timeSinceLastTemplateValidation.start();
        }

        return mineBlockTask;
    }

    protected void _sendWork(final JsonSocket jsonSocket, final Boolean abandonOldJobs) {
        final Long jsonSocketId = jsonSocket.getId();

        final StratumMineBlockTask mineBlockTask = _buildNewMiningTask(jsonSocketId);
        if (mineBlockTask == null) {
            Logger.debug("Unable to send work to " + jsonSocket + ".");
            return;
        }

        final Long mineBlockTaskId = mineBlockTask.getId();
        _mineBlockTasks.put(mineBlockTaskId, mineBlockTask);

        final RequestMessage mineBlockRequest = mineBlockTask.createRequest(abandonOldJobs);
        jsonSocket.write(new JsonProtocolMessage(mineBlockRequest));
        Logger.debug("Sent: " + mineBlockRequest);
    }

    protected void _setDifficulty(final JsonSocket socketConnection) {
        final Long shareDifficulty = _baseShareDifficulty;

        final SetDifficultyMessage setDifficultyMessage = new SetDifficultyMessage();
        setDifficultyMessage.setShareDifficulty(shareDifficulty);

        socketConnection.write(new JsonProtocolMessage(setDifficultyMessage));
        Logger.debug("Sent: " + setDifficultyMessage);
    }

    protected MinerSubscribeResponseMessage _createSubscribeResponseMessage(final Integer requestId, final Long clientId) {
        final ByteArray subscriptionId = _createRandomBytes(8);
        final ByteArray extraNonce = _getExtraNonce(clientId);

        final MinerSubscribeResponseMessage minerSubscribeResponseMessage = new MinerSubscribeResponseMessage(requestId);

        minerSubscribeResponseMessage.setSubscriptionId(subscriptionId);
        minerSubscribeResponseMessage.addSubscription(RequestMessage.ServerCommand.SET_DIFFICULTY);
        minerSubscribeResponseMessage.addSubscription(RequestMessage.ServerCommand.NOTIFY);
        minerSubscribeResponseMessage.setExtraNonce(extraNonce);
        minerSubscribeResponseMessage.setExtraNonce2ByteCount(_extraNonce2ByteCount);

        return minerSubscribeResponseMessage;
    }

    protected void _handleSubscribeMessage(final RequestMessage requestMessage, final JsonSocket jsonSocket) {
        final Integer requestId = requestMessage.getId();
        final Long clientId = jsonSocket.getId();
        final MinerSubscribeResponseMessage minerSubscribeResponseMessage = _createSubscribeResponseMessage(requestId, clientId);

        jsonSocket.write(new JsonProtocolMessage(minerSubscribeResponseMessage));
        Logger.debug("Sent: " + minerSubscribeResponseMessage);
    }

    protected void _handleAuthorizeMessage(final RequestMessage requestMessage, final JsonSocket socketConnection) {
        { // Respond with successful authorization...
            final Integer requestId = requestMessage.getId();
            final ResponseMessage responseMessage = new ResponseMessage(requestId);
            responseMessage.setResult(ResponseMessage.RESULT_TRUE);
            socketConnection.write(new JsonProtocolMessage(responseMessage));
            Logger.debug("Sent: " + responseMessage);
        }

        _sendWork(socketConnection, true);
    }

    protected void _handleSubmitMessage(final RequestMessage requestMessage, final JsonSocket jsonSocket) {
        // mining.submit("username", "job id", "ExtraNonce2", "nTime", "nOnce")

        final Integer messageId = requestMessage.getId();
        final Json messageParameters = requestMessage.getParameters();
        _handleSubmitMessage(messageId, messageParameters, jsonSocket);
    }

    protected Boolean _handleSubmitMessage(final Integer messageId, final Json messageParameters, final JsonSocket jsonSocket) {
        if (messageParameters.length() < 5) { return false; }

        final String workerUsername = messageParameters.getString(0);
        final String taskIdHex = messageParameters.getString(1);
        final String stratumNonce = messageParameters.getString(4);
        final String stratumExtraNonce2 = messageParameters.getString(2);
        final String stratumTimestamp = messageParameters.getString(3);

        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final ByteArray taskId = MutableByteArray.wrap(HexUtil.hexStringToByteArray(taskIdHex));
        final Long taskIdLong = ByteUtil.bytesToLong(taskId.getBytes());
        final StratumMineBlockTask mineBlockTask = _getMineBlockTask(taskIdLong);

        if (mineBlockTask == null) {
            if (jsonSocket != null) {
                final ResponseMessage responseMessage = new MinerSubmitBlockResponseMessage(messageId, ResponseMessage.Error.NOT_FOUND);
                jsonSocket.write(new JsonProtocolMessage(responseMessage));
                Logger.debug("Sent: " + responseMessage);
            }
            return false;
        }

        final Long baseShareDifficulty = _baseShareDifficulty;
        final Difficulty shareDifficulty = (_invertDifficultyEnabled ? Difficulty.BASE_DIFFICULTY.multiplyBy(baseShareDifficulty) : Difficulty.BASE_DIFFICULTY.divideBy(baseShareDifficulty));

        final BlockHeader blockHeader = mineBlockTask.assembleBlockHeader(stratumNonce, stratumExtraNonce2, stratumTimestamp);
        final Sha256Hash blockHash = blockHeader.getHash();
        Logger.debug(workerUsername + ": " + blockHash);

        if (! shareDifficulty.isSatisfiedBy(blockHash)) {
            Logger.warn("Share Difficulty not satisfied.");

            if (jsonSocket != null) {
                final ResponseMessage responseMessage = new MinerSubmitBlockResponseMessage(messageId, ResponseMessage.Error.LOW_DIFFICULTY);
                jsonSocket.write(new JsonProtocolMessage(responseMessage));
                Logger.debug("Sent: " + responseMessage);

                _setDifficulty(jsonSocket);
                _sendWork(jsonSocket, true);
            }
            return false;
        }

        if (blockHeader.isValid()) {
            final BlockHeaderDeflater blockHeaderDeflater = _masterInflater.getBlockHeaderDeflater();
            Logger.info("Valid Block: " + blockHeaderDeflater.toBytes(blockHeader));

            final BlockDeflater blockDeflater = _masterInflater.getBlockDeflater();
            final Block block = mineBlockTask.assembleBlock(stratumNonce, stratumExtraNonce2, stratumTimestamp);
            Logger.info(blockDeflater.toBytes(block));

            final BitcoinMiningRpcConnector bitcoinRpcConnector = _getBitcoinRpcConnector();
            final Boolean submitBlockResponse = bitcoinRpcConnector.submitBlock(block);
            if (! submitBlockResponse) {
                Logger.warn("Unable to submit block: " + blockHash);
            }

            _rebuildBlockTemplate();
            _deprecateMiningTasks();
            _resetHashCountMonitoring();
            _broadcastNewTask(true);

            final BlockFoundCallback blockFoundCallback = _blockFoundCallback;
            if (blockFoundCallback != null) {
                blockFoundCallback.run(block, workerUsername);
            }
        }

        final WorkerShareCallback workerShareCallback = _workerShareCallback;
        if (workerShareCallback != null) {
            final Long blockHeight = mineBlockTask.getBlockHeight();
            final Boolean wasAccepted = workerShareCallback.onNewWorkerShare(workerUsername, baseShareDifficulty, blockHeight, blockHash);
            if (! wasAccepted) {
                if (jsonSocket != null) {
                    final ResponseMessage responseMessage = new MinerSubmitBlockResponseMessage(messageId, ResponseMessage.Error.DUPLICATE);
                    jsonSocket.write(new JsonProtocolMessage(responseMessage));
                    Logger.debug("Sent: " + responseMessage);
                }
                return false;
            }
        }

        _shareCount.incrementAndGet();

        if (jsonSocket != null) {
            final ResponseMessage shareAcceptedMessage = new MinerSubmitBlockResponseMessage(messageId);
            jsonSocket.write(new JsonProtocolMessage(shareAcceptedMessage));
            Logger.debug("Sent: " + shareAcceptedMessage);
        }

        nanoTimer.stop();
        Logger.trace("Accepted share in " + nanoTimer.getMillisecondsElapsed() + "ms.");

        return true;
    }

    protected StratumServerSocket.SocketEventCallback _createSocketEventCallback() {
        return new StratumServerSocket.SocketEventCallback() {
            @Override
            public void onConnect(final JsonSocket jsonSocket) {
                Logger.debug("Node connected: " + jsonSocket.getIp() + ":" + jsonSocket.getPort());
                final Long connectionId = jsonSocket.getId();
                _connections.put(connectionId, jsonSocket);

                final Runnable messageReceivedCallback;
                if (PROXY_VIABTC) {
                    messageReceivedCallback = new ProxyViaBtcMessageReceivedCallback(jsonSocket, _threadPool);
                }
                else {
                    messageReceivedCallback = new Runnable() {
                        @Override
                        public void run() {
                            final JsonProtocolMessage jsonProtocolMessage = jsonSocket.popMessage();
                            final Json message = jsonProtocolMessage.getMessage();

                            { // Handle Request Messages...
                                final RequestMessage requestMessage = RequestMessage.parse(message);
                                if (requestMessage != null) {
                                    Logger.trace("Received: " + requestMessage);

                                    if (requestMessage.isCommand(RequestMessage.ClientCommand.SUBSCRIBE)) {
                                        _handleSubscribeMessage(requestMessage, jsonSocket);
                                        _setDifficulty(jsonSocket); // Redundant difficulty message due to miners ignoring the difficulty sent in subscribe response...
                                    }
                                    else if (requestMessage.isCommand(RequestMessage.ClientCommand.AUTHORIZE)) {
                                        _handleAuthorizeMessage(requestMessage, jsonSocket);
                                        _setDifficulty(jsonSocket);
                                    }
                                    else if (requestMessage.isCommand(RequestMessage.ClientCommand.SUBMIT)) {
                                        _handleSubmitMessage(requestMessage, jsonSocket);
                                    }
                                    else {
                                        Logger.debug("Unrecognized Message: " + requestMessage.getCommand());
                                    }
                                }
                            }

                            { // Handle Response Messages...
                                final ResponseMessage responseMessage = ResponseMessage.parse(message);
                                if (responseMessage != null) { Logger.debug(responseMessage); }
                            }
                        }
                    };
                }

                jsonSocket.setMessageReceivedCallback(messageReceivedCallback);

                jsonSocket.beginListening();
            }

            @Override
            public void onDisconnect(final JsonSocket jsonSocket) {
                Logger.debug("Node disconnected: " + jsonSocket.getIp() + ":" + jsonSocket.getPort());

                final Long connectionId = jsonSocket.getId();
                _connections.remove(connectionId);
            }
        };
    }

    public BitcoinCoreStratumServer(final StratumProperties stratumProperties, final ThreadPool threadPool, final MasterInflater masterInflater) {
        this(stratumProperties, threadPool, masterInflater, new StratumServerSocket(stratumProperties.getPort(), threadPool));
    }

    public BitcoinCoreStratumServer(final StratumProperties stratumProperties, final ThreadPool threadPool, final MasterInflater masterInflater, final StratumServerSocket stratumServerSocket) {
        _masterInflater = masterInflater;
        _stratumProperties = stratumProperties;
        _threadPool = threadPool;
        _seedBytes = _createRandomBytes(_extraNonceByteCount);

        _stratumServerSocket = stratumServerSocket;
        if (stratumServerSocket != null) {
            final StratumServerSocket.SocketEventCallback socketEventCallback = _createSocketEventCallback();
            stratumServerSocket.setSocketEventCallback(socketEventCallback);
        }

        _rebuildBlockTemplateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (! Thread.interrupted()) {
                    try { Thread.sleep(15000); } catch (final InterruptedException exception) { break; }

                    _rebuildBlockTemplate();
                    _deprecateMiningTasks();
                    _broadcastNewTask(false);
                }
            }
        });
        _rebuildBlockTemplateThread.setName("StratumServer - Block Template Thread");
        _rebuildBlockTemplateThread.setDaemon(true);
        _rebuildBlockTemplateThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable exception) {
                Logger.debug(exception);
            }
        });
    }

    @Override
    public void start() {
        _timeSinceLastTemplateValidation.start();
        _rebuildBlockTemplate();

        final BitcoinMiningRpcConnector notificationBitcoinRpcConnector = _getBitcoinRpcConnector();
        notificationBitcoinRpcConnector.subscribeToNotifications(new RpcNotificationCallback() {
            @Override
            public void onNewNotification(final RpcNotification rpcNotification) {
                final Boolean useBlockHashAsAnnouncementHook = notificationBitcoinRpcConnector.supportsNotification(RpcNotificationType.BLOCK_HASH);

                switch (rpcNotification.rpcNotificationType) {
                    case BLOCK: {
                        if (useBlockHashAsAnnouncementHook) {
                            break;
                        }
                    } // Fallthrough...
                    case BLOCK_HASH: {
                        Logger.info("New Block received.");
                        _rebuildBlockTemplate();
                        _abandonMiningTasks();
                        _resetHashCountMonitoring();
                        _broadcastNewTask(true);
                    }
                }
            }
        });

        if (_stratumServerSocket != null) {
            _stratumServerSocket.start();
        }

        Logger.info("[Server Online]");

        final Address address = _getCoinbaseAddress();
        Logger.debug("Coinbase Address: " + address.toBase58CheckEncoded());

        _rebuildBlockTemplateThread.start();
    }

    @Override
    public void stop() {
        _rebuildBlockTemplateThread.interrupt();
        try { _rebuildBlockTemplateThread.join(15000L); } catch (final Exception exception) { }

        if (_stratumServerSocket != null) {
            _stratumServerSocket.stop();
        }
    }

    @Override
    public Block getPrototypeBlock() {
        final StratumMineBlockTask mineBlockTask = _buildNewMiningTask(0L);
        if (mineBlockTask == null) { return null; }

        return mineBlockTask.assembleBlockTemplate(_extraNonceByteCount, _extraNonce2ByteCount);
    }

    @Override
    public Long getBlockHeight() {
        final BlockTemplate blockTemplate = _blockTemplate;
        if (blockTemplate == null) { return 0L; }

        return blockTemplate.getBlockHeight();
    }

    @Override
    public Long getShareDifficulty() {
        return _baseShareDifficulty;
    }

    @Override
    public void setShareDifficulty(final Long baseShareDifficulty) {
        _baseShareDifficulty = baseShareDifficulty;
        _resetHashCountMonitoring();
        for (final JsonSocket jsonSocket : _connections.values()) {
            _setDifficulty(jsonSocket);
        }
        _broadcastNewTask(true);
    }

    @Override
    public Long getShareCount() {
        return _shareCount.get();
    }

    @Override
    public Long getStartTimeInSeconds() {
        return _startTime;
    }

    @Override
    public Long getCurrentBlockStartTimeInSeconds() { return _currentBlockStartTime; }

    @Override
    public void setWorkerShareCallback(final WorkerShareCallback workerShareCallback) {
        _workerShareCallback = workerShareCallback;
    }

    @Override
    public void setBlockFoundCallback(final BlockFoundCallback blockFoundCallback) {
        _blockFoundCallback = blockFoundCallback;
    }

    @Override
    public void invertDifficulty(final Boolean shouldInvertDifficulty) {
        _invertDifficultyEnabled = shouldInvertDifficulty;
    }

    @Override
    public void setCoinbaseAddress(final Address address) {
        _coinbaseAddress = address;
    }

    public Boolean submitShare(final Json messageParameters) {
        return _handleSubmitMessage(null, messageParameters, null);
    }

    public MinerSubscribeResponseMessage subscribeMiner(final Long minerId) {
        return _createSubscribeResponseMessage(null, minerId);
    }

    public MinerNotifyMessage getMinerWork(final Long minerId, final Boolean shouldAbandonOldJobs) {
        final StratumMineBlockTask mineBlockTask = _buildNewMiningTask(minerId);
        if (mineBlockTask == null) { return null; }

        final Long mineBlockTaskId = mineBlockTask.getId();
        _mineBlockTasks.put(mineBlockTaskId, mineBlockTask);

        return mineBlockTask.createRequest(shouldAbandonOldJobs);
    }
}