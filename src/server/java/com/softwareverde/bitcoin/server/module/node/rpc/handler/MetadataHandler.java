package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.indexer.BlockchainIndexerDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.dsproof.DoubleSpendProofStore;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.slp.SlpUtil;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptInflater;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoAction;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptInflater;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

import java.math.BigInteger;
import java.util.HashMap;

public class MetadataHandler implements NodeRpcHandler.MetadataHandler {

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final DoubleSpendProofStore _doubleSpendProofStore;

    protected static void _addMetadataForBlockHeaderToJson(final Sha256Hash blockHash, final Json blockJson, final DatabaseManager databaseConnection) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseConnection.getBlockHeaderDatabaseManager();
        final BlockDatabaseManager blockDatabaseManager = databaseConnection.getBlockDatabaseManager();

        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);

        final MedianBlockTime medianTimePast = blockHeaderDatabaseManager.getMedianTimePast(blockId);
        final MedianBlockTime medianBlockTime = blockHeaderDatabaseManager.getMedianBlockTime(blockId);

        { // Include Extra Block Metadata...
            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
            final Integer transactionCount = blockDatabaseManager.getTransactionCount(blockId);
            final ChainWork chainWork = blockHeaderDatabaseManager.getChainWork(blockId);

            blockJson.put("height", blockHeight);
            blockJson.put("reward", BlockHeader.calculateBlockReward(blockHeight));
            blockJson.put("byteCount", blockHeaderDatabaseManager.getBlockByteCount(blockId));
            blockJson.put("transactionCount", transactionCount);
            blockJson.put("medianBlockTime", medianBlockTime.getCurrentTimeInSeconds());
            blockJson.put("medianTimePast", medianTimePast.getCurrentTimeInSeconds());
            blockJson.put("chainWork", chainWork);
        }
    }

    protected static void _addMetadataForTransactionToJson(final Transaction transaction, final Json transactionJson, final FullNodeDatabaseManager databaseManager, final DoubleSpendProofStore doubleSpendProofStore) throws DatabaseException {
        final Sha256Hash transactionHash = transaction.getHash();

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
        final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = databaseManager.getBlockchainIndexerDatabaseManager();
        final SlpTransactionDatabaseManager slpTransactionDatabaseManager = databaseManager.getSlpTransactionDatabaseManager();
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
        final MemoScriptInflater memoScriptInflater = new MemoScriptInflater();

        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        final ByteArray transactionData = transactionDeflater.toBytes(transaction);
        transactionJson.put("byteCount", transactionData.getByteCount());

        final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
        final SlpTokenId slpTokenId = blockchainIndexerDatabaseManager.getSlpTokenId(transactionId);
        final Boolean hasSlpData = (slpTokenId != null);
        final Boolean isSlpValid;
        if (hasSlpData) {
            isSlpValid = slpTransactionDatabaseManager.getSlpTransactionValidationResult(transactionId);
        }
        else {
            isSlpValid = null;
        }

        Long transactionFee = 0L;

        final boolean isUnconfirmed;
        { // Include Block hashes which include this transaction...
            final Json blockHashesJson = new Json(true);
            final List<BlockId> blockIds = transactionDatabaseManager.getBlockIds(transactionHash);
            for (final BlockId blockId : blockIds) {
                final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);
                blockHashesJson.add(blockHash);
            }
            transactionJson.put("blocks", blockHashesJson);
            isUnconfirmed = blockIds.isEmpty();
        }

        if ( isUnconfirmed && (doubleSpendProofStore != null) ) {
            boolean hasActiveDoubleSpend = false;
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                final DoubleSpendProof doubleSpendProof = doubleSpendProofStore.getDoubleSpendProof(transactionOutputIdentifier);
                if (doubleSpendProof != null) {
                    hasActiveDoubleSpend = true;
                    break;
                }
            }
            transactionJson.put("wasDoubleSpent", hasActiveDoubleSpend);
        }
        else {
            transactionJson.put("wasDoubleSpent", null);
        }

        { // Process TransactionInputs...
            final HashMap<Sha256Hash, Transaction> cachedTransactions = new HashMap<Sha256Hash, Transaction>(8);

            int transactionInputIndex = 0;
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final Sha256Hash previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                final Transaction previousTransaction;
                {
                    final Transaction cachedTransaction = cachedTransactions.get(previousOutputTransactionHash);
                    if (cachedTransaction != null) {
                        previousTransaction = cachedTransaction;
                    }
                    else {
                        final TransactionId previousOutputTransactionId = transactionDatabaseManager.getTransactionId(previousOutputTransactionHash);
                        previousTransaction = (previousOutputTransactionId != null ? transactionDatabaseManager.getTransaction(previousOutputTransactionId) : null);
                        cachedTransactions.put(previousOutputTransactionHash, previousTransaction);
                    }
                }

                if (previousTransaction == null) {
                    transactionFee = null; // Abort calculating the transaction fee but continue with the rest of the processing...
                }

                final TransactionOutput previousTransactionOutput;
                if (previousTransaction != null) {
                    final List<TransactionOutput> previousOutputs = previousTransaction.getTransactionOutputs();
                    final Integer previousOutputIndex = transactionInput.getPreviousOutputIndex();
                    previousTransactionOutput = previousOutputs.get(previousOutputIndex);
                }
                else {
                    previousTransactionOutput = null;
                }

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
                    final Integer previousOutputIndex = transactionInput.getPreviousOutputIndex();

                    if (previousTransaction != null) {
                        final Boolean isSlpOutput = SlpUtil.isSlpTokenOutput(previousTransaction, previousOutputIndex);
                        if (isSlpOutput) {
                            final BigInteger slpTokenAmount = SlpUtil.getOutputTokenAmount(previousTransaction, previousOutputIndex);
                            final Boolean isSlpBatonOutput = SlpUtil.isSlpTokenBatonHolder(previousTransaction, previousOutputIndex);

                            final Json slpOutputJson = new Json(false);
                            slpOutputJson.put("tokenAmount", slpTokenAmount);
                            slpOutputJson.put("isBaton", isSlpBatonOutput);
                            transactionInputJson.put("slp", slpOutputJson);
                        }
                    }
                }

                transactionInputIndex += 1;
            }
        }

        final MutableList<MemoAction> memoActions = new MutableList<>();
        { // Process TransactionOutputs...
            int transactionOutputIndex = 0;
            for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
                final LockingScript lockingScript = transactionOutput.getLockingScript();

                if (transactionFee != null) {
                    transactionFee -= transactionOutput.getAmount();
                }

                { // Add extra TransactionOutput json fields...
                    final String addressString;
                    final String cashAddressString;
                    {
                        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
                        final Address address = scriptPatternMatcher.extractAddress(scriptType, lockingScript);
                        addressString = (address != null ? address.toBase58CheckEncoded() : null);
                        cashAddressString = (address != null ? address.toBase32CheckEncoded(true) : null);
                    }

                    final Json transactionOutputJson = transactionJson.get("outputs").get(transactionOutputIndex);
                    transactionOutputJson.put("address", addressString);
                    transactionOutputJson.put("cashAddress", cashAddressString);

                    if (hasSlpData && Util.coalesce(isSlpValid, false)) { // SLP
                        final Boolean isSlpOutput = SlpUtil.isSlpTokenOutput(transaction, transactionOutputIndex);
                        if (isSlpOutput) {
                            final BigInteger slpTokenAmount = SlpUtil.getOutputTokenAmount(transaction, transactionOutputIndex);
                            final Boolean isSlpBatonOutput = SlpUtil.isSlpTokenBatonHolder(transaction, transactionOutputIndex);

                            final Json slpOutputJson = new Json(false);
                            slpOutputJson.put("tokenAmount", slpTokenAmount);
                            slpOutputJson.put("isBaton",isSlpBatonOutput);
                            transactionOutputJson.put("slp", slpOutputJson);
                        }
                    }

                    { // Memo
                        final MemoAction memoAction = memoScriptInflater.fromLockingScript(lockingScript);
                        if (memoAction != null) {
                            memoActions.add(memoAction);
                        }
                    }
                }

                transactionOutputIndex += 1;
            }
        }

        if (! memoActions.isEmpty()) {
            final Json memoActionsJson = new Json(true);
            for (final MemoAction memoAction : memoActions) {
                memoActionsJson.add(memoAction);
            }
            transactionJson.put("memo", memoActionsJson);
        }

        transactionJson.put("fee", transactionFee);

        final Json slpJson;
        if (hasSlpData) {
            final TransactionId slpGenesisTransactionId = transactionDatabaseManager.getTransactionId(slpTokenId);
            final Transaction slpGenesisTransaction = transactionDatabaseManager.getTransaction(slpGenesisTransactionId);

            final SlpGenesisScript slpGenesisScript;
            {
                if (slpGenesisTransaction != null) {
                    final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();

                    final List<TransactionOutput> transactionOutputs = slpGenesisTransaction.getTransactionOutputs();
                    final TransactionOutput transactionOutput = transactionOutputs.get(0);
                    final LockingScript lockingScript = transactionOutput.getLockingScript();
                    slpGenesisScript = slpScriptInflater.genesisScriptFromScript(lockingScript);
                }
                else {
                    slpGenesisScript = null;
                }
            }

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

    public MetadataHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory, final DoubleSpendProofStore doubleSpendProofStore) {
        _databaseManagerFactory = databaseManagerFactory;
        _doubleSpendProofStore = doubleSpendProofStore;
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
            _addMetadataForTransactionToJson(transaction, transactionJson, databaseManager, _doubleSpendProofStore);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }
}
