package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.slp.SlpUtil;
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
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

public class MetadataHandler implements NodeRpcHandler.MetadataHandler {

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    protected static void _addMetadataForBlockHeaderToJson(final Sha256Hash blockHash, final Json blockJson, final DatabaseManager databaseConnection) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseConnection.getBlockHeaderDatabaseManager();
        final BlockDatabaseManager blockDatabaseManager = databaseConnection.getBlockDatabaseManager();

        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);

        final MedianBlockTime medianBlockTime = blockHeaderDatabaseManager.calculateMedianBlockTime(blockId);

        { // Include Extra Block Metadata...
            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
            final Integer transactionCount = blockDatabaseManager.getTransactionCount(blockId);

            blockJson.put("height", blockHeight);
            blockJson.put("reward", BlockHeader.calculateBlockReward(blockHeight));
            blockJson.put("byteCount", blockHeaderDatabaseManager.getBlockByteCount(blockId));
            blockJson.put("transactionCount", transactionCount);
            blockJson.put("medianBlockTime", medianBlockTime.getCurrentTimeInSeconds());
        }
    }

    protected static void _addMetadataForTransactionToJson(final Transaction transaction, final Json transactionJson, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final Sha256Hash transactionHash = transaction.getHash();
        final String transactionHashString = transactionHash.toString();

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
        final SlpTransactionDatabaseManager slpTransactionDatabaseManager = databaseManager.getSlpTransactionDatabaseManager();
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = databaseManager.getTransactionOutputDatabaseManager();
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        final ByteArray transactionData = transactionDeflater.toBytes(transaction);
        transactionJson.put("byteCount", transactionData.getByteCount());

        final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
        final SlpTokenId slpTokenId = slpTransactionDatabaseManager.getSlpTokenId(transactionId);
        final Boolean hasSlpData = (slpTokenId != null);
        final Boolean isSlpValid;
        if (hasSlpData) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            isSlpValid = slpTransactionDatabaseManager.getSlpTransactionValidationResult(blockchainSegmentId, transactionId);
        }
        else {
            isSlpValid = null;
        }

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
                final Sha256Hash previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                final TransactionOutputId previousTransactionOutputId;
                {
                    if (previousOutputTransactionHash != null) {
                        final TransactionOutputIdentifier previousTransactionOutputIdentifier = new TransactionOutputIdentifier(previousOutputTransactionHash, transactionInput.getPreviousOutputIndex());
                        previousTransactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(previousTransactionOutputIdentifier);

                        if (previousTransactionOutputId == null) {
                            Logger.warn("Error calculating fee for Transaction: " + transactionHashString);
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
                final String cashAddressString;
                {
                    if (previousTransactionOutput != null) {
                        final LockingScript lockingScript = previousTransactionOutput.getLockingScript();
                        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
                        final Address address = scriptPatternMatcher.extractAddress(scriptType, lockingScript);
                        addressString = address.toBase58CheckEncoded();
                        cashAddressString = address.toBase32CheckEncoded(true);
                    }
                    else {
                        addressString = null;
                        cashAddressString = null;
                    }
                }

                final Json transactionInputJson = transactionJson.get("inputs").get(transactionInputIndex);
                transactionInputJson.put("previousTransactionAmount", previousTransactionOutputAmount);
                transactionInputJson.put("address", addressString);
                transactionInputJson.put("cashAddress", cashAddressString);

                if (hasSlpData && Util.coalesce(isSlpValid, false)) {
                    final TransactionId previousTransactionId = transactionDatabaseManager.getTransactionId(previousOutputTransactionHash);
                    final Transaction previousTransaction = transactionDatabaseManager.getTransaction(previousTransactionId);
                    final Integer previousOutputIndex = transactionInput.getPreviousOutputIndex();

                    final Boolean isSlpOutput = SlpUtil.isSlpTokenOutput(previousTransaction, previousOutputIndex);
                    if (isSlpOutput) {
                        final Long slpTokenAmount = SlpUtil.getOutputTokenAmount(previousTransaction, previousOutputIndex);
                        final Boolean isSlpBatonOutput = SlpUtil.isSlpTokenBatonHolder(previousTransaction, previousOutputIndex);

                        final Json slpOutputJson = new Json(false);
                        slpOutputJson.put("tokenAmount", slpTokenAmount);
                        slpOutputJson.put("isBaton",isSlpBatonOutput);
                        transactionInputJson.put("slp", slpOutputJson);
                    }
                }

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
                    final String cashAddressString;
                    {
                        final LockingScript lockingScript = transactionOutput.getLockingScript();
                        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
                        final Address address = scriptPatternMatcher.extractAddress(scriptType, lockingScript);
                        addressString = (address != null ? address.toBase58CheckEncoded() : null);
                        cashAddressString = (address != null ? address.toBase32CheckEncoded(true) : null);
                    }

                    final Json transactionOutputJson = transactionJson.get("outputs").get(transactionOutputIndex);
                    transactionOutputJson.put("address", addressString);
                    transactionOutputJson.put("cashAddress", cashAddressString);

                    if (hasSlpData && Util.coalesce(isSlpValid, false)) {
                        final Boolean isSlpOutput = SlpUtil.isSlpTokenOutput(transaction, transactionOutputIndex);
                        if (isSlpOutput) {
                            final Long slpTokenAmount = SlpUtil.getOutputTokenAmount(transaction, transactionOutputIndex);
                            final Boolean isSlpBatonOutput = SlpUtil.isSlpTokenBatonHolder(transaction, transactionOutputIndex);

                            final Json slpOutputJson = new Json(false);
                            slpOutputJson.put("tokenAmount", slpTokenAmount);
                            slpOutputJson.put("isBaton",isSlpBatonOutput);
                            transactionOutputJson.put("slp", slpOutputJson);
                        }
                    }
                }

                transactionOutputIndex += 1;
            }
        }

        transactionJson.put("fee", transactionFee);

        final Json slpJson;
        if (hasSlpData) {
            final SlpGenesisScript slpGenesisScript = slpTransactionDatabaseManager.getSlpGenesisScript(slpTokenId);
            if (slpGenesisScript != null) {
                slpJson = slpGenesisScript.toJson();

                slpJson.put("tokenId", slpTokenId);
                slpJson.put("isValid", isSlpValid);
            }
            else {
                slpJson = null;
            }

        }
        else {
            slpJson = null;
        }
        transactionJson.put("slp", slpJson);
    }

    public MetadataHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public void applyMetadataToBlockHeader(final Sha256Hash blockHash, final Json blockJson) {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            _addMetadataForBlockHeaderToJson(blockHash, blockJson, databaseManager);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }

    @Override
    public void applyMetadataToTransaction(final Transaction transaction, final Json transactionJson) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            _addMetadataForTransactionToJson(transaction, transactionJson, databaseManager);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }
}
