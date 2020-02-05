package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.util.IoUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteBuffer;

import java.io.File;
import java.io.RandomAccessFile;

public class BlockCache {
    protected final BlockInflaters _blockInflaters;
    protected final String _cachedBlockDirectory;
    protected final Integer _blocksPerCacheDirectory = 2016; // About 2 weeks...

    protected final ByteBuffer _byteBuffer = new ByteBuffer();

    protected String _getCachedBlockDirectory(final Long blockHeight) {
        final String cachedBlockDirectory = _cachedBlockDirectory;
        if (cachedBlockDirectory == null) { return null; }

        final Long blockHeightDirectory = (blockHeight / _blocksPerCacheDirectory);
        return (cachedBlockDirectory + "/" + blockHeightDirectory);
    }

    protected String _getCachedBlockPath(final Sha256Hash blockHash, final Long blockHeight) {
        final String cachedBlockDirectory = _cachedBlockDirectory;
        if (cachedBlockDirectory == null) { return null; }

        final String blockHeightDirectory = _getCachedBlockDirectory(blockHeight);
        return (blockHeightDirectory + "/" + blockHash);
    }

    public BlockCache(final String cachedBlockDirectory) {
        this(cachedBlockDirectory, new CoreInflater());
    }

    public BlockCache(final String cachedBlockDirectory, final BlockInflaters blockInflaters) {
        _blockInflaters = blockInflaters;
        _cachedBlockDirectory = cachedBlockDirectory;
    }

    public void cacheBlock(final Block block, final Long blockHeight) {
        if (_cachedBlockDirectory == null) { return; }

        final Sha256Hash blockHash = block.getHash();

        final String blockPath = _getCachedBlockPath(blockHash, blockHeight);
        if (blockPath == null) { return; }

        if (IoUtil.fileExists(blockPath)) { return; }

        { // Create the directory, if necessary...
            final String cacheDirectory = _getCachedBlockDirectory(blockHeight);
            final File directory = new File(cacheDirectory);
            if (! directory.exists()) {
                final Boolean mkdirSuccessful = directory.mkdirs();
                if (! mkdirSuccessful) {
                    Logger.warn("Unable to create block cache directory: " + cacheDirectory);
                    return;
                }
            }
        }

        final BlockDeflater blockDeflater = _blockInflaters.getBlockDeflater();
        final MutableByteArray byteArray = blockDeflater.toBytes(block);

        IoUtil.putFileContents(blockPath, byteArray.unwrap());
    }

    public Block getCachedBlock(final Sha256Hash blockHash, final Long blockHeight) {
        if (_cachedBlockDirectory == null) { return null; }

        final String blockPath = _getCachedBlockPath(blockHash, blockHeight);
        if (blockPath == null) { return null; }

        if (! IoUtil.fileExists(blockPath)) { return null; }
        final ByteArray blockBytes = MutableByteArray.wrap(IoUtil.getFileContents(blockPath));
        if (blockBytes == null) { return null; }

        final BlockInflater blockInflater = _blockInflaters.getBlockInflater();
        return blockInflater.fromBytes(blockBytes);
    }

    public ByteArray readFromBlock(final Sha256Hash blockHash, final Long blockHeight, final Long diskOffset, final Integer byteCount) {
        if (_cachedBlockDirectory == null) { return null; }

        final String blockPath = _getCachedBlockPath(blockHash, blockHeight);
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

                if (totalBytesRead < byteCount) { return null; }
            }

            return byteArray;
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return null;
        }
    }

    public String getCachedBlockDirectory() {
        return _cachedBlockDirectory;
    }
}
