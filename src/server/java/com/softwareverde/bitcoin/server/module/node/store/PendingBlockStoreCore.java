package com.softwareverde.bitcoin.server.module.node.store;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.inflater.BlockHeaderInflaters;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.IoUtil;

import java.io.File;

public class PendingBlockStoreCore extends BlockStoreCore implements PendingBlockStore {
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
        if (_pendingBlockDataDirectory == null) { return null; }

        final String blockHeightDirectory = _getPendingBlockDataDirectory(blockHash);
        return (blockHeightDirectory + "/" + blockHash);
    }

    protected void _deletePendingBlockData(final String blockPath) {
        final File file = new File(blockPath);
        file.delete();
    }

    public PendingBlockStoreCore(final String dataDirectory, final BlockHeaderInflaters blockHeaderInflaters, final BlockInflaters blockInflaters) {
        super(dataDirectory, blockHeaderInflaters, blockInflaters);
        _pendingBlockDataDirectory = (dataDirectory != null ? (dataDirectory + "/" + BitcoinProperties.DATA_DIRECTORY_NAME + "/pending-blocks") : null);
    }

    @Override
    public Boolean storePendingBlock(final Block block) {
        if (_pendingBlockDataDirectory == null) { return false; }

        final Sha256Hash blockHash = block.getHash();

        final String blockPath = _getPendingBlockDataPath(blockHash);
        if (blockPath == null) { return false; }

        if (! IoUtil.isEmpty(blockPath)) { return true; }

        { // Create the directory, if necessary...
            final String cacheDirectory = _getPendingBlockDataDirectory(blockHash);
            final File directory = new File(cacheDirectory);
            if (! directory.exists()) {
                final boolean mkdirSuccessful = directory.mkdirs();
                if (! mkdirSuccessful) {
                    Logger.warn("Unable to create block cache directory: " + cacheDirectory);
                    return false;
                }
            }
        }

        final BlockDeflater blockDeflater = _blockInflaters.getBlockDeflater();
        final ByteArray byteArray = blockDeflater.toBytes(block);

        return IoUtil.putFileContents(blockPath, byteArray);
    }

    @Override
    public void removePendingBlock(final Sha256Hash blockHash) {
        if (_pendingBlockDataDirectory == null) { return; }

        final String blockPath = _getPendingBlockDataPath(blockHash);
        if (blockPath == null) { return; }

        if (! IoUtil.fileExists(blockPath)) { return; }

        _deletePendingBlockData(blockPath);
    }

    @Override
    public ByteArray getPendingBlockData(final Sha256Hash blockHash) {
        if (_pendingBlockDataDirectory == null) { return null; }

        final String blockPath = _getPendingBlockDataPath(blockHash);
        if (blockPath == null) {
            Logger.trace("Unable to create block path for block: " + blockHash);
            return null;
        }

        if (! IoUtil.fileExists(blockPath)) {
            Logger.trace("Block file does not exist: " + blockPath);
            return null;
        }
        return MutableByteArray.wrap(IoUtil.getFileContents(blockPath));
    }

    @Override
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
