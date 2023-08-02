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
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class Blockchain {
    protected final MutableHashMap<Sha256Hash, Long> _blockHeights = new MutableHashMap<>();
    protected final MutableList<BlockHeader> _blockHeaders = new MutableArrayList<>();
    protected final MutableList<MedianBlockTime> _medianBlockTimes = new MutableArrayList<>();
    protected final MutableList<ChainWork> _chainWorks = new MutableArrayList<>();
    protected final MutableHashMap<Sha256Hash, BlockHeader> _orphanedHeaders = new MutableHashMap<>();
    protected final BlockStore _blockStore;
    protected final UnspentTransactionOutputDatabaseManager _unspentTransactionOutputDatabaseManager;
    protected AsertReferenceBlock _asertReferenceBlock = null;
    protected Long _headBlockHeight = -1L;

    protected void _addBlockHeader(final BlockHeader blockHeader, final Long blockHeight, final MedianBlockTime medianBlockTime, final ChainWork chainWork) {
        final Sha256Hash blockHash = blockHeader.getHash();
        _blockHeaders.add(blockHeader);
        _medianBlockTimes.add(medianBlockTime);
        _chainWorks.add(chainWork);
        _blockHeights.put(blockHash, blockHeight);

        if (_blockStore.blockExists(blockHash, blockHeight)) {
            _headBlockHeight = Math.max(_headBlockHeight, blockHeight);
        }
    }

    protected void _addBlock(final Block block, final Long blockHeight) {
        _blockStore.storeBlock(block, blockHeight);
        _headBlockHeight = Math.max(_headBlockHeight, blockHeight);
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

    public Blockchain(final BlockStore blockStore, final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager) {
        _blockStore = blockStore;
        _unspentTransactionOutputDatabaseManager = unspentTransactionOutputDatabaseManager;
    }

    public void load(final File file) throws Exception {
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

        if (file.exists()) {
            final MutableChainWork currentChainWork = new MutableChainWork();
            final int bytesPerInteger = 4;
            final int bytesPerLong = 8;
            try (final InputStream inputStream = new BufferedInputStream(new FileInputStream(file), 8000)) {
                final MutableByteArray buffer = new MutableByteArray(BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT);

                long blockHeight = 0L;
                while (true) {
                    final BlockHeader blockHeader;
                    {
                        final int byteCount = inputStream.readNBytes(buffer.unwrap(), 0, buffer.getByteCount());
                        if (byteCount < BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT) { break; }
                        blockHeader = blockHeaderInflater.fromBytes(buffer);
                        if (blockHeader == null) { break; }
                    }

                    final MedianBlockTime medianBlockTime;
                    {
                        final int byteCount = inputStream.readNBytes(buffer.unwrap(), 0, bytesPerLong);
                        if (byteCount != bytesPerLong) { break; }
                        final Long value = ByteUtil.bytesToLong(buffer.getBytes(0, bytesPerLong));
                        medianBlockTime = MedianBlockTime.fromSeconds(value);
                    }

                    final Difficulty difficulty = blockHeader.getDifficulty();
                    final ChainWork chainWork = ChainWork.add(currentChainWork, difficulty.calculateWork());

                    _addBlockHeader(blockHeader, blockHeight, medianBlockTime, chainWork);
                    blockHeight += 1L;
                }
            }
        }

        if (_blockHeaders.isEmpty()) {
            final BlockInflater blockInflater = new BlockInflater();
            final ByteArray genesisBlockBytes = MutableByteArray.wrap(HexUtil.hexStringToByteArray(BitcoinConstants.getGenesisBlock()));
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(genesisBlockBytes);
            final Block block = blockInflater.fromBytes(genesisBlockBytes);
            final MedianBlockTime medianBlockTime = MedianBlockTime.fromSeconds(blockHeader.getTimestamp());
            final Difficulty difficulty = blockHeader.getDifficulty();
            final ChainWork chainWork = ChainWork.add(new MutableChainWork(), difficulty.calculateWork());

            _addBlockHeader(blockHeader, 0L, medianBlockTime, chainWork);
            _addBlock(block, 0L);

            Logger.debug("Added Genesis: " + blockHeader.getHash());
        }
    }

    public void save(final File file) throws Exception {
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

    public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
        if (blockHeight >= _medianBlockTimes.getCount()) { return null; }
        return _medianBlockTimes.get(blockHeight.intValue());
    }

    public ChainWork getChainWork(final Long blockHeight) {
        if (blockHeight >= _chainWorks.getCount()) { return null; }
        return _chainWorks.get(blockHeight.intValue());
    }

    public BlockHeader getBlockHeader(final Long blockHeight) {
        return _getBlockHeader(blockHeight);
    }

    public BlockHeader getBlockHeader(final Sha256Hash blockHash) {
        final Long blockHeight = _blockHeights.get(blockHash);

        if (blockHeight >= _blockHeaders.getCount()) { return null; }
        return _blockHeaders.get(blockHeight.intValue());
    }

    public Long getBlockHeight(final Sha256Hash blockHash) {
        return _blockHeights.get(blockHash);
    }

    public BlockHeader getParentBlockHeader(final Sha256Hash blockHash, final int parentCount) {
        final Long blockHeight = _blockHeights.get(blockHash);
        if (blockHeight == null) { return null; }

        final int parentBlockHeight = (int) (blockHeight - parentCount);
        if (parentBlockHeight < 0) { return null; }

        if (blockHeight >= _blockHeaders.getCount()) { return null; }
        return _blockHeaders.get(parentBlockHeight);
    }

    public BlockHeader getChildBlockHeader(final Sha256Hash blockHash, final int childCount) {
        final Long blockHeight = _blockHeights.get(blockHash);
        if (blockHeight == null) { return null; }

        final int childBlockHeight = (int) (blockHeight + childCount);
        if (blockHeight >= _blockHeaders.getCount()) { return null; }
        return _blockHeaders.get(childBlockHeight);
    }

    public Boolean addBlockHeader(final BlockHeader blockHeader) {
        final Sha256Hash blockHash = blockHeader.getHash();
        final Sha256Hash previousBlockHash = blockHeader.getPreviousBlockHash();
        final long blockHeight = _blockHeaders.getCount();
        if (blockHeight == 0L) {
            if (! Util.areEqual(BlockHeader.GENESIS_BLOCK_HASH, blockHash)) { return false; }
        }
        else {
            final BlockHeader headBlockHeader = _blockHeaders.get((int) (blockHeight - 1L));
            final Sha256Hash headBlockHeaderHash = headBlockHeader.getHash();
            if (! Util.areEqual(headBlockHeaderHash, previousBlockHash)) {
                _orphanedHeaders.put(blockHash, blockHeader);
                return false;
            }
        }

        final MedianBlockTime medianBlockTime = _calculateMedianBlockTime(blockHeader, blockHeight);
        final ChainWork currentChainWork = (blockHeight > 0L ? _chainWorks.get((int) (blockHeight - 1L)) : new MutableChainWork());
        final Difficulty difficulty = blockHeader.getDifficulty();
        final BlockWork blockWork = difficulty.calculateWork();
        final ChainWork newChainWork = ChainWork.add(currentChainWork, blockWork);

        _addBlockHeader(blockHeader, blockHeight, medianBlockTime, newChainWork);
        return true;
    }

    public Boolean addBlock(final Block block) {
        final Sha256Hash blockHash = block.getHash();
        final Long blockHeight = _blockHeights.get(blockHash);
        if (blockHeight == null) { return false; }

        _addBlock(block, blockHeight);
        return true;
    }

    public Long getHeadBlockHeaderHeight() {
        return (_blockHeaders.getCount() - 1L);
    }

    public Long getHeadBlockHeight() {
        return (_headBlockHeight < 0L ? null : _headBlockHeight);
    }

    public Sha256Hash getHeadBlockHeaderHash() {
        if (_blockHeaders.isEmpty()) { return BlockHeader.GENESIS_BLOCK_HASH; }

        final int blockHeaderCount = _blockHeights.getCount();
        final BlockHeader blockHeader = _blockHeaders.get(blockHeaderCount - 1);
        return blockHeader.getHash();
    }

    public Sha256Hash getHeadBlockHash() {
        if (_headBlockHeight < 0L) { return null; }
        final int blockIndex = _headBlockHeight.intValue();
        final BlockHeader blockHeader = _blockHeaders.get(blockIndex);
        return blockHeader.getHash();
    }

    public AsertReferenceBlock getAsertReferenceBlock() {
        return _asertReferenceBlock;
    }

    public void setAsertReferenceBlock(final AsertReferenceBlock asertReferenceBlock) {
        _asertReferenceBlock = asertReferenceBlock;
    }

    public UnspentTransactionOutputDatabaseManager getUnspentTransactionOutputDatabaseManager() {
        return _unspentTransactionOutputDatabaseManager;
    }
}
