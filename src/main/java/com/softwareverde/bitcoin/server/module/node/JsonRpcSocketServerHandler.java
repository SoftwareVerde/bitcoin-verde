package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.database.*;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.ReadOnlyLocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.socket.JsonProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.network.socket.JsonSocketServer;
import com.softwareverde.util.Container;
import com.softwareverde.util.DateUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.util.Map;

public class JsonRpcSocketServerHandler implements JsonSocketServer.SocketConnectedCallback {
    protected static final String CORRUPTED_BLOCK_ERROR_MESSAGE = "Could not inflate Block; it may be corrupted.";
    protected static final String BLOCK_HEADER_FALLBACK_ERROR_MESSAGE = "Block not synchronized yet; falling back to BlockHeader.";

    public interface ShutdownHandler {
        Boolean shutdown();
    }

    public interface NodeHandler {
        Boolean addNode(String host, Integer port);
        List<BitcoinNode> getNodes();
    }

    public interface QueryAddressHandler {
        Long getBalance(Address address);
        List<Transaction> getAddressTransactions(Address address);
    }

    public interface ThreadPoolInquisitor {
        Integer getQueueCount();
        Integer getActiveThreadCount();
        Integer getMaxThreadCount();
    }

    public interface ServiceInquisitor {
        Map<String, String> getServiceStatuses();
    }

    public static class StatisticsContainer {
        public Container<Float> averageBlockHeadersPerSecond;
        public Container<Float> averageBlocksPerSecond;
        public Container<Float> averageTransactionsPerSecond;
    }

    protected final Environment _environment;
    protected final ReadOnlyLocalDatabaseManagerCache _databaseManagerCache;
    protected final SynchronizationStatus _synchronizationStatus;
    protected final Container<Float> _averageBlocksPerSecond;
    protected final Container<Float> _averageBlockHeadersPerSecond;
    protected final Container<Float> _averageTransactionsPerSecond;

    protected ShutdownHandler _shutdownHandler = null;
    protected NodeHandler _nodeHandler = null;
    protected QueryAddressHandler _queryAddressHandler = null;
    protected ThreadPoolInquisitor _threadPoolInquisitor = null;
    protected ServiceInquisitor _serviceInquisitor = null;

    protected static void _addMetadataForBlockHeaderToJson(final BlockId blockId, final Json blockJson, final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, databaseManagerCache);
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, databaseManagerCache);

        { // Include Extra Block Metadata...
            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
            final Integer transactionCount = transactionDatabaseManager.getTransactionCount(blockId);

            blockJson.put("height", blockHeight);
            blockJson.put("reward", BlockHeader.calculateBlockReward(blockHeight));
            blockJson.put("byteCount", blockHeaderDatabaseManager.getBlockByteCount(blockId));
            blockJson.put("transactionCount", transactionCount);
        }
    }

    protected static void _addMetadataForTransactionToJson(final Transaction transaction, final Json transactionJson, final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) throws DatabaseException {
        final Sha256Hash transactionHash = transaction.getHash();
        final String transactionHashString = transactionHash.toString();

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, databaseManagerCache);
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, databaseManagerCache);
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection, databaseManagerCache);
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        final ByteArray transactionData = transactionDeflater.toBytes(transaction);
        transactionJson.put("byteCount", transactionData.getByteCount());

        Long transactionFee = 0L;

        { // Include Block hashes which include this transaction...
            final Json blockHashesJson = new Json(true);
            final List<BlockId> blockIds = transactionDatabaseManager.getBlockIds(transactionHash);
            for (final BlockId blockId : blockIds) {
                final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);
                blockHashesJson.add(blockHash);
            }
            transactionJson.put("blocks", blockHashesJson);
        }

        { // Process TransactionInputs...
            Integer transactionInputIndex = 0;
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final TransactionOutputId previousTransactionOutputId;
                {
                    final Sha256Hash previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                    if (previousOutputTransactionHash != null) {
                        final TransactionOutputIdentifier previousTransactionOutputIdentifier = new TransactionOutputIdentifier(previousOutputTransactionHash, transactionInput.getPreviousOutputIndex());
                        previousTransactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(previousTransactionOutputIdentifier);

                        if (previousTransactionOutputId == null) {
                            Logger.log("NOTICE: Error calculating fee for Transaction: " + transactionHashString);
                        }
                    }
                    else {
                        previousTransactionOutputId = null;
                    }
                }

                if (previousTransactionOutputId == null) {
                    transactionFee = null; // Abort calculating the transaction fee but continue with the rest of the processing...
                }

                final TransactionOutput previousTransactionOutput = ( previousTransactionOutputId != null ? transactionOutputDatabaseManager.getTransactionOutput(previousTransactionOutputId) : null );
                final Long previousTransactionOutputAmount = ( previousTransactionOutput != null ? previousTransactionOutput.getAmount() : null );

                if (transactionFee != null) {
                    transactionFee += Util.coalesce(previousTransactionOutputAmount);
                }

                final String addressString;
                {
                    if (previousTransactionOutput != null) {
                        final LockingScript lockingScript = previousTransactionOutput.getLockingScript();
                        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
                        final Address address = scriptPatternMatcher.extractAddress(scriptType, lockingScript);
                        addressString = address.toBase58CheckEncoded();
                    }
                    else {
                        addressString = null;
                    }
                }

                final Json transactionInputJson = transactionJson.get("inputs").get(transactionInputIndex);
                transactionInputJson.put("previousTransactionAmount", previousTransactionOutputAmount);
                transactionInputJson.put("address", addressString);

                transactionInputIndex += 1;
            }
        }

        { // Process TransactionOutputs...
            int transactionOutputIndex = 0;
            for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
                if (transactionFee != null) {
                    transactionFee -= transactionOutput.getAmount();
                }

                { // Add extra TransactionOutput json fields...
                    final String addressString;
                    {
                        final LockingScript lockingScript = transactionOutput.getLockingScript();
                        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
                        final Address address = scriptPatternMatcher.extractAddress(scriptType, lockingScript);
                        addressString = (address != null ? address.toBase58CheckEncoded() : null);
                    }

                    final Json transactionOutputJson = transactionJson.get("outputs").get(transactionOutputIndex);
                    transactionOutputJson.put("address", addressString);
                }

                transactionOutputIndex += 1;
            }
        }

        transactionJson.put("fee", transactionFee);
    }

    public JsonRpcSocketServerHandler(final Environment environment, final SynchronizationStatus synchronizationStatus, final StatisticsContainer statisticsContainer) {
        _environment = environment;
        _databaseManagerCache = new ReadOnlyLocalDatabaseManagerCache(environment.getMasterDatabaseManagerCache());
        _synchronizationStatus = synchronizationStatus;

        _averageBlockHeadersPerSecond = statisticsContainer.averageBlockHeadersPerSecond;
        _averageBlocksPerSecond = statisticsContainer.averageBlocksPerSecond;
        _averageTransactionsPerSecond = statisticsContainer.averageTransactionsPerSecond;
    }

    protected Long _calculateBlockHeight(final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);

        final BlockId blockId = blockDatabaseManager.getHeadBlockId();
        if (blockId == null) { return 0L; }

        return blockHeaderDatabaseManager.getBlockHeight(blockId);
    }

    protected Long _calculateBlockHeaderHeight(final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);

        final BlockId blockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
        if (blockId == null) { return 0L; }

        return blockHeaderDatabaseManager.getBlockHeight(blockId);
    }

    // Requires GET:    [blockHeight=null], [maxBlockCount=10]
    protected void _getBlockHeaders(final Json parameters, final Json response) {
        final EmbeddedMysqlDatabase database = _environment.getDatabase();
        try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);

            final Long startingBlockHeight;
            {
                final String blockHeightString = parameters.getString("blockHeight");
                if (Util.isInt(blockHeightString)) {
                    startingBlockHeight = parameters.getLong("blockHeight");
                }
                else {
                    final BlockId headBlockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
                    startingBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);
                }
            }

            final Integer maxBlockCount;
            if (parameters.hasKey("maxBlockCount")) {
                maxBlockCount = parameters.getInteger("maxBlockCount");
            }
            else {
                maxBlockCount = 10;
            }

            final Json blockHeadersJson = new Json(true);
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT id FROM blocks WHERE block_height <= ? ORDER BY block_height DESC LIMIT " + maxBlockCount)
                    .setParameter(startingBlockHeight)
            );
            for (final Row row : rows) {
                final BlockId blockId = BlockId.wrap(row.getLong("id"));

                final Json blockJson;
                {
                    final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);
                    blockJson = blockHeader.toJson();
                }

                _addMetadataForBlockHeaderToJson(blockId, blockJson, databaseConnection, _databaseManagerCache);

                blockHeadersJson.add(blockJson);
            }

            response.put("blockHeaders", blockHeadersJson);

            response.put("wasSuccess", 1);
        }
        catch (final Exception exception) {
            response.put("wasSuccess", 0);
            response.put("errorMessage", exception.getMessage());
        }
    }

    protected void _getBlockHeader(final Json parameters, final Json response) {
        if ( (! parameters.hasKey("hash")) && (! parameters.hasKey("blockHeight")) ) {
            response.put("errorMessage", "Missing parameters. Required: [hash|blockHeight]");
            return;
        }

        final Boolean shouldReturnRawBlockData = parameters.getBoolean("rawFormat");

        final Boolean blockHeightWasProvided = parameters.hasKey("blockHeight");
        final Long paramBlockHeight = parameters.getLong("blockHeight");
        final String paramBlockHashString = parameters.getString("hash");
        final Sha256Hash paramBlockHash = Sha256Hash.fromHexString(paramBlockHashString);

        if ( (paramBlockHash == null) && (! blockHeightWasProvided) ) {
            response.put("errorMessage", "Invalid block hash: " + paramBlockHashString);
            return;
        }

        final EmbeddedMysqlDatabase database = _environment.getDatabase();
        try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

            final BlockId blockId;
            final Sha256Hash blockHash;
            if (blockHeightWasProvided) {
                final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseManagerCache);
                final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
                blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(headBlockchainSegmentId, paramBlockHeight);
                if (blockId == null) {
                    response.put("errorMessage", "Block not found at height: " + paramBlockHeight);
                    return;
                }

                blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);
            }
            else {
                blockHash = paramBlockHash;
                blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);

                if (blockId == null) {
                    response.put("errorMessage", "Block not found: " + paramBlockHashString);
                    return;
                }
            }

            if (shouldReturnRawBlockData) {
                final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
                final Block block = blockDatabaseManager.getBlock(blockId);
                if (block == null) {
                    response.put("errorMessage", CORRUPTED_BLOCK_ERROR_MESSAGE);
                    return;
                }

                final ByteArray blockData = blockHeaderDeflater.toBytes(block);

                response.put("block", blockData);
            }
            else {
                final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);
                if (blockHeader == null) {
                    response.put("errorMessage", "Could not inflate Block; it may be corrupted.");
                    return;
                }

                final Json blockJson = blockHeader.toJson();
                _addMetadataForBlockHeaderToJson(blockId, blockJson, databaseConnection, _databaseManagerCache);
                response.put("block", blockJson);
            }

            response.put("wasSuccess", 1);
        }
        catch (final Exception exception) {
            response.put("wasSuccess", 0);
            response.put("errorMessage", exception.getMessage());
        }
    }

    protected void _getBlock(final Json parameters, final Json response) {
        if ( (! parameters.hasKey("hash")) && (! parameters.hasKey("blockHeight")) ) {
            response.put("errorMessage", "Missing parameters. Required: [hash|blockHeight]");
            return;
        }

        final Boolean shouldReturnRawBlockData = parameters.getBoolean("rawFormat");

        final Boolean blockHeightWasProvided = parameters.hasKey("blockHeight");
        final Long paramBlockHeight = parameters.getLong("blockHeight");
        final String paramBlockHashString = parameters.getString("hash");
        final Sha256Hash paramBlockHash = Sha256Hash.fromHexString(paramBlockHashString);

        if ( (paramBlockHash == null) && (! blockHeightWasProvided) ) {
            response.put("errorMessage", "Invalid block hash: " + paramBlockHashString);
            return;
        }

        final EmbeddedMysqlDatabase database = _environment.getDatabase();
        try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);

            final BlockId blockId;
            final Sha256Hash blockHash;
            if (blockHeightWasProvided) {
                final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseManagerCache);
                final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
                blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(headBlockchainSegmentId, paramBlockHeight);
                if (blockId == null) {
                    response.put("errorMessage", "Block not found at height: " + paramBlockHeight);
                    return;
                }

                blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);
            }
            else {
                blockHash = paramBlockHash;
                blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);

                if (blockId == null) {
                    response.put("errorMessage", "Block not found: " + paramBlockHashString);
                    return;
                }
            }

            final Boolean blockExists = blockDatabaseManager.blockHeaderHasTransactions(blockHash);
            if (! blockExists) {
                response.put("errorMessage", "Block not synchronized: " + blockHash.toString());
                return;
            }

            if (shouldReturnRawBlockData) {
                final Integer transactionCount = transactionDatabaseManager.getTransactionCount(blockId);

                final ByteArray blockData;
                final Boolean isFullBlock = (transactionCount > 0);
                if (isFullBlock) {
                    final BlockDeflater blockDeflater = new BlockDeflater();
                    final Block block = blockDatabaseManager.getBlock(blockId);
                    if (block == null) {
                        response.put("errorMessage", CORRUPTED_BLOCK_ERROR_MESSAGE);
                        return;
                    }

                    blockData = blockDeflater.toBytes(block);
                }
                else {
                    final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);
                    if (blockHeader == null) {
                        response.put("errorMessage", CORRUPTED_BLOCK_ERROR_MESSAGE);
                        return;
                    }

                    response.put("errorMessage", BLOCK_HEADER_FALLBACK_ERROR_MESSAGE);
                    final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
                    blockData = blockHeaderDeflater.toBytes(blockHeader);
                }

                response.put("block", blockData);
            }
            else {
                final Block block = blockDatabaseManager.getBlock(blockId);
                if (block == null) {
                    response.put("errorMessage", CORRUPTED_BLOCK_ERROR_MESSAGE);
                    return;
                }

                final List<Transaction> blockTransactions = block.getTransactions();

                final Json blockJson = block.toJson();

                final Json transactionsJson = blockJson.get("transactions");
                for (int i = 0; i < transactionsJson.length(); ++i) {
                    final Json transactionJson = transactionsJson.get(i);
                    final Transaction transaction = blockTransactions.get(i);
                    _addMetadataForTransactionToJson(transaction, transactionJson, databaseConnection, _databaseManagerCache);
                }

                _addMetadataForBlockHeaderToJson(blockId, blockJson, databaseConnection, _databaseManagerCache);

                response.put("block", blockJson);
            }

            response.put("wasSuccess", 1);
        }
        catch (final Exception exception) {
            response.put("wasSuccess", 0);
            response.put("errorMessage", exception.getMessage());
        }
    }

    protected void _getTransaction(final Json parameters, final Json response) {
        if (! parameters.hasKey("hash")) {
            response.put("errorMessage", "Missing parameters. Required: hash");
            return;
        }

        final Boolean shouldReturnRawTransactionData = parameters.getBoolean("rawFormat");

        final String transactionHashString = parameters.getString("hash");
        final Sha256Hash transactionHash = Sha256Hash.fromHexString(transactionHashString);

        if (transactionHash == null) {
            response.put("errorMessage", "Invalid transaction hash: " + transactionHashString);
            return;
        }

        final EmbeddedMysqlDatabase database = _environment.getDatabase();
        try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);

            final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
            if (transactionId == null) {
                response.put("errorMessage", "Transaction not found: " + transactionHashString);
                return;
            }

            final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);

            if (shouldReturnRawTransactionData) {
                final TransactionDeflater transactionDeflater = new TransactionDeflater();
                final ByteArray transactionData = transactionDeflater.toBytes(transaction);
                response.put("transaction", HexUtil.toHexString(transactionData.getBytes()));
            }
            else {
                final Json transactionJson = transaction.toJson();

                _addMetadataForTransactionToJson(transaction, transactionJson, databaseConnection, _databaseManagerCache);

                response.put("transaction", transactionJson);
            }
            response.put("wasSuccess", 1);
        }
        catch (final Exception exception) {
            response.put("wasSuccess", 0);
            response.put("errorMessage", exception.getMessage());
        }
    }

    protected void _queryBlockHeight(final Json response) {
        final EmbeddedMysqlDatabase database = _environment.getDatabase();
        try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
            final Long blockHeight = _calculateBlockHeight(databaseConnection);
            final Long blockHeaderHeight = _calculateBlockHeaderHeight(databaseConnection);

            response.put("wasSuccess", 1);
            response.put("blockHeight", blockHeight);
            response.put("blockHeaderHeight", blockHeaderHeight);
        }
        catch (final Exception exception) {
            response.put("wasSuccess", 0);
            response.put("errorMessage", exception.getMessage());
        }
    }

    protected void _queryStatus(final Json response) {
        final EmbeddedMysqlDatabase database = _environment.getDatabase();
        try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);

            final Long blockTimestampInSeconds;
            {
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
                final Sha256Hash lastKnownBlockHash = blockDatabaseManager.getHeadBlockHash();
                if (lastKnownBlockHash == null) {
                    blockTimestampInSeconds = MedianBlockTime.GENESIS_BLOCK_TIMESTAMP;
                }
                else {
                    final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(lastKnownBlockHash);
                    final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);
                    blockTimestampInSeconds = blockHeader.getTimestamp();
                }
            }

            final Long blockHeaderTimestampInSeconds;
            {
                final Sha256Hash lastKnownHeaderHash = blockHeaderDatabaseManager.getHeadBlockHeaderHash();
                if (lastKnownHeaderHash == null) {
                    blockHeaderTimestampInSeconds = MedianBlockTime.GENESIS_BLOCK_TIMESTAMP;
                }
                else {
                    final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(lastKnownHeaderHash);
                    final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);
                    blockHeaderTimestampInSeconds = blockHeader.getTimestamp();
                }
            }

            response.put("wasSuccess", 1);

            { // Status
                response.put("status", _synchronizationStatus.getState());
            }

            { // Statistics
                final Long blockHeight = _calculateBlockHeight(databaseConnection);
                final Long blockHeaderHeight = _calculateBlockHeaderHeight(databaseConnection);

                final Json statisticsJson = new Json();
                statisticsJson.put("blockHeaderHeight", blockHeaderHeight);
                statisticsJson.put("blockHeadersPerSecond", _averageBlockHeadersPerSecond.value);
                statisticsJson.put("blockHeaderDate", DateUtil.Utc.timestampToDatetimeString(blockHeaderTimestampInSeconds * 1000));
                statisticsJson.put("blockHeaderTimestamp", blockHeaderTimestampInSeconds);

                statisticsJson.put("blockHeight", blockHeight);
                statisticsJson.put("blocksPerSecond", _averageBlocksPerSecond.value);
                statisticsJson.put("blockDate", DateUtil.Utc.timestampToDatetimeString(blockTimestampInSeconds * 1000));
                statisticsJson.put("blockTimestamp", blockTimestampInSeconds);

                statisticsJson.put("transactionsPerSecond", _averageTransactionsPerSecond.value);
                response.put("statistics", statisticsJson);
            }

            { // Server Load
                final Json serverLoadJson = new Json();
                final ThreadPoolInquisitor threadPoolInquisitor = _threadPoolInquisitor;
                serverLoadJson.put("threadPoolQueueCount",          (threadPoolInquisitor != null ? threadPoolInquisitor.getQueueCount() : null));
                serverLoadJson.put("threadPoolActiveThreadCount",   (threadPoolInquisitor != null ? threadPoolInquisitor.getActiveThreadCount() : null));
                serverLoadJson.put("threadPoolMaxThreadCount",      (threadPoolInquisitor != null ? threadPoolInquisitor.getMaxThreadCount() : null));

                final Runtime runtime = Runtime.getRuntime();
                serverLoadJson.put("jvmMemoryUsageByteCount", (runtime.totalMemory() - runtime.freeMemory()));
                serverLoadJson.put("jvmMemoryMaxByteCount", runtime.maxMemory());

                response.put("serverLoad", serverLoadJson);
            }

            { // Service Statuses
                final Json servicesStatusJson = new Json();
                final ServiceInquisitor serviceInquisitor = _serviceInquisitor;
                if (serviceInquisitor != null) {
                    final Map<String, String> serviceStatuses = serviceInquisitor.getServiceStatuses();
                    for (final String serviceName : serviceStatuses.keySet()) {
                        final String serviceStatus = serviceStatuses.get(serviceName);
                        servicesStatusJson.put(serviceName, serviceStatus);
                    }
                }
                response.put("serviceStatuses", servicesStatusJson);
            }
        }
        catch (final Exception exception) {
            response.put("wasSuccess", 0);
            response.put("errorMessage", exception.getMessage());
        }
    }

    protected void _queryBalance(final Json parameters, final Json response) {
        final QueryAddressHandler queryAddressHandler = _queryAddressHandler;
        if (queryAddressHandler == null) {
            response.put("errorMessage", "Operation not supported.");
            return;
        }

        if (! parameters.hasKey("address")) {
            response.put("errorMessage", "Missing parameters. Required: address");
            return;
        }

        final String addressString = parameters.getString("address");
        final AddressInflater addressInflater = new AddressInflater();
        final Address address = addressInflater.fromBase58Check(addressString);

        if (address == null) {
            response.put("errorMessage", "Invalid address.");
            return;
        }

        final Long balance = queryAddressHandler.getBalance(address);

        if (balance == null) {
            response.put("errorMessage", "Unable to determine balance.");
            return;
        }

        response.put("balance", balance);
        response.put("wasSuccess", 1);
    }

    protected void _queryAddressTransactions(final Json parameters, final Json response) {
        final QueryAddressHandler queryAddressHandler = _queryAddressHandler;
        if (queryAddressHandler == null) {
            response.put("errorMessage", "Operation not supported.");
            return;
        }

        if (! parameters.hasKey("address")) {
            response.put("errorMessage", "Missing parameters. Required: address");
            return;
        }

        final String addressString = parameters.getString("address");
        final AddressInflater addressInflater = new AddressInflater();
        final Address address = addressInflater.fromBase58Check(addressString);

        if (address == null) {
            response.put("errorMessage", "Invalid address: " + addressString);
            return;
        }

        final List<Transaction> addressTransactions = queryAddressHandler.getAddressTransactions(address);

        if (addressTransactions == null) {
            response.put("errorMessage", "Unable to determine address transactions.");
            return;
        }

        final Json transactionsJson = new Json(true);

        final EmbeddedMysqlDatabase database = _environment.getDatabase();
        try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
            for (final Transaction transaction : addressTransactions) {
                final Json transactionJson = transaction.toJson();
                _addMetadataForTransactionToJson(transaction, transactionJson, databaseConnection, _databaseManagerCache);
                transactionsJson.add(transactionJson);
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            response.put("errorMessage", "Error loading transaction metadata.");
            return;
        }

        response.put("transactions", transactionsJson);
        response.put("wasSuccess", 1);
    }

    protected void _shutdown(final Json parameters, final Json response) {
        final ShutdownHandler shutdownHandler = _shutdownHandler;
        if (shutdownHandler == null) {
            response.put("errorMessage", "Operation not supported.");
            return;
        }

        final Boolean wasSuccessful = shutdownHandler.shutdown();
        response.put("wasSuccess", (wasSuccessful ? 1 : 0));
    }

    protected void _addNode(final Json parameters, final Json response) {
        final NodeHandler nodeHandler = _nodeHandler;
        if (nodeHandler == null) {
            response.put("errorMessage", "Operation not supported.");
            return;
        }

        if ((! parameters.hasKey("host")) || (! parameters.hasKey("port"))) {
            response.put("errorMessage", "Missing parameters. Required: host, port");
            return;
        }

        final String host = parameters.getString("host");
        final Integer port = parameters.getInteger("port");

        final Boolean wasSuccessful = nodeHandler.addNode(host, port);
        response.put("wasSuccess", (wasSuccessful ? 1 : 0));
    }

    protected void _nodeStatus(final Json response) {
        final NodeHandler nodeHandler = _nodeHandler;
        if (nodeHandler == null) {
            response.put("errorMessage", "Operation not supported.");
            return;
        }

        final Json nodeListJson = new Json();

        final List<BitcoinNode> nodes = _nodeHandler.getNodes();
        for (final BitcoinNode node : nodes) {
            final NodeIpAddress nodeIpAddress = node.getRemoteNodeIpAddress();
            if (nodeIpAddress == null) { continue; }

            final Json nodeJson = new Json();
            nodeJson.put("host", nodeIpAddress.getIp());
            nodeJson.put("port", nodeIpAddress.getPort());
            nodeJson.put("userAgent", node.getUserAgent());
            nodeJson.put("initializationTimestamp", (node.getInitializationTimestamp() / 1000L));
            nodeJson.put("lastMessageReceivedTimestamp", (node.getLastMessageReceivedTimestamp() / 1000L));
            nodeJson.put("networkOffset", node.getNetworkTimeOffset());

            final NodeIpAddress localNodeIpAddress = node.getLocalNodeIpAddress();
            nodeJson.put("localHost", (localNodeIpAddress != null ? localNodeIpAddress.getIp() : null));
            nodeJson.put("localPort", (localNodeIpAddress != null ? localNodeIpAddress.getPort() : null));

            nodeJson.put("handshakeIsComplete", (node.handshakeIsComplete() ? 1 : 0));
            nodeJson.put("id", node.getId());

            final Json featuresJson = new Json(false);
            for (final NodeFeatures.Feature nodeFeature : NodeFeatures.Feature.values()) {
                final Boolean hasFeatureEnabled = (node.hasFeatureEnabled(nodeFeature));
                final String featureKey = nodeFeature.name();
                if (hasFeatureEnabled == null) {
                    featuresJson.put(featureKey, null);
                }
                else {
                    featuresJson.put(featureKey, (hasFeatureEnabled ? 1 : 0));
                }
            }
            featuresJson.put("THIN_PROTOCOL_ENABLED", (node.supportsExtraThinBlocks() ? 1 : 0));
            nodeJson.put("features", featuresJson);

            nodeListJson.add(nodeJson);
        }

        response.put("nodes", nodeListJson);
        response.put("wasSuccess", 1);
    }

    public void setShutdownHandler(final ShutdownHandler shutdownHandler) {
        _shutdownHandler = shutdownHandler;
    }

    public void setNodeHandler(final NodeHandler nodeHandler) {
        _nodeHandler = nodeHandler;
    }

    public void setQueryAddressHandler(final QueryAddressHandler queryAddressHandler) {
        _queryAddressHandler = queryAddressHandler;
    }

    public void setThreadPoolInquisitor(final ThreadPoolInquisitor threadPoolInquisitor) {
        _threadPoolInquisitor = threadPoolInquisitor;
    }

    public void setServiceInquisitor(final ServiceInquisitor serviceInquisitor) {
        _serviceInquisitor = serviceInquisitor;
    }

    @Override
    public void run(final JsonSocket socketConnection) {
        Logger.log("New Connection: " + socketConnection);
        socketConnection.setMessageReceivedCallback(new Runnable() {
            @Override
            public void run() {
                final Json message = socketConnection.popMessage().getMessage();
                Logger.log("Message received: "+ message);

                final String method = message.getString("method");
                final String query = message.getString("query");

                final Json response = new Json();
                response.put("method", "RESPONSE");
                response.put("wasSuccess", 0);
                response.put("errorMessage", null);

                final Json parameters = message.get("parameters");

                switch (method.toUpperCase()) {
                    case "GET": {
                        switch (query.toUpperCase()) {
                            case "BLOCK_HEADERS": {
                                _getBlockHeaders(parameters, response);
                            } break;

                            case "BLOCK": {
                                _getBlock(parameters, response);
                            } break;

                            case "BLOCK_HEADER": {
                                _getBlockHeader(parameters, response);
                            } break;

                            case "TRANSACTION": {
                                _getTransaction(parameters, response);
                            } break;

                            case "BLOCK_HEIGHT": {
                                _queryBlockHeight(response);
                            } break;

                            case "STATUS": {
                                _queryStatus(response);
                            } break;

                            case "NODES": {
                                _nodeStatus(response);
                            } break;

                            case "BALANCE": {
                                _queryBalance(parameters, response);
                            } break;

                            case "ADDRESS": {
                                _queryAddressTransactions(parameters, response);
                            } break;

                            default: {
                                response.put("errorMessage", "Invalid command: " + method + "/" + query);
                            } break;
                        }
                    } break;

                    case "POST": {
                        switch (query.toUpperCase()) {
                            case "SHUTDOWN": {
                                _shutdown(parameters, response);
                            } break;

                            case "ADD_NODE": {
                                _addNode(parameters, response);
                            } break;

                            default: {
                                response.put("errorMessage", "Invalid command: " + method + "/" + query);
                            } break;
                        }
                    } break;

                    default: {
                        response.put("errorMessage", "Invalid method: " + method);
                    } break;
                }

                final String responseString = response.toString();
                final String truncatedResponseString = responseString.substring(0, Math.min(256, responseString.length()));
                Logger.log("Writing: " + truncatedResponseString + (responseString.length() > 256 ? "..." : ""));
                socketConnection.write(new JsonProtocolMessage(response));
            }
        });
        socketConnection.beginListening();
    }
}
