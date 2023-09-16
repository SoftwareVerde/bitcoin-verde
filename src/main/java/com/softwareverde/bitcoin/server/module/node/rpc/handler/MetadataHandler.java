package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressType;
import com.softwareverde.bitcoin.address.ParsedAddress;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.Blockchain;
import com.softwareverde.bitcoin.server.module.node.IndexedTransaction;
import com.softwareverde.bitcoin.server.module.node.TransactionIndexer;
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
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainIndexer;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.slp.SlpUtil;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptInflater;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoAction;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptInflater;
import com.softwareverde.bitcoin.transaction.script.slp.genesis.SlpGenesisScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.Map;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

import java.math.BigInteger;


public class MetadataHandler implements NodeRpcHandler.MetadataHandler {

    protected final Blockchain _blockchain;
    protected final TransactionIndexer _transactionIndexer;
    protected final DoubleSpendProofStore _doubleSpendProofStore;

    protected IndexedTransaction _getIndexedTransaction(final Sha256Hash transactionHash) {
        try {
            return _transactionIndexer.getIndexedTransaction(transactionHash);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return null;
        }
    }

    protected Sha256Hash _getSpendingTransactionHash(final Sha256Hash transactionHash, final Integer outputIndex) {
        try {
            final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
            return _transactionIndexer.getSpendingTransactionHash(transactionOutputIdentifier);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return null;
        }
    }

    public MetadataHandler(final Blockchain blockchain, final TransactionIndexer transactionIndexer, final DoubleSpendProofStore doubleSpendProofStore) {
        _blockchain = blockchain;
        _transactionIndexer = transactionIndexer;
        _doubleSpendProofStore = doubleSpendProofStore;
    }

    @Override
    public void applyMetadataToBlockHeader(final Sha256Hash blockHash, final Json blockJson) {
        final Long blockHeight = _blockchain.getBlockHeight(blockHash);
        if (blockHeight == null) { return; }

        final MedianBlockTime medianTimePast = _blockchain.getMedianBlockTime(blockHeight - 1L);
        final MedianBlockTime medianBlockTime = _blockchain.getMedianBlockTime(blockHeight);

        { // Include Extra Block Metadata...
            final Integer transactionCount = _blockchain.getTransactionCount(blockHeight);
            final ChainWork chainWork = _blockchain.getChainWork(blockHeight);
            final Long byteCount = _blockchain.getBlockByteCount(blockHeight);

            blockJson.put("height", blockHeight);
            blockJson.put("reward", BlockHeader.calculateBlockReward(blockHeight));
            blockJson.put("byteCount", byteCount);
            blockJson.put("transactionCount", transactionCount);
            blockJson.put("medianBlockTime", medianBlockTime.getCurrentTimeInSeconds());
            blockJson.put("medianTimePast", medianTimePast.getCurrentTimeInSeconds());
            blockJson.put("chainWork", chainWork);
        }
    }

    @Override
    public void applyMetadataToTransaction(final Transaction transaction, final Json transactionJson) {
        final Sha256Hash transactionHash = transaction.getHash();

        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
        final MemoScriptInflater memoScriptInflater = new MemoScriptInflater();

        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        final ByteArray transactionData = transactionDeflater.toBytes(transaction);
        transactionJson.put("byteCount", transactionData.getByteCount());

        final SlpTokenId slpTokenId = null; // TODO
        final boolean hasSlpData = (slpTokenId != null);
        final Boolean isSlpValid;
        if (hasSlpData) {
            isSlpValid = false; // TODO
        }
        else {
            isSlpValid = null;
        }

        Long transactionFee = 0L;

        final IndexedTransaction indexedTransaction = _getIndexedTransaction(transactionHash);

        final boolean isUnconfirmed;
        { // Include Block hashes which include this transaction...
            isUnconfirmed = (indexedTransaction != null && indexedTransaction.blockHeight != null);

            final Json blockHashesJson = new Json(true);
            if (indexedTransaction != null && indexedTransaction.blockHeight != null) {
                final BlockHeader blockHeader = _blockchain.getBlockHeader(indexedTransaction.blockHeight);
                if (blockHeader != null) {
                    final Sha256Hash blockHash = blockHeader.getHash();
                    blockHashesJson.add(blockHash);
                }
            }

            transactionJson.put("blocks", blockHashesJson);
        }

        if ( isUnconfirmed && (_doubleSpendProofStore != null) ) {
            boolean hasActiveDoubleSpend = false;
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                final DoubleSpendProof doubleSpendProof = _doubleSpendProofStore.getDoubleSpendProof(transactionOutputIdentifier);
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
            final MutableMap<Sha256Hash, Transaction> cachedTransactions = new MutableHashMap<>(8);

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
                        final IndexedTransaction previousOutputIndexedTransaction = _getIndexedTransaction(previousOutputTransactionHash);
                        previousTransaction = (previousOutputIndexedTransaction != null ? _blockchain.getTransaction(previousOutputIndexedTransaction) : null);
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
                        final Address addressBytes = scriptPatternMatcher.extractAddress(scriptType, lockingScript);
                        if (addressBytes != null) {
                            final ParsedAddress parsedAddress = new ParsedAddress(AddressType.P2PKH, false, addressBytes);
                            addressString = parsedAddress.toBase58CheckEncoded();
                            cashAddressString = parsedAddress.toBase32CheckEncoded(true);
                        }
                        else {
                            addressString = null;
                            cashAddressString = null;
                        }
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

        final MutableList<MemoAction> memoActions = new MutableArrayList<>();
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
                    final Sha256Hash scriptHash;
                    {
                        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
                        final Address addressBytes = scriptPatternMatcher.extractAddress(scriptType, lockingScript);
                        if (addressBytes != null) {
                            final ParsedAddress parsedAddress = new ParsedAddress(AddressType.P2PKH, false, addressBytes);
                            addressString = parsedAddress.toBase58CheckEncoded();
                            cashAddressString = parsedAddress.toBase32CheckEncoded(true);
                        }
                        else {
                            addressString = null;
                            cashAddressString = null;
                        }
                        scriptHash = ScriptBuilder.computeScriptHash(lockingScript);
                    }

                    final Sha256Hash spendingTransactionHash = _getSpendingTransactionHash(transactionHash, transactionOutputIndex);

                    final Json transactionOutputJson = transactionJson.get("outputs").get(transactionOutputIndex);
                    transactionOutputJson.put("address", addressString);
                    transactionOutputJson.put("cashAddress", cashAddressString);
                    transactionOutputJson.put("scriptHash", scriptHash);
                    transactionOutputJson.put("spentByTransaction", spendingTransactionHash);

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
            final IndexedTransaction slpGenesisIndexedTransaction = _getIndexedTransaction(slpTokenId);
            final Transaction slpGenesisTransaction = _blockchain.getTransaction(slpGenesisIndexedTransaction);

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
}
