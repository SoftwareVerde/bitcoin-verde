package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class DisabledDatabaseManagerCache implements DatabaseManagerCache {

    @Override
    public TransactionId getCachedTransactionId(final Sha256Hash transactionHash) {
        return null;
    }

    @Override
    public void cacheTransactionId(final Sha256Hash transactionHash, final TransactionId transactionId) { }

    @Override
    public void invalidateTransactionId(final Sha256Hash transactionHash) { }

    @Override
    public void invalidateTransactionIdCache() {
    }

    @Override
    public Transaction getCachedTransaction(final TransactionId transactionId) {
        return null;
    }

    @Override
    public void cacheTransaction(final TransactionId transactionId, final Transaction transaction) { }

    @Override
    public void invalidateTransaction(final TransactionId transactionId) { }

    @Override
    public void invalidateTransactionCache() { }

    @Override
    public TransactionOutputId getCachedTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex) {
        return null;
    }

    @Override
    public void cacheTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) { }

    @Override
    public void invalidateTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex) { }

    @Override
    public void invalidateTransactionOutputIdCache() { }

    @Override
    public TransactionOutputId getCachedUnspentTransactionOutputId(final Sha256Hash transactionHash, final Integer transactionOutputIndex) {
        return null;
    }

    @Override
    public void cacheUnspentTransactionOutputId(final Sha256Hash transactionHash, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) { }

    @Override
    public void invalidateUnspentTransactionOutputId(final TransactionOutputIdentifier transactionOutputIdentifier) { }

    @Override
    public void invalidateUnspentTransactionOutputIds(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) { }

    @Override
    public BlockchainSegmentId getCachedBlockchainSegmentId(final BlockId blockId) {
        return null;
    }

    @Override
    public void cacheBlockchainSegmentId(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId) { }

    @Override
    public void invalidCachedBlockchainSegmentId(final BlockId blockId) { }

    @Override
    public void invalidateBlockIdBlockchainSegmentIdCache() { }

    @Override
    public AddressId getCachedAddressId(final String address) {
        return null;
    }

    @Override
    public void cacheAddressId(final String address, final AddressId addressId) { }

    @Override
    public void invalidateAddressId(final String address) { }

    @Override
    public void invalidateAddressIdCache() { }

    @Override
    public Long getCachedBlockHeight(final BlockId blockId) {
        return null;
    }

    @Override
    public void cacheBlockHeight(final BlockId blockId, final Long blockHeight) { }

    @Override
    public void invalidateBlockHeight(final BlockId blockId) { }

    @Override
    public void invalidateBlockHeaderCache() { }

    @Override
    public void close() { }
}
