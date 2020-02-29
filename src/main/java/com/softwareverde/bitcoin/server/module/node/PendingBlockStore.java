package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.util.IoUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;

import java.io.File;

public class PendingBlockStore extends BlockStore {
    protected final String _pendingBlockDataDirectory;

    protected String _getPendingBlockDataDirectory(final Sha256Hash blockHash) {
        final String cachedBlockDirectory = _pendingBlockDataDirectory;
        if (cachedBlockDirectory == null) { return null; }

        final String blockHashString = blockHash.toString();
        int zeroCount = 0;
        for (final char c : blockHashString.toCharArray()) {
            if (c != '0') { break; }
            zeroCount += 1;
        }

        return (cachedBlockDirectory + "/" + zeroCount);
    }

    protected String _getPendingBlockDataPath(final Sha256Hash blockHash) {
        final String pendingBlockDataDirectory = _pendingBlockDataDirectory;
        if (pendingBlockDataDirectory == null) { return null; }

        final String blockHeightDirectory = _getPendingBlockDataDirectory(blockHash);
        return (blockHeightDirectory + "/" + blockHash);
    }

    public PendingBlockStore(final String blockDataDirectory, final String pendingBlockDataDirectory, final BlockInflaters blockInflaters) {
        super(blockDataDirectory, blockInflaters);
        _pendingBlockDataDirectory = pendingBlockDataDirectory;
    }

    public Boolean storePendingBlock(final Block block) {
        if (_pendingBlockDataDirectory == null) { return false; }

        final Sha256Hash blockHash = block.getHash();

        final String blockPath = _getPendingBlockDataPath(blockHash);
        if (blockPath == null) { return false; }

        if (IoUtil.fileExists(blockPath)) { return true; }

        { // Create the directory, if necessary...
            final String cacheDirectory = _getPendingBlockDataDirectory(blockHash);
            final File directory = new File(cacheDirectory);
            if (! directory.exists()) {
                final Boolean mkdirSuccessful = directory.mkdirs();
                if (! mkdirSuccessful) {
                    Logger.warn("Unable to create block cache directory: " + cacheDirectory);
                    return false;
                }
            }
        }

        final BlockDeflater blockDeflater = _blockInflaters.getBlockDeflater();
        final MutableByteArray byteArray = blockDeflater.toBytes(block);

        return IoUtil.putFileContents(blockPath, byteArray.unwrap());
    }

    public void removePendingBlock(final Sha256Hash blockHash) {
        if (_pendingBlockDataDirectory == null) { return; }

        final String blockPath = _getPendingBlockDataPath(blockHash);
        if (blockPath == null) { return; }

        if (! IoUtil.fileExists(blockPath)) { return; }

        final File file = new File(blockPath);
        // file.delete();
    }

    public ByteArray getPendingBlockData(final Sha256Hash blockHash) {
        if (_pendingBlockDataDirectory == null) { return null; }

        final String blockPath = _getPendingBlockDataPath(blockHash);
        if (blockPath == null) { return null; }

        if (! IoUtil.fileExists(blockPath)) { return null; }
        return MutableByteArray.wrap(IoUtil.getFileContents(blockPath));
    }

    public Boolean pendingBlockExists(final Sha256Hash blockHash) {
        if (_pendingBlockDataDirectory == null) { return false; }

        final String blockPath = _getPendingBlockDataPath(blockHash);
        if (blockPath == null) { return false; }

        return IoUtil.fileExists(blockPath);
    }

    public String getPendingBlockDataDirectory() {
        return _pendingBlockDataDirectory;
    }
}
