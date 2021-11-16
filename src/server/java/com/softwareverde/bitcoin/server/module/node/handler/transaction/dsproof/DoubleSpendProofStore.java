package com.softwareverde.bitcoin.server.module.node.handler.transaction.dsproof;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.CircleBuffer;
import com.softwareverde.util.Util;

import java.util.HashSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DoubleSpendProofStore {
    protected final Float _percentCapacityAllocatedToPendingProofs = 0.10F;
    protected final Integer _banCapacity = 128; // ~1 minute of invalid proofs at 32MB capacity, assuming every Transaction is double-spent...

    // DSProofs that have been deemed valid...
    protected final ReentrantReadWriteLock.ReadLock _readLock;
    protected final ReentrantReadWriteLock.WriteLock _writeLock;
    protected final CircleBuffer<DoubleSpendProof> _doubleSpendProofs;

    // DSProofs that cannot be validated due to missing dependent transactions...
    protected final ReentrantReadWriteLock.ReadLock _pendingReadLock;
    protected final ReentrantReadWriteLock.WriteLock _pendingWriteLock;
    protected final CircleBuffer<DoubleSpendProof> _pendingDoubleSpendProofs;

    // DSProofs that have been banned...
    protected final ReentrantReadWriteLock.ReadLock _bannedReadLock;
    protected final ReentrantReadWriteLock.WriteLock _bannedWriteLock;
    protected final CircleBuffer<Sha256Hash> _bannedDoubleSpendProofs;

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
        final int itemCount = Math.max(1, (int) (maxCachedItemCount * _percentCapacityAllocatedToPendingProofs));
        final int pendingItemCount = Math.max(1, (maxCachedItemCount - itemCount));

        { // DSProof Buffer
            final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
            _readLock = readWriteLock.readLock();
            _writeLock = readWriteLock.writeLock();
            _doubleSpendProofs = new CircleBuffer<>(itemCount);
        }

        { // Pending DSProof Buffer
            final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
            _pendingReadLock = readWriteLock.readLock();
            _pendingWriteLock = readWriteLock.writeLock();
            _pendingDoubleSpendProofs = new CircleBuffer<>(pendingItemCount);
        }

        { // Banned DSProof Buffer
            final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
            _bannedReadLock = readWriteLock.readLock();
            _bannedWriteLock = readWriteLock.writeLock();
            _bannedDoubleSpendProofs = new CircleBuffer<>(_banCapacity);
        }
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

    public List<DoubleSpendProof> getDoubleSpendProofs() {
        _readLock.lock();
        try {
            final ImmutableListBuilder<DoubleSpendProof> listBuilder = new ImmutableListBuilder<>();
            for (final DoubleSpendProof doubleSpendProof : _doubleSpendProofs) {
                listBuilder.add(doubleSpendProof);
            }
            return listBuilder.build();
        }
        finally {
            _readLock.unlock();
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

    public void storePendingDoubleSpendProof(final DoubleSpendProof doubleSpendProof) {
        _pendingWriteLock.lock();
        try {
            _pendingDoubleSpendProofs.push(doubleSpendProof);
        }
        finally {
            _pendingWriteLock.unlock();
        }
    }

    public void getPendingDoubleSpendProof(final DoubleSpendProof doubleSpendProof) {
        _pendingReadLock.lock();
        try {
            _pendingDoubleSpendProofs.push(doubleSpendProof);
        }
        finally {
            _pendingReadLock.unlock();
        }
    }

    public List<DoubleSpendProof> getTriggeredPendingDoubleSpendProof(final List<Transaction> transactions) {
        final HashSet<Sha256Hash> triggeringTransactionHashes = new HashSet<>();

        for (final Transaction transaction : transactions) {
            { // Add the Transaction's previous TransactionOutputs' hashes...
                for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                    final Sha256Hash transactionHash = transactionInput.getPreviousOutputTransactionHash();
                    triggeringTransactionHashes.add(transactionHash);
                }
            }

            { // Add the Transaction's Hash...
                final Sha256Hash transactionHash = transaction.getHash();
                triggeringTransactionHashes.add(transactionHash);
            }
        }

        _pendingReadLock.lock();
        try {
            final MutableList<DoubleSpendProof> triggeredDoubleSpendProofs = new MutableList<>(0);
            for (final DoubleSpendProof doubleSpendProof : _pendingDoubleSpendProofs) {
                final TransactionOutputIdentifier transactionOutputIdentifier = doubleSpendProof.getTransactionOutputIdentifierBeingDoubleSpent();
                final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                if (triggeringTransactionHashes.contains(transactionHash)) {
                    triggeredDoubleSpendProofs.add(doubleSpendProof);
                }
            }
            return triggeredDoubleSpendProofs;
        }
        finally {
            _pendingReadLock.unlock();
        }
    }

    public void banDoubleSpendProof(final Sha256Hash doubleSpendProofHash) {
        _bannedWriteLock.lock();
        try {
            _bannedDoubleSpendProofs.push(doubleSpendProofHash);
        }
        finally {
            _bannedWriteLock.unlock();
        }
    }

    public Boolean isDoubleSpendProofBanned(final Sha256Hash doubleSpendProofHash) {
        _bannedReadLock.lock();
        try {
            return _bannedDoubleSpendProofs.contains(doubleSpendProofHash);
        }
        finally {
            _bannedReadLock.unlock();
        }
    }
}
