package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

public class MetadataHandler implements NodeRpcHandler.MetadataHandler {

    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;

    protected static void _addMetadataForBlockHeaderToJson(final Sha256Hash blockHash, final Json blockJson, final DatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, databaseManagerCache);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, databaseManagerCache);
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, databaseManagerCache);

        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);

        { // Include Extra Block Metadata...
            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
            final Integer transactionCount = blockDatabaseManager.getTransactionCount(blockId);

            blockJson.put("height", blockHeight);
            blockJson.put("reward", BlockHeader.calculateBlockReward(blockHeight));
            blockJson.put("byteCount", blockHeaderDatabaseManager.getBlockByteCount(blockId));
            blockJson.put("transactionCount", transactionCount);
        }
    }

    protected static void _addMetadataForTransactionToJson(final Transaction transaction, final Json transactionJson, final DatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) throws DatabaseException {
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

    public MetadataHandler(final DatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
    }

    @Override
    public void applyMetadataToBlockHeader(final Sha256Hash blockHash, final Json blockJson) {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            _addMetadataForBlockHeaderToJson(blockHash, blockJson, databaseConnection, _databaseManagerCache);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }
    }

    @Override
    public void applyMetadataToTransaction(final Transaction transaction, final Json transactionJson) {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            _addMetadataForTransactionToJson(transaction, transactionJson, databaseConnection, _databaseManagerCache);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }
    }
}
