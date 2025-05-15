package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.BlockValidationResult;
import com.softwareverde.bitcoin.block.validator.ValidationResult;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputLevelDbManager;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionWithFee;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptInflater;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

public class BlockchainDataHandler implements NodeRpcHandler.DataHandler {
    protected final Blockchain _blockchain;
    protected final BlockStore _blockStore;
    protected final UpgradeSchedule _upgradeSchedule;
    protected final TransactionIndexer _transactionIndexer;
    protected final TransactionMempool _mempool;
    protected final UnspentTransactionOutputLevelDbManager _utxoManager;

    protected Container<Long> _headBlockHeightContainer;
    protected Container<Long> _headBlockHeaderHeightContainer;
    protected Container<Long> _indexedBlockHeightContainer;

    public BlockchainDataHandler(final Blockchain blockchain, final BlockStore blockStore, final UpgradeSchedule upgradeSchedule, final TransactionIndexer transactionIndexer, final TransactionMempool transactionMempool, final UnspentTransactionOutputLevelDbManager utxoManager) {
        _blockchain = blockchain;
        _blockStore = blockStore;
        _upgradeSchedule = upgradeSchedule;
        _transactionIndexer = transactionIndexer;
        _mempool = transactionMempool;
        _utxoManager = utxoManager;
    }

    public void setHeadBlockHeightContainer(final Container<Long> container) {
        _headBlockHeightContainer = container;
    }
    public void setHeadBlockHeaderHeightContainer(final Container<Long> container) {
        _headBlockHeaderHeightContainer = container;
    }
    public void setIndexedBlockHeightContainer(final Container<Long> container) {
        _indexedBlockHeightContainer = container;
    }

    @Override
    public Long getBlockHeaderHeight() {
        return _blockchain.getHeadBlockHeaderHeight();
    }

    @Override
    public Long getBlockHeight() {
        return _blockchain.getHeadBlockHeight();
    }

    @Override
    public Long getBlockHeaderTimestamp() {
        final Long blockHeight = _blockchain.getHeadBlockHeaderHeight();
        final BlockHeader blockHeader = _blockchain.getBlockHeader(blockHeight);
        return blockHeader.getTimestamp();
    }

    @Override
    public Long getBlockTimestamp() {
        final Long blockHeight = _blockchain.getHeadBlockHeight();
        final BlockHeader blockHeader = _blockchain.getBlockHeader(blockHeight);
        return blockHeader.getTimestamp();
    }

    @Override
    public BlockHeader getBlockHeader(final Long blockHeight) {
        return _blockchain.getBlockHeader(blockHeight);
    }

    @Override
    public BlockHeader getBlockHeader(final Sha256Hash blockHash) {
        final Long blockHeight = _blockchain.getBlockHeight(blockHash);
        if (blockHeight == null) { return null; }
        return _blockchain.getBlockHeader(blockHeight);
    }

    @Override
    public Boolean isBlockOrphaned(final Sha256Hash blockHash) {
        return (_blockchain.getBlockHeight(blockHash) == null);
    }

    @Override
    public Long getBlockHeaderHeight(final Sha256Hash blockHash) {
        return _blockchain.getBlockHeight(blockHash);
    }

    @Override
    public Block getBlock(final Long blockHeight) {
        final BlockHeader blockHeader = _blockchain.getBlockHeader(blockHeight);
        if (blockHeader == null) { return null; }

        final Sha256Hash blockHash = blockHeader.getHash();
        return _blockStore.getBlock(blockHash, blockHeight);
    }

    @Override
    public Block getBlock(final Sha256Hash blockHash) {
        final Long blockHeight = _blockchain.getBlockHeight(blockHash);
        if (blockHeight == null) { return null; }
        return _blockStore.getBlock(blockHash, blockHeight);
    }

    @Override
    public List<Transaction> getBlockTransactions(final Long blockHeight, final Integer pageSize, final Integer pageNumber) {
        final BlockHeader blockHeader = _blockchain.getBlockHeader(blockHeight);
        if (blockHeader == null) { return null; }

        final Sha256Hash blockHash = blockHeader.getHash();
        final Block block = _blockStore.getBlock(blockHash, blockHeight);
        if (block == null) { return null; }

        final List<Transaction> transactions = block.getTransactions();
        final int transactionCount = transactions.getCount();

        final MutableList<Transaction> pagedTransactions = new MutableArrayList<>(pageSize);
        for (int i = 0; i < pageSize; ++i) {
            final int index = (pageNumber * pageSize) + i;
            if (index >= transactionCount) { break; }

            final Transaction transaction = transactions.get(index);
            pagedTransactions.add(transaction);
        }

        return pagedTransactions;
    }

    @Override
    public List<Transaction> getBlockTransactions(final Sha256Hash blockHash, final Integer pageSize, final Integer pageNumber) {
        final Long blockHeight = _blockchain.getBlockHeight(blockHash);
        if (blockHeight == null) { return null; }

        return this.getBlockTransactions(blockHeight, pageSize, pageNumber);
    }

    @Override
    public List<BlockHeader> getBlockHeaders(final Long nullableBlockHeight, final Integer maxBlockCount, final Direction blockHeaderDirection) {
        final Long blockHeight = Util.coalesce(nullableBlockHeight, _blockchain.getHeadBlockHeaderHeight());

        final MutableList<BlockHeader> blockHeaders = new MutableArrayList<>();
        for (int i = 0; i < maxBlockCount; ++i) {
            final long iBlockHeight = blockHeight + (blockHeaderDirection == Direction.AFTER ? i : -i);

            final BlockHeader blockHeader = _blockchain.getBlockHeader(iBlockHeight);
            if (blockHeader == null) { break; }

            blockHeaders.add(blockHeader);
        }

        return blockHeaders;
    }

    @Override
    public Transaction getTransaction(final Sha256Hash transactionHash) {
        try {
            final IndexedTransaction indexedTransaction = _transactionIndexer.getIndexedTransaction(transactionHash);
            if (indexedTransaction == null) { return null; }

            final Transaction transaction = _blockchain.getTransaction(indexedTransaction);
            if (transaction != null) { return transaction; }

            final TransactionWithFee transactionWithFee = _mempool.getTransaction(transactionHash);
            return (transactionWithFee != null ? transactionWithFee.transaction : null);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public Sha256Hash getTransactionBlockHash(final Sha256Hash transactionHash) {
        try {
            final IndexedTransaction indexedTransaction = _transactionIndexer.getIndexedTransaction(transactionHash);
            if (indexedTransaction == null) { return null; }

            final BlockHeader blockHeader = _blockchain.getBlockHeader(indexedTransaction.blockHeight);
            if (blockHeader == null) { return null; }

            return blockHeader.getHash();
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public Integer getTransactionBlockIndex(final Sha256Hash transactionHash) {
        // TODO: Not very fast for large blocks.
        try {
            final IndexedTransaction indexedTransaction = _transactionIndexer.getIndexedTransaction(transactionHash);
            if (indexedTransaction == null) { return null; }

            final long blockHeight = indexedTransaction.blockHeight;

            final BlockHeader blockHeader = _blockchain.getBlockHeader(blockHeight);
            if (blockHeader == null) { return null; }

            final Sha256Hash blockHash = blockHeader.getHash();
            final Block block = _blockStore.getBlock(blockHash, blockHeight);
            if (block == null) { return null; }

            int index = 0;
            final List<Transaction> transactions = block.getTransactions();
            for (final Transaction transaction : transactions) {
                if (Util.areEqual(transactionHash, transaction.getHash())) {
                    return index;
                }
                index += 1;
            }

            return null;
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public Boolean hasUnconfirmedInputs(final Sha256Hash transactionHash) {
        final Transaction transaction = this.getTransaction(transactionHash);
        if (transaction == null) { return null; }

        try {
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                if (_utxoManager.getUnspentTransactionOutput(transactionOutputIdentifier) == null) { return true; }
            }
            return false;
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return true;
        }
    }

    @Override
    public Difficulty getDifficulty() {
        final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(_blockchain, _upgradeSchedule);
        final long blockHeight = _blockchain.getHeadBlockHeaderHeight();
        return difficultyCalculator.calculateRequiredDifficulty(blockHeight + 1L);
    }

    @Override
    public List<Transaction> getUnconfirmedTransactions() {
        final List<TransactionWithFee> unconfirmedTransactions = _mempool.getTransactions();

        final int transactionCount = unconfirmedTransactions.getCount();
        final MutableList<Transaction> transactions = new MutableArrayList<>(transactionCount);
        for (final TransactionWithFee transactionWithFee : unconfirmedTransactions) {
            transactions.add(transactionWithFee.transaction);
        }
        return transactions;
    }

    @Override
    public List<TransactionWithFee> getUnconfirmedTransactionsWithFees() {
        return _mempool.getTransactions();
    }

    @Override
    public Tuple<Block, Long> getPrototypeBlock() {
        // TODO: requires blockchain mempool.
        return null;
    }

    @Override
    public Long getBlockReward() {
        final long blockHeight = _blockchain.getHeadBlockHeaderHeight();
        return BlockHeader.calculateBlockReward(blockHeight);
    }

    @Override
    public Boolean isSlpTransaction(final Sha256Hash transactionHash) {
        final Transaction transaction = this.getTransaction(transactionHash);
        if (transaction == null) { return null; }

        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

        final TransactionOutput transactionOutput = transactionOutputs.get(0);
        final LockingScript lockingScript = transactionOutput.getLockingScript();

        return SlpScriptInflater.matchesSlpFormat(lockingScript);
    }

    @Override
    public Boolean isValidSlpTransaction(final Sha256Hash transactionHash) {
        // TODO: requires slp indexing.
        return null;
    }

    @Override
    public SlpTokenId getSlpTokenId(Sha256Hash transactionHash) {
        final Transaction transaction = this.getTransaction(transactionHash);
        if (transaction == null) { return null; }

        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

        final TransactionOutput transactionOutput = transactionOutputs.get(0);
        final LockingScript lockingScript = transactionOutput.getLockingScript();
        if (! SlpScriptInflater.matchesSlpFormat(lockingScript)) { return null; }

        return SlpScriptInflater.getTokenId(lockingScript);
    }

    @Override
    public List<DoubleSpendProof> getDoubleSpendProofs() {
        // TODO: requires dsproofs...
        return null;
    }

    @Override
    public DoubleSpendProof getDoubleSpendProof(final Sha256Hash doubleSpendProofHash) {
        // TODO: requires dsproofs...
        return null;
    }

    @Override
    public DoubleSpendProof getDoubleSpendProof(final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent) {
        // TODO: requires dsproofs...
        return null;
    }

    @Override
    public BlockValidationResult validatePrototypeBlock(final Block block) {
        // TODO: inject anonymous interface from nodeModule.
        return null;
    }

    @Override
    public ValidationResult validateTransaction(final Transaction transaction, final Boolean enableSlpValidation) {
        // TODO: inject anonymous interface from nodeModule.
        return null;
    }

    @Override
    public Float getIndexingPercentComplete() {
        final Long headBlockHeight = (_headBlockHeaderHeightContainer != null ? _headBlockHeaderHeightContainer.value : null);
        final Long indexedBlockHeight = (_indexedBlockHeightContainer != null ? _indexedBlockHeightContainer.value : null);
        if (headBlockHeight == null || indexedBlockHeight == null) { return null; }

        return (indexedBlockHeight.floatValue() / headBlockHeight.floatValue());
    }

    @Override
    public Float getSlpIndexingPercentComplete() {
        return null;
    }

    @Override
    public void submitTransaction(final Transaction transaction) {
        // TODO: inject anonymous interface from nodeModule.
    }

    @Override
    public void submitBlock(final Block block) {
        // TODO: inject anonymous interface from nodeModule.
    }

    @Override
    public void reconsiderBlock(final Sha256Hash blockHash) {
        // TODO: track invalid blocks.
    }
}
