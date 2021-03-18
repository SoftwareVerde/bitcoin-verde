package com.softwareverde.bitcoin.server.module.node.handler.transaction.dsproof;

import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.CircleBuffer;
import com.softwareverde.util.Util;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DoubleSpendProofStore {
    protected final ReentrantReadWriteLock.ReadLock _readLock;
    protected final ReentrantReadWriteLock.WriteLock _writeLock;
    protected final CircleBuffer<DoubleSpendProof> _doubleSpendProofs;

    protected DoubleSpendProof _getDoubleSpendProof(final Sha256Hash doubleSpendProofHash) {
        for (final DoubleSpendProof doubleSpendProof : _doubleSpendProofs) {
            final Sha256Hash existingDoubleSpendProofHash = doubleSpendProof.getHash();
            if (Util.areEqual(doubleSpendProofHash, existingDoubleSpendProofHash)) {
                return doubleSpendProof;
            }
        }
        return null;
    }

    protected DoubleSpendProof _getDoubleSpendProof(final TransactionOutputIdentifier transactionOutputIdentifier) {
        for (final DoubleSpendProof doubleSpendProof : _doubleSpendProofs) {
            final TransactionOutputIdentifier existingTransactionOutputIdentifier = doubleSpendProof.getTransactionOutputIdentifierBeingDoubleSpent();
            if (Util.areEqual(transactionOutputIdentifier, existingTransactionOutputIdentifier)) {
                return doubleSpendProof;
            }
        }
        return null;
    }

    public DoubleSpendProofStore(final Integer maxCachedItemCount) {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _readLock = readWriteLock.readLock();
        _writeLock = readWriteLock.writeLock();

        _doubleSpendProofs = new CircleBuffer<>(maxCachedItemCount);
    }

    public Boolean storeDoubleSpendProof(final DoubleSpendProof doubleSpendProof) {
        _writeLock.lock();
        try {
            // NOTE: The DoubleSpendProof is looked-up via the PreviousOutputIdentifier (vs hash) so that the store is always unique for multiple double-spends.
            final TransactionOutputIdentifier transactionOutputIdentifier = doubleSpendProof.getTransactionOutputIdentifierBeingDoubleSpent();
            final DoubleSpendProof existingDoubleSpendProof = _getDoubleSpendProof(transactionOutputIdentifier);
            if (existingDoubleSpendProof != null) { return false; }

            _doubleSpendProofs.push(doubleSpendProof);
            return true;
        }
        finally {
            _writeLock.unlock();
        }
    }

    public DoubleSpendProof getDoubleSpendProof(final Sha256Hash doubleSpendProofHash) {
        _readLock.lock();
        try {
            return _getDoubleSpendProof(doubleSpendProofHash);
        }
        finally {
            _readLock.unlock();
        }
    }

    public DoubleSpendProof getDoubleSpendProof(final TransactionOutputIdentifier transactionOutputIdentifier) {
        _readLock.lock();
        try {
            return _getDoubleSpendProof(transactionOutputIdentifier);
        }
        finally {
            _readLock.unlock();
        }
    }
}
