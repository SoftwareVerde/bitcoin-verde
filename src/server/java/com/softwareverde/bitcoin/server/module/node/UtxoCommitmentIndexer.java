package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainIndexer;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.timer.MilliTimer;

import java.util.HashSet;

public class UtxoCommitmentIndexer {
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

        // TODO: Inject the TransactionId -> TransactionHash mapping into the IndexerCache...
        transactionDatabaseManager.storeTransactionHashes(transactionHashes, transactionByteCounts);
        return _blockchainIndexer.indexUtxosFromUtxoCommitmentImport(batchIdentifiers, batchOutputs);
    }

    public UtxoCommitmentIndexer(final BlockchainIndexer blockchainIndexer, final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _blockchainIndexer = blockchainIndexer;
        _databaseManagerFactory = databaseManagerFactory;
    }

    public void indexUtxosAfterUtxoCommitmentImport(final List<TransactionOutputIdentifier> transactionOutputIdentifiers, final List<TransactionOutput> transactionOutputs) throws DatabaseException {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final MilliTimer milliTimer = new MilliTimer();
            milliTimer.start();

            final TransactionId transactionId = _indexUtxoBatch(transactionOutputIdentifiers, transactionOutputs, databaseManager);
            _blockchainIndexer.commitLastProcessedTransactionIdFromUtxoCommitmentImport(transactionId);

            milliTimer.stop();
            Logger.trace("Indexed " + transactionOutputIdentifiers.getCount() + " outputs in " + milliTimer.getMillisecondsElapsed() + "ms.");
        }
        catch (final Exception exception) {
            if (exception instanceof DatabaseException) {
                throw (DatabaseException) exception;
            }

            throw new DatabaseException(exception);
        }
    }
}
