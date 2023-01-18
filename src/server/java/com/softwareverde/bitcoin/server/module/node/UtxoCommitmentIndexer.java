package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainIndexer;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.timer.MilliTimer;

import java.util.HashSet;

public class UtxoCommitmentIndexer {
    protected final BlockchainIndexer _blockchainIndexer;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    protected TransactionId _indexUtxoBatch(final List<TransactionOutputIdentifier> batchIdentifiers, final List<TransactionOutput> batchOutputs) throws Exception {
        final HashSet<Sha256Hash> transactionHashDuplicateSet = new HashSet<>();

        final int batchCount = batchIdentifiers.getCount();
        final MutableList<Sha256Hash> transactionHashes = new MutableArrayList<>(batchCount);
        final MutableList<Integer> transactionByteCounts = new MutableArrayList<>(batchCount);
        for (final TransactionOutputIdentifier outputIdentifier : batchIdentifiers) {
            final Sha256Hash transactionHash = outputIdentifier.getTransactionHash();
            final Integer byteCount = 0;

            final boolean isUnique = transactionHashDuplicateSet.add(transactionHash);
            if (! isUnique) { continue; }

            transactionHashes.add(transactionHash);
            transactionByteCounts.add(byteCount);
        }

        // Store the TransactionHashes via the BlockchainIndexer so its cache may be also be updated...
        _blockchainIndexer.storeFastSyncTransactionHashes(transactionHashes, transactionByteCounts);

        return _blockchainIndexer.indexFastSyncUtxos(batchIdentifiers, batchOutputs);
    }

    public UtxoCommitmentIndexer(final BlockchainIndexer blockchainIndexer, final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _blockchainIndexer = blockchainIndexer;
        _databaseManagerFactory = databaseManagerFactory;
    }

    public void indexFastSyncUtxos(final List<TransactionOutputIdentifier> transactionOutputIdentifiers, final List<TransactionOutput> transactionOutputs) throws DatabaseException {
        try {
            final MilliTimer milliTimer = new MilliTimer();
            milliTimer.start();

            final TransactionId transactionId = _indexUtxoBatch(transactionOutputIdentifiers, transactionOutputs);
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
