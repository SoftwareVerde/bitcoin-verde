package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.util.ByteBuffer;
import com.softwareverde.bitcoin.util.IoUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;

import java.io.File;
import java.io.RandomAccessFile;

public class BlockStore {
    protected final BlockInflaters _blockInflaters;
    protected final String _blockDataDirectory;
    protected final Integer _blocksPerDirectoryCount = 2016; // About 2 weeks...

    protected final ByteBuffer _byteBuffer = new ByteBuffer();

    protected String _getBlockDataDirectory(final Long blockHeight) {
        final String blockDataDirectory = _blockDataDirectory;
        if (blockDataDirectory == null) { return null; }

        final Long blockHeightDirectory = (blockHeight / _blocksPerDirectoryCount);
        return (blockDataDirectory + "/" + blockHeightDirectory);
    }

    protected String _getBlockDataPath(final Sha256Hash blockHash, final Long blockHeight) {
        final String blockDataDirectory = _blockDataDirectory;
        if (blockDataDirectory == null) { return null; }

        final String blockHeightDirectory = _getBlockDataDirectory(blockHeight);
        return (blockHeightDirectory + "/" + blockHash);
    }

    public BlockStore(final String blockDataDirectory, final BlockInflaters blockInflaters) {
        _blockDataDirectory = blockDataDirectory;
        _blockInflaters = blockInflaters;
    }

    public void storeBlock(final Block block, final Long blockHeight) {
        if (_blockDataDirectory == null) { return; }

        final Sha256Hash blockHash = block.getHash();

        final String blockPath = _getBlockDataPath(blockHash, blockHeight);
        if (blockPath == null) { return; }

        if (IoUtil.fileExists(blockPath)) { return; }

        { // Create the directory, if necessary...
            final String dataDirectory = _getBlockDataDirectory(blockHeight);
            final File directory = new File(dataDirectory);
            if (! directory.exists()) {
                final Boolean mkdirSuccessful = directory.mkdirs();
                if (! mkdirSuccessful) {
                    Logger.warn("Unable to create block data directory: " + dataDirectory);
                    return;
                }
            }
        }

        final BlockDeflater blockDeflater = _blockInflaters.getBlockDeflater();
        final MutableByteArray byteArray = blockDeflater.toBytes(block);

        IoUtil.putFileContents(blockPath, byteArray.unwrap());
    }

    public void removeBlock(final Sha256Hash blockHash, final Long blockHeight) {
        if (_blockDataDirectory == null) { return; }

        final String blockPath = _getBlockDataPath(blockHash, blockHeight);
        if (blockPath == null) { return; }

        if (! IoUtil.fileExists(blockPath)) { return; }

        final File file = new File(blockPath);
        file.delete();
    }

    public Block getBlock(final Sha256Hash blockHash, final Long blockHeight) {
        if (_blockDataDirectory == null) { return null; }

        final String blockPath = _getBlockDataPath(blockHash, blockHeight);
        if (blockPath == null) { return null; }

        if (! IoUtil.fileExists(blockPath)) { return null; }
        final ByteArray blockBytes = MutableByteArray.wrap(IoUtil.getFileContents(blockPath));
        if (blockBytes == null) { return null; }

        final BlockInflater blockInflater = _blockInflaters.getBlockInflater();
        return blockInflater.fromBytes(blockBytes);
    }

    public ByteArray readFromBlock(final Sha256Hash blockHash, final Long blockHeight, final Long diskOffset, final Integer byteCount) {
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
                    _byteBuffer.returnRecycledBuffer(buffer);
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

    public String getBlockDataDirectory() {
        return _blockDataDirectory;
    }
}
