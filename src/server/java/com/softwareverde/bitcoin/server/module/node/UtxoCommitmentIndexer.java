package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainIndexer;
import com.softwareverde.bitcoin.server.properties.PropertiesStore;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;

import java.util.HashSet;

public class UtxoCommitmentIndexer {
    protected static final String UTXO_COMMIT_INDEXING_COMPLETED_KEY = "utxo_import_indexing_has_completed";

    protected final BlockchainIndexer _blockchainIndexer;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    protected TransactionId _indexUtxoBatch(final List<TransactionOutputIdentifier> batchIdentifiers, final List<TransactionOutput> batchOutputs, final FullNodeDatabaseManager databaseManager) throws Exception {
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
        final HashSet<Sha256Hash> transactionHashDuplicateSet = new HashSet<>();

        final int batchCount = batchIdentifiers.getCount();
        final MutableList<Sha256Hash> transactionHashes = new MutableList<>(batchCount);
        final MutableList<Integer> transactionByteCounts = new MutableList<>(batchCount);
        for (final TransactionOutputIdentifier outputIdentifier : batchIdentifiers) {
            final Sha256Hash transactionHash = outputIdentifier.getTransactionHash();
            final Integer byteCount = 0;

            final boolean isUnique = transactionHashDuplicateSet.add(transactionHash);
            if (! isUnique) { continue; }

            transactionHashes.add(transactionHash);
            transactionByteCounts.add(byteCount);
        }

        transactionDatabaseManager.storeTransactionHashes(transactionHashes, transactionByteCounts);
        return _blockchainIndexer.indexUtxosFromUtxoCommitmentImport(batchIdentifiers, batchOutputs);
    }

    protected void _indexUtxosAfterUtxoCommitmentImport(final FullNodeDatabaseManager databaseManager) throws Exception {
        final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();

        final int batchSize = 1024;
        final MutableList<TransactionOutputIdentifier> batchIdentifiers = new MutableList<>(batchSize);
        final MutableList<TransactionOutput> batchOutputs = new MutableList<>(batchSize);
        final Container<TransactionId> lastCompletedTransactionId = new Container<>();
        unspentTransactionOutputDatabaseManager.visitUnspentTransactionOutputs(new UnspentTransactionOutputDatabaseManager.UnspentTransactionOutputVisitor() {
            @Override
            public void run(final TransactionOutputIdentifier transactionOutputIdentifier, final UnspentTransactionOutput transactionOutput) throws Exception {
                batchIdentifiers.add(transactionOutputIdentifier);
                batchOutputs.add(transactionOutput);

                final int batchCount = batchIdentifiers.getCount();
                if (batchCount >= batchSize) {
                    lastCompletedTransactionId.value = _indexUtxoBatch(batchIdentifiers, batchOutputs, databaseManager);
                    batchIdentifiers.clear();
                    batchOutputs.clear();
                }
            }
        });
        if (! batchIdentifiers.isEmpty()) {
            lastCompletedTransactionId.value = _indexUtxoBatch(batchIdentifiers, batchOutputs, databaseManager);
            batchIdentifiers.clear();
            batchOutputs.clear();
        }

        final TransactionId transactionId = lastCompletedTransactionId.value;
        if (transactionId != null) {
            _blockchainIndexer.commitLastProcessedTransactionIdFromUtxoCommitmentImport(transactionId);
        }
    }

    public UtxoCommitmentIndexer(final BlockchainIndexer blockchainIndexer, final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _blockchainIndexer = blockchainIndexer;
        _databaseManagerFactory = databaseManagerFactory;
    }

    public void indexUtxosAfterUtxoCommitmentImport() throws DatabaseException {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            _indexUtxosAfterUtxoCommitmentImport(databaseManager);

            final PropertiesStore propertiesStore = databaseManager.getPropertiesStore();
            propertiesStore.set(UtxoCommitmentIndexer.UTXO_COMMIT_INDEXING_COMPLETED_KEY, 1L);
        }
        catch (final Exception exception) {
            if (exception instanceof DatabaseException) {
                throw (DatabaseException) exception;
            }

            throw new DatabaseException(exception);
        }
    }

    public Boolean hasPostFastSyncIndexingCompleted() throws DatabaseException {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final PropertiesStore propertiesStore = databaseManager.getPropertiesStore();
            final Long hasCompletedLong = propertiesStore.get(UtxoCommitmentIndexer.UTXO_COMMIT_INDEXING_COMPLETED_KEY);
            return (Util.coalesce(hasCompletedLong) > 0L);
        }
    }
}
