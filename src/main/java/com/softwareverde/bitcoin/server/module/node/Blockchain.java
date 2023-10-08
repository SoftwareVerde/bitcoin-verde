package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.BlockWork;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.header.difficulty.work.MutableChainWork;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.configuration.CheckpointConfiguration;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.filedb.WorkerManager;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.timer.NanoTimer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Blockchain {
    protected final ReentrantReadWriteLock.WriteLock _writeLock;
    protected final ReentrantReadWriteLock.ReadLock _readLock;

    protected final MutableHashMap<Sha256Hash, Long> _blockHeights = new MutableHashMap<>();
    protected final MutableArrayList<BlockHeader> _blockHeaders = new MutableArrayList<>();
    protected final MutableArrayList<MedianBlockTime> _medianBlockTimes = new MutableArrayList<>();
    protected final MutableArrayList<ChainWork> _chainWorks = new MutableArrayList<>();
    protected final BlockStore _blockStore;
    protected final CheckpointConfiguration _checkpointConfiguration;
    protected final UnspentTransactionOutputDatabaseManager _unspentTransactionOutputDatabaseManager;
    protected AsertReferenceBlock _asertReferenceBlock = null;
    protected Long _headBlockHeight = -1L;

    protected boolean _addBlockHeader(final BlockHeader blockHeader, final Long blockHeight, final MedianBlockTime medianBlockTime, final ChainWork chainWork) {
        final Sha256Hash blockHash = blockHeader.getHash();

        if (! blockHeader.isValid()) {
            Logger.debug("Corrupted block header " + blockHash + " at height " + blockHeight + ".");
            return false;
        }

        if ( (_checkpointConfiguration != null) && _checkpointConfiguration.violatesCheckpoint(blockHeight, blockHash) ) {
            Logger.debug(blockHash + " violates checkpoint for height " + blockHeight + ".");
            return false;
        }

        _blockHeaders.add(blockHeader);
        _medianBlockTimes.add(medianBlockTime);
        _chainWorks.add(chainWork);
        _blockHeights.put(blockHash, blockHeight);

        if ( _headBlockHeight == (blockHeight - 1L) ) {
            if (_blockStore.blockExists(blockHash, blockHeight)) {
                _headBlockHeight = Math.max(_headBlockHeight, blockHeight);
            }
        }
        return true;
    }

    protected boolean _addBlock(final Block block, final Long blockHeight) {
        final boolean storeResult = _blockStore.storeBlock(block, blockHeight);
        if (! storeResult) { return false; }

        _headBlockHeight = Math.max(_headBlockHeight, blockHeight);
        return true;
    }

    protected BlockHeader _getBlockHeader(final Long blockHeight) {
        if (blockHeight >= _blockHeaders.getCount()) { return null; }
        return _blockHeaders.get(blockHeight.intValue());
    }

    protected MutableMedianBlockTime _calculateMedianBlockTime(final BlockHeader forBlockHeader, final Long blockHeight) {
        final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();

        final MutableList<BlockHeader> blockHeadersInDescendingOrder = new MutableArrayList<>(MedianBlockTime.BLOCK_COUNT);
        blockHeadersInDescendingOrder.add(forBlockHeader);

        for (int i = 0; i < MedianBlockTime.BLOCK_COUNT - 1; ++i) {
            final long index = (blockHeight - 1L - i);
            if (index < 0L) { break; }

            final BlockHeader blockHeader = _getBlockHeader(index);
            if (blockHeader == null) { break; }
            blockHeadersInDescendingOrder.add(blockHeader);
        }

        // Add the blocks to the MedianBlockTime in ascending order (lowest block-height is added first)...
        final int blockHeaderCount = blockHeadersInDescendingOrder.getCount();
        for (int i = 0; i < blockHeaderCount; ++i) {
            final BlockHeader blockHeader = blockHeadersInDescendingOrder.get(blockHeaderCount - i - 1);
            medianBlockTime.addBlock(blockHeader);
        }

        return medianBlockTime;
    }

    protected void _load(final File file) throws Exception {
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

        if (file.exists()) {
            final MutableChainWork currentChainWork = new MutableChainWork();
            final int bytesPerInteger = 4;
            final int bytesPerLong = 8;

            final long totalFileSize = file.length();
            long totalBytesRead = 0L;
            int progressReportCount = 0;

            try (final InputStream inputStream = new BufferedInputStream(new FileInputStream(file), (BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT + bytesPerLong) * 1000)) {
                final MutableByteArray buffer = new MutableByteArray(BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT);

                double blockHeaderTime = 0D;
                double medianBlockTimeTime = 0D;
                double chainWorkTime = 0D;

                try (final WorkerManager workerManager = new WorkerManager(1, 1)) {
                    workerManager.setName("Blockchain Anonymous");
                    workerManager.start();

                    final AtomicBoolean shouldContinue = new AtomicBoolean(true);

                    long i = 0L;
                    while (shouldContinue.get()) {
                        final long blockHeight = i;
                        final NanoTimer nanoTimer = new NanoTimer();

                        nanoTimer.start();
                        final BlockHeader blockHeader;
                        {
                            final int byteCount = inputStream.readNBytes(buffer.unwrap(), 0, buffer.getByteCount());
                            if (byteCount < BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT) { break; }
                            totalBytesRead += byteCount;
                            blockHeader = blockHeaderInflater.fromBytes(buffer);
                            if (blockHeader == null) { break; }
                        }
                        nanoTimer.stop();
                        blockHeaderTime += nanoTimer.getMillisecondsElapsed();

                        nanoTimer.start();
                        final MedianBlockTime medianBlockTime;
                        {
                            final int byteCount = inputStream.readNBytes(buffer.unwrap(), 0, bytesPerLong);
                            if (byteCount != bytesPerLong) { break; }
                            totalBytesRead += byteCount;
                            final Long value = ByteUtil.bytesToLong(buffer.getBytes(0, bytesPerLong));
                            medianBlockTime = MedianBlockTime.fromSeconds(value);
                        }
                        nanoTimer.stop();
                        medianBlockTimeTime += nanoTimer.getMillisecondsElapsed();

                        // NOTE: WorkerManager's queue must be 1 in order to prevent out-of-order insertion of BlockHeaders.
                        workerManager.submitTask(new WorkerManager.Task() {
                            @Override
                            public void run() {
                                final Difficulty difficulty = blockHeader.getDifficulty();
                                currentChainWork.add(difficulty.calculateWork());

                                final ChainWork chainWork = currentChainWork.asConst();
                                nanoTimer.stop();

                                final boolean wasValid = _addBlockHeader(blockHeader, blockHeight, medianBlockTime, chainWork);
                                if (! wasValid) {
                                    shouldContinue.set(false);
                                }
                            }
                        });

                        final long readPercent = (totalBytesRead * 100L) / totalFileSize;
                        if (readPercent >= 10L * (progressReportCount + 1L)) {
                            progressReportCount += 1;
                            Logger.debug("Loading Blockchain: " + readPercent + "%");
                        }

                        i += 1L;
                    }

                    workerManager.waitForCompletion();
                }

                Logger.debug("blockHeaderTime=" + blockHeaderTime + ", medianBlockTimeTime=" + medianBlockTimeTime + ", chainWorkTime=" + chainWorkTime);
                Logger.debug("headBlockHeight=" + _headBlockHeight);
            }
        }

        if (_blockHeaders.isEmpty() || _headBlockHeight < 0L) {
            final BlockInflater blockInflater = new BlockInflater();
            final ByteArray genesisBlockBytes = MutableByteArray.wrap(HexUtil.hexStringToByteArray(BitcoinConstants.getGenesisBlock()));

            if (_blockHeaders.isEmpty()) {
                final BlockHeader blockHeader = blockHeaderInflater.fromBytes(genesisBlockBytes);
                final MedianBlockTime medianBlockTime = MedianBlockTime.fromSeconds(blockHeader.getTimestamp());
                final Difficulty difficulty = blockHeader.getDifficulty();
                final ChainWork chainWork = ChainWork.add(new MutableChainWork(), difficulty.calculateWork());

                _addBlockHeader(blockHeader, 0L, medianBlockTime, chainWork);
                Logger.debug("Added Genesis Header: " + blockHeader.getHash());
            }

            if (_headBlockHeight < 0L) {
                final Block block = blockInflater.fromBytes(genesisBlockBytes);
                final boolean addBlockResult = _addBlock(block, 0L);
                if (!addBlockResult) { throw new Exception("Unable to store Genesis Block."); }
                Logger.debug("Added Genesis Block: " + block.getHash());
            }
        }
    }

    protected void _save(final File file) throws Exception {
        if (file.exists()) {
            file.delete();
        }

        try (final OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file))) {
            final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
            int blockHeight = 0;
            for (final BlockHeader blockHeader : _blockHeaders) {
                final MedianBlockTime medianBlockTime = _medianBlockTimes.get(blockHeight);
                final Long medianBlockTimeValue = medianBlockTime.getCurrentTimeInSeconds();

                final ByteArray bytes = blockHeaderDeflater.toBytes(blockHeader);
                outputStream.write(bytes.getBytes());
                outputStream.write(ByteUtil.longToBytes(medianBlockTimeValue));
                blockHeight += 1;
            }
        }
    }

    public Blockchain(final BlockStore blockStore) {
        this(blockStore, null, null);
    }

    public Blockchain(final BlockStore blockStore, final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager, final CheckpointConfiguration checkpointConfiguration) {
        _blockStore = blockStore;
        _unspentTransactionOutputDatabaseManager = unspentTransactionOutputDatabaseManager;
        _checkpointConfiguration = checkpointConfiguration;

        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _readLock = readWriteLock.readLock();
        _writeLock = readWriteLock.writeLock();
    }

    public void load(final File file) throws Exception {
        _writeLock.lock();
        try {
            _load(file);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void save(final File file) throws Exception {
        _writeLock.lock();
        try {
            _save(file);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
        _readLock.lock();
        try {
            if (blockHeight >= _medianBlockTimes.getCount()) { return null; }
            if (blockHeight < 0L) { return null; }
            return _medianBlockTimes.get(blockHeight.intValue());
        }
        finally {
            _readLock.unlock();
        }
    }

    public ChainWork getChainWork(final Long blockHeight) {
        _readLock.lock();
        try {
            if (blockHeight >= _chainWorks.getCount()) { return null; }
            if (blockHeight < 0L) { return null; }
            return _chainWorks.get(blockHeight.intValue());
        }
        finally {
            _readLock.unlock();
        }
    }

    public BlockHeader getBlockHeader(final Long blockHeight) {
        _readLock.lock();
        try {
            return _getBlockHeader(blockHeight);
        }
        finally {
            _readLock.unlock();
        }
    }

    public BlockHeader getBlockHeader(final Sha256Hash blockHash) {
        _readLock.lock();
        try {
            final Long blockHeight = _blockHeights.get(blockHash);
            if (blockHeight >= _blockHeaders.getCount()) { return null; }
            if (blockHeight < 0L) { return null; }
            return _blockHeaders.get(blockHeight.intValue());
        }
        finally {
            _readLock.unlock();
        }
    }

    public Long getBlockHeight(final Sha256Hash blockHash) {
        _readLock.lock();
        try {
            return _blockHeights.get(blockHash);
        }
        finally {
            _readLock.unlock();
        }
    }

    public BlockHeader getParentBlockHeader(final Sha256Hash blockHash, final int parentCount) {
        _readLock.lock();
        try {
            final Long blockHeight = _blockHeights.get(blockHash);
            if (blockHeight == null) { return null; }

            final int parentBlockHeight = (int) (blockHeight - parentCount);
            if (parentBlockHeight >= _blockHeaders.getCount()) { return null; }
            if (parentBlockHeight < 0L) { return null; }
            return _blockHeaders.get(parentBlockHeight);
        }
        finally {
            _readLock.unlock();
        }
    }

    public BlockHeader getChildBlockHeader(final Sha256Hash blockHash, final int childCount) {
        _readLock.lock();
        try {
            final Long blockHeight = _blockHeights.get(blockHash);
            if (blockHeight == null) { return null; }

            final int childBlockHeight = (int) (blockHeight + childCount);
            if (childBlockHeight >= _blockHeaders.getCount()) { return null; }
            if (childBlockHeight < 0L) { return null; }
            return _blockHeaders.get(childBlockHeight);
        }
        finally {
            _readLock.unlock();
        }
    }

    public Integer getTransactionCount(final Long blockHeight) {
        _readLock.lock();
        try {
            if (blockHeight >= _blockHeaders.getCount()) { return null; }
            if (blockHeight < 0L) { return null; }
            final BlockHeader blockHeader = _blockHeaders.get(blockHeight.intValue());
            if (blockHeader == null) { return null; }

            if (blockHeight > _headBlockHeight) { return null; }

            final Sha256Hash blockHash = blockHeader.getHash();
            final long diskOffset = BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT;
            final ByteArray transactionCountBytes = _blockStore.readFromBlock(blockHash, blockHeight, diskOffset, CompactVariableLengthInteger.MAX_BYTE_COUNT);
            final CompactVariableLengthInteger compactVariableLengthInteger = CompactVariableLengthInteger.readVariableLengthInteger(new ByteArrayReader(transactionCountBytes));
            return compactVariableLengthInteger.intValue();
        }
        finally {
            _readLock.unlock();
        }
    }

    public Long getBlockByteCount(final Long blockHeight) {
        _readLock.lock();
        try {
            if (blockHeight >= _blockHeaders.getCount()) { return null; }
            if (blockHeight < 0L) { return null; }
            final BlockHeader blockHeader = _blockHeaders.get(blockHeight.intValue());
            if (blockHeader == null) { return null; }

            if (blockHeight > _headBlockHeight) { return null; }

            final Sha256Hash blockHash = blockHeader.getHash();
            return _blockStore.getBlockByteCount(blockHash, blockHeight);
        }
        finally {
            _readLock.unlock();
        }
    }

    public Boolean addBlockHeader(final BlockHeader blockHeader) {
        _writeLock.lock();
        try {
            final Sha256Hash blockHash = blockHeader.getHash();
            final Sha256Hash previousBlockHash = blockHeader.getPreviousBlockHash();
            final long blockHeight = _blockHeaders.getCount();
            if (blockHeight == 0L) {
                if (!Util.areEqual(BlockHeader.GENESIS_BLOCK_HASH, blockHash)) { return false; }
            }
            else {
                final BlockHeader headBlockHeader = _blockHeaders.get((int) (blockHeight - 1L));
                final Sha256Hash headBlockHeaderHash = headBlockHeader.getHash();
                if (!Util.areEqual(headBlockHeaderHash, previousBlockHash)) { return false; }
            }

            final MedianBlockTime medianBlockTime = _calculateMedianBlockTime(blockHeader, blockHeight);
            final ChainWork currentChainWork = (blockHeight > 0L ? _chainWorks.get((int) (blockHeight - 1L)) : new MutableChainWork());
            final Difficulty difficulty = blockHeader.getDifficulty();
            final BlockWork blockWork = difficulty.calculateWork();
            final ChainWork newChainWork = ChainWork.add(currentChainWork, blockWork);

            return _addBlockHeader(blockHeader, blockHeight, medianBlockTime, newChainWork);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public Boolean addBlock(final Block block) {
        _writeLock.lock();
        try {
            final Sha256Hash blockHash = block.getHash();
            final Long blockHeight = _blockHeights.get(blockHash);
            if (blockHeight == null) { return false; }

            final BlockHeader headBlockHeader = _getBlockHeader(_headBlockHeight);
            if (headBlockHeader != null) {
                final Sha256Hash headBlockHash = headBlockHeader.getHash();
                if (! Util.areEqual(headBlockHash, block.getPreviousBlockHash())) {
                    return false;
                }
            }

            return _addBlock(block, blockHeight);
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void undoHeadBlockHeader() {
        _writeLock.lock();
        try {
            if (_blockHeaders.isEmpty()) { return; }

            final int blockHeaderCount = _blockHeaders.getCount();
            final int index = blockHeaderCount - 1;
            final Long blockHeight = (long) index;
            final BlockHeader headBlock = _blockHeaders.get(index);
            final Sha256Hash blockHash = headBlock.getHash();

            _blockHeaders.remove(index);
            _medianBlockTimes.remove(index);
            _chainWorks.remove(index);
            _blockHeights.remove(blockHash);

            if (_blockStore.blockExists(blockHash, blockHeight)) {
                _blockStore.removeBlock(blockHash, blockHeight);
                _headBlockHeight -= 1;
            }
        }
        finally {
            _writeLock.unlock();
        }
    }

    public Long getHeadBlockHeaderHeight() {
        _readLock.lock();
        try {
            return (_blockHeaders.getCount() - 1L);
        }
        finally {
            _readLock.unlock();
        }
    }

    public Long getHeadBlockHeight() {
        _readLock.lock();
        try {
            return (_headBlockHeight < 0L ? null : _headBlockHeight);
        }
        finally {
            _readLock.unlock();
        }
    }

    public Sha256Hash getHeadBlockHeaderHash() {
        _readLock.lock();
        try {
            if (_blockHeaders.isEmpty()) { return BlockHeader.GENESIS_BLOCK_HASH; }

            final int blockHeaderCount = _blockHeights.getCount();
            final BlockHeader blockHeader = _blockHeaders.get(blockHeaderCount - 1);
            return blockHeader.getHash();
        }
        finally {
            _readLock.unlock();
        }
    }

    public Sha256Hash getHeadBlockHash() {
        _readLock.lock();
        try {
            if (_headBlockHeight < 0L) { return null; }
            final int blockIndex = _headBlockHeight.intValue();
            final BlockHeader blockHeader = _blockHeaders.get(blockIndex);
            return blockHeader.getHash();
        }
        finally {
            _readLock.unlock();
        }
    }

    public AsertReferenceBlock getAsertReferenceBlock() {
        _readLock.lock();
        try {
            return _asertReferenceBlock;
        }
        finally {
            _readLock.unlock();
        }
    }

    public void setAsertReferenceBlock(final AsertReferenceBlock asertReferenceBlock) {
        _writeLock.lock();
        try {
            _asertReferenceBlock = asertReferenceBlock;
        }
        finally {
            _writeLock.unlock();
        }
    }

    public UnspentTransactionOutputDatabaseManager getUnspentTransactionOutputDatabaseManager() {
        return _unspentTransactionOutputDatabaseManager;
    }

    public Transaction getTransaction(final IndexedTransaction indexedTransaction) {
        final TransactionInflater transactionInflater = new TransactionInflater();

        final int blockHeaderIndex = indexedTransaction.blockHeight.intValue();
        if (blockHeaderIndex >= _blockHeaders.getCount()) { return null; }

        final BlockHeader blockHeader = _blockHeaders.get(blockHeaderIndex);
        final Sha256Hash blockHash = blockHeader.getHash();

        final ByteArray transactionBytes = _blockStore.readFromBlock(blockHash, indexedTransaction.blockHeight, indexedTransaction.diskOffset, indexedTransaction.byteCount);
        return transactionInflater.fromBytes(transactionBytes);
    }

    public List<Transaction> getTransactions(final List<IndexedTransaction> indexedTransactions) {
        final int transactionCount = indexedTransactions.getCount();
        final TransactionInflater transactionInflater = new TransactionInflater();
        final MutableHashMap<Sha256Hash, MutableList<IndexedTransaction>> blockTransactions = new MutableHashMap<>();

        for (final IndexedTransaction indexedTransaction : indexedTransactions) {
            final int blockHeaderIndex = indexedTransaction.blockHeight.intValue();
            if (blockHeaderIndex >= _blockHeaders.getCount()) { return null; }

            final BlockHeader blockHeader = _blockHeaders.get(blockHeaderIndex);
            final Sha256Hash blockHash = blockHeader.getHash();

            MutableList<IndexedTransaction> queuedTransactions = blockTransactions.get(blockHash);
            if (queuedTransactions == null) {
                queuedTransactions = new MutableArrayList<>();
                blockTransactions.put(blockHash, queuedTransactions);
            }
            queuedTransactions.add(indexedTransaction);
        }

        final MutableMap<IndexedTransaction, Transaction> loadedTransactions = new MutableHashMap<>(transactionCount);
        for (final Sha256Hash blockHash : blockTransactions.getKeys()) {
            final MutableList<IndexedTransaction> transactions = blockTransactions.get(blockHash);
            transactions.sort(new Comparator<>() {
                @Override
                public int compare(final IndexedTransaction indexedTransaction0, final IndexedTransaction indexedTransaction1) {
                    return indexedTransaction0.diskOffset.compareTo(indexedTransaction1.diskOffset);
                }
            });

            for (final IndexedTransaction indexedTransaction : transactions) {
                // TODO: optimize BlockStore to read from multiple section of the same block without opening the file each time.
                final ByteArray transactionBytes = _blockStore.readFromBlock(blockHash, indexedTransaction.blockHeight, indexedTransaction.diskOffset, indexedTransaction.byteCount);
                final Transaction transaction = transactionInflater.fromBytes(transactionBytes);
                loadedTransactions.put(indexedTransaction, transaction);
            }
        }

        final MutableList<Transaction> transactions = new MutableArrayList<>(transactionCount);
        for (final IndexedTransaction indexedTransaction : indexedTransactions) {
            final Transaction transaction = loadedTransactions.get(indexedTransaction);
            transactions.add(transaction);
        }
        return transactions;
    }
}
