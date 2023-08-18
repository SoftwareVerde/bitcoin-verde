package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.filedb.EntryInflater;
import com.softwareverde.filedb.FileDb;

import java.io.File;

public class TransactionIndexer implements AutoCloseable {
    protected final Double _falsePositiveRate = 0.000001D;
    protected final FileDb<Sha256Hash, Integer> _fileDb;

    public TransactionIndexer(final File dataDirectory) throws Exception {
        if (! FileDb.exists(dataDirectory)) {
            FileDb.initialize(dataDirectory);
        }

        _fileDb = new FileDb<>(dataDirectory, new TransactionIndexEntryInflater());
        _fileDb.setTargetBucketMemoryByteCount(0L);
        _fileDb.setTargetFilterMemoryByteCount(ByteUtil.Unit.Binary.GIBIBYTES);
        _fileDb.load();
        _fileDb.loadIntoMemory();
        _fileDb.createMetaFilters();
    }

    public void indexTransactions(final Block block, final Long blockHeight) throws Exception {
        final Integer blockHeightInt = blockHeight.intValue();
        final List<Transaction> transactions = block.getTransactions();
        _fileDb.resizeCapacity(transactions.getCount(), _falsePositiveRate);
        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            _fileDb.put(transactionHash, blockHeightInt);
        }
        _fileDb.finalizeBucket();

        if (blockHeight % 1024L == 0L) {
            _fileDb.createMetaFilters();
        }
    }

    public void undoBlock() throws Exception {
        _fileDb.undoBucket();
    }

    public Long getTransactionBlockHeight(final Sha256Hash transactionHash) throws Exception {
        final Integer blockHeightInt = _fileDb.get(transactionHash);
        if (blockHeightInt == null) { return null; }

        return blockHeightInt.longValue();
    }

    public List<Long> getTransactionBlockHeights(final List<Sha256Hash> transactionHashes) throws Exception {
        final List<Integer> blockHeightInts = _fileDb.get(transactionHashes, false);
        if (blockHeightInts == null) { return null; }

        final MutableList<Long> blockHeights = new MutableArrayList<>(transactionHashes.getCount());
        for (final Integer blockHeight : blockHeightInts) {
            blockHeights.add(blockHeight.longValue());
        }
        return blockHeights;
    }

    @Override
    public void close() throws Exception {
        _fileDb.close();
    }
}

class TransactionIndexEntryInflater implements EntryInflater<Sha256Hash, Integer> {
    @Override
    public Sha256Hash keyFromBytes(final ByteArray byteArray) {
        return Sha256Hash.wrap(byteArray.getBytes(0, Sha256Hash.BYTE_COUNT));
    }

    @Override
    public ByteArray keyToBytes(final Sha256Hash transactionHash) {
        return transactionHash;
    }

    @Override
    public int getKeyByteCount() {
        return Sha256Hash.BYTE_COUNT;
    }

    @Override
    public Integer valueFromBytes(final ByteArray byteArray) {
        if (byteArray.isEmpty()) { return null; } // Support null values.
        return ByteUtil.bytesToInteger(byteArray);
    }

    @Override
    public ByteArray valueToBytes(final Integer value) {
        if (value == null) { return new MutableByteArray(0); } // Support null values.

        return MutableByteArray.wrap(ByteUtil.integerToBytes(value));
    }

    @Override
    public int getValueByteCount(final Integer value) {
        if (value == null) { return 0; } // Support null values.
        return 4;
    }
}