package com.softwareverde.bitcoin.server.module.node.store;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.inflater.BlockHeaderInflaters;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteBuffer;
import com.softwareverde.util.IoUtil;

import java.io.File;
import java.io.RandomAccessFile;

public class BlockStoreCore implements BlockStore {
    protected final BlockHeaderInflaters _blockHeaderInflaters;
    protected final BlockInflaters _blockInflaters;
    protected final String _dataDirectory;
    protected final String _blockDataDirectory;
    protected final Integer _blocksPerDirectoryCount = 2016; // About 2 weeks...

    protected final ByteBuffer _byteBuffer = new ByteBuffer();

    protected String _getBlockDataDirectory(final Long blockHeight) {
        final String blockDataDirectory = _blockDataDirectory;
        if (blockDataDirectory == null) { return null; }

        final long blockHeightDirectory = (blockHeight / _blocksPerDirectoryCount);
        return (blockDataDirectory + "/" + blockHeightDirectory);
    }

    protected String _getBlockDataPath(final Sha256Hash blockHash, final Long blockHeight) {
        if (_blockDataDirectory == null) { return null; }

        final String blockHeightDirectory = _getBlockDataDirectory(blockHeight);
        return (blockHeightDirectory + "/" + blockHash);
    }

    protected ByteArray _readFromBlock(final Sha256Hash blockHash, final Long blockHeight, final Long diskOffset, final Integer byteCount) {
        if (_blockDataDirectory == null) { return null; }

        final String blockPath = _getBlockDataPath(blockHash, blockHeight);
        if (blockPath == null) { return null; }

        if (! IoUtil.fileExists(blockPath)) { return null; }

        try {
            final MutableByteArray byteArray = new MutableByteArray(byteCount);

            try (final RandomAccessFile file = new RandomAccessFile(new File(blockPath), "r")) {
                file.seek(diskOffset);

                final byte[] buffer;
                synchronized (_byteBuffer) {
                    buffer = _byteBuffer.getRecycledBuffer();
                }

                int totalBytesRead = 0;
                while (totalBytesRead < byteCount) {
                    final int byteCountRead = file.read(buffer);
                    if (byteCountRead < 0) { break; }

                    byteArray.setBytes(totalBytesRead, buffer);
                    totalBytesRead += byteCountRead;
                }

                synchronized (_byteBuffer) {
                    _byteBuffer.recycleBuffer(buffer);
                }

                if (totalBytesRead < byteCount) { return null; }
            }

            return byteArray;
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return null;
        }
    }

    public BlockStoreCore(final String dataDirectory, final BlockHeaderInflaters blockHeaderInflaters, final BlockInflaters blockInflaters) {
        _dataDirectory = dataDirectory;
        _blockDataDirectory = (dataDirectory != null ? (dataDirectory + "/" + BitcoinProperties.DATA_DIRECTORY_NAME + "/blocks") : null);
        _blockInflaters = blockInflaters;
        _blockHeaderInflaters = blockHeaderInflaters;
    }

    @Override
    public Boolean storeBlock(final Block block, final Long blockHeight) {
        if (_blockDataDirectory == null) { return false; }
        if (block == null) { return false; }

        final Sha256Hash blockHash = block.getHash();

        final String blockPath = _getBlockDataPath(blockHash, blockHeight);
        if (blockPath == null) { return false; }

        if (! IoUtil.isEmpty(blockPath)) { return true; }

        { // Create the directory, if necessary...
            final String dataDirectory = _getBlockDataDirectory(blockHeight);
            final File directory = new File(dataDirectory);
            if (! directory.exists()) {
                final boolean mkdirSuccessful = directory.mkdirs();
                if (! mkdirSuccessful) {
                    Logger.warn("Unable to create block data directory: " + dataDirectory);
                    return false;
                }
            }
        }

        final BlockDeflater blockDeflater = _blockInflaters.getBlockDeflater();
        final ByteArray byteArray = blockDeflater.toBytes(block);

        if (Logger.isTraceEnabled()) {
            if (IoUtil.fileExists(blockPath)) {
                Logger.trace("Overwriting existing block: " + blockHash, new Exception());
            }
        }

        return IoUtil.putFileContents(blockPath, byteArray);
    }

    @Override
    public void removeBlock(final Sha256Hash blockHash, final Long blockHeight) {
        if (_blockDataDirectory == null) { return; }

        final String blockPath = _getBlockDataPath(blockHash, blockHeight);
        if (blockPath == null) { return; }

        if (! IoUtil.fileExists(blockPath)) { return; }

        final File file = new File(blockPath);
        file.delete();
    }

    @Override
    public MutableBlockHeader getBlockHeader(final Sha256Hash blockHash, final Long blockHeight) {
        if (_blockDataDirectory == null) { return null; }

        final String blockPath = _getBlockDataPath(blockHash, blockHeight);
        if (blockPath == null) { return null; }

        if (! IoUtil.fileExists(blockPath)) { return null; }
        final ByteArray blockBytes = _readFromBlock(blockHash, blockHeight, 0L, BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT);
        if (blockBytes == null) { return null; }

        final BlockHeaderInflater blockHeaderInflater = _blockHeaderInflaters.getBlockHeaderInflater();
        return blockHeaderInflater.fromBytes(blockBytes);
    }

    @Override
    public MutableBlock getBlock(final Sha256Hash blockHash, final Long blockHeight) {
        if (_blockDataDirectory == null) { return null; }

        final String blockPath = _getBlockDataPath(blockHash, blockHeight);
        if (blockPath == null) { return null; }

        if (! IoUtil.fileExists(blockPath)) { return null; }
        final ByteArray blockBytes = MutableByteArray.wrap(IoUtil.getFileContents(blockPath));
        if (blockBytes == null) { return null; }

        final BlockInflater blockInflater = _blockInflaters.getBlockInflater();
        return blockInflater.fromBytes(blockBytes);
    }

    @Override
    public Boolean blockExists(final Sha256Hash blockHash, final Long blockHeight) {
        if (_blockDataDirectory == null) { return false; }

        final String blockPath = _getBlockDataPath(blockHash, blockHeight);
        if (blockPath == null) { return false; }

        return IoUtil.fileExists(blockPath);
    }

    @Override
    public ByteArray readFromBlock(final Sha256Hash blockHash, final Long blockHeight, final Long diskOffset, final Integer byteCount) {
        return _readFromBlock(blockHash, blockHeight, diskOffset, byteCount);
    }

    @Override
    public String getDataDirectory() {
        return _dataDirectory;
    }

    public String getBlockDataDirectory() {
        return _blockDataDirectory;
    }
}
