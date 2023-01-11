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
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.IoUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteBuffer;
import com.softwareverde.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;

public class BlockStoreCore implements BlockStore {
    protected static final int GZIP_BUFFER_SIZE = (int) (ByteUtil.Unit.Binary.MEBIBYTES / 2);
    protected static final int READ_BUFFER_SIZE = (int) (16L * ByteUtil.Unit.Binary.KIBIBYTES);

    protected final BlockHeaderInflaters _blockHeaderInflaters;
    protected final BlockInflaters _blockInflaters;
    protected final String _dataDirectory;
    protected final String _blockDataDirectory;
    protected final Integer _blocksPerDirectoryCount = 2016; // About 2 weeks...
    protected final Boolean _blockCompressionIsEnabled;

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

    protected ByteArray _readCompressedInternal(final String blockPath) throws ZipException {
        final ByteBuffer byteBuffer = new ByteBuffer();

        final File inputFile = new File(blockPath);
        try (
            final InputStream inputStream = new FileInputStream(inputFile);
            final GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream, GZIP_BUFFER_SIZE)
        ) {
            final byte[] buffer = new byte[READ_BUFFER_SIZE];

            while (true) {
                final int byteCountRead = gzipInputStream.read(buffer);
                if (byteCountRead < 0) { break; }

                byteBuffer.appendBytes(buffer, byteCountRead);
            }
        }
        catch (final Exception exception) {
            if (exception instanceof ZipException) {
                throw (ZipException) exception; // File is not a GZIP'ed file...
            }

            Logger.warn(exception);
            return null;
        }

        return byteBuffer;
    }

    protected ByteArray _readCompressedChunkInternal(final String blockPath, final Long diskOffset, final Integer byteCount) throws ZipException {
        final MutableByteArray byteArray = new MutableByteArray(byteCount);
        final File inputFile = new File(blockPath);
        try (
            final InputStream inputStream = new FileInputStream(inputFile);
            final GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream, GZIP_BUFFER_SIZE)
        ) {
            gzipInputStream.skip(diskOffset);

            int totalBytesRead = 0;
            while (totalBytesRead < byteCount) {
                final int byteArrayOffset = totalBytesRead;
                final int maxBytesRead = (byteCount - totalBytesRead);
                final int byteCountRead = gzipInputStream.read(byteArray.unwrap(), byteArrayOffset, maxBytesRead);
                if (byteCountRead < 0) { break; }

                totalBytesRead += byteCountRead;
            }

            if (totalBytesRead < byteCount) { return null; }
        }
        catch (final Exception exception) {
            if (exception instanceof ZipException) {
                throw (ZipException) exception; // File is not a GZIP'ed file...
            }

            Logger.warn(exception);
            return null;
        }

        return byteArray;
    }

    protected void _compressInternal(final String blockPath) throws Exception {
        final File outputFile = new File(blockPath + ".gz");
        final File inputFile = new File(blockPath);
        final File swapFile = new File(blockPath + ".swp");
        try (
            final InputStream inputStream = new FileInputStream(inputFile);
            final OutputStream outputStream = new FileOutputStream(outputFile);
            final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream, GZIP_BUFFER_SIZE)
        ) {
            final byte[] buffer = new byte[READ_BUFFER_SIZE];

            while (true) {
                final int byteCountRead = inputStream.read(buffer);
                if (byteCountRead < 0) { break; }

                gzipOutputStream.write(buffer, 0, byteCountRead);
            }
        }

                                                            //  "input" "output"  "swap"
                                                            //   BLOCK       GZ    NULL
        inputFile.renameTo(swapFile.getAbsoluteFile());     //    NULL       GZ   BLOCK
        outputFile.renameTo(inputFile.getAbsoluteFile());   //      GZ     NULL   BLOCK
        swapFile.delete();                                  //      GZ     NULL    NULL
    }

    protected ByteArray _readBlock(final Sha256Hash blockHash, final Long blockHeight) {
        if (_blockDataDirectory == null) { return null; }

        final String blockPath = _getBlockDataPath(blockHash, blockHeight);
        if (blockPath == null) { return null; }

        if (! IoUtil.fileExists(blockPath)) { return null; }

        try {
            if (_blockCompressionIsEnabled) {
                try {
                    return _readCompressedInternal(blockPath);
                }
                catch (final ZipException zipException) {
                    Logger.debug(blockHash + " was not a compressed block file; compressing.");
                    _compressInternal(blockPath);

                    return _readCompressedInternal(blockPath);
                }
            }

            final File blockFile = new File(blockPath);
            final byte[] bytes = IoUtil.getFileContents(blockFile);
            return MutableByteArray.wrap(bytes);
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return null;
        }
    }

    protected ByteArray _readFromBlock(final Sha256Hash blockHash, final Long blockHeight, final Long diskOffset, final Integer byteCount) {
        if (_blockDataDirectory == null) { return null; }

        final String blockPath = _getBlockDataPath(blockHash, blockHeight);
        if (blockPath == null) { return null; }

        if (! IoUtil.fileExists(blockPath)) { return null; }

        try {
            if (_blockCompressionIsEnabled) {
                try {
                    return _readCompressedChunkInternal(blockPath, diskOffset, byteCount);
                }
                catch (final ZipException zipException) {
                    Logger.debug(blockHash + " was not a compressed block file; compressing.");
                    _compressInternal(blockPath);

                    return _readCompressedChunkInternal(blockPath, diskOffset, byteCount);
                }
            }

            final MutableByteArray byteArray = new MutableByteArray(byteCount);

            try (final RandomAccessFile file = new RandomAccessFile(new File(blockPath), "r")) {
                file.seek(diskOffset);
                file.readFully(byteArray.unwrap()); // Can throw EOF, but is caught later and returns null.
            }

            return byteArray;
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return null;
        }
    }

    public BlockStoreCore(final String dataDirectory, final BlockHeaderInflaters blockHeaderInflaters, final BlockInflaters blockInflaters, final Boolean useCompression) {
        _dataDirectory = dataDirectory;
        _blockDataDirectory = (dataDirectory != null ? (dataDirectory + "/" + BitcoinProperties.DATA_DIRECTORY_NAME + "/blocks") : null);
        _blockInflaters = blockInflaters;
        _blockHeaderInflaters = blockHeaderInflaters;
        _blockCompressionIsEnabled = useCompression;
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

        if (_blockCompressionIsEnabled) {
            final File file = new File(blockPath);
            final boolean result = IoUtil.putCompressedFileContents(file, byteArray);
            if (result) { return true; }
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

        final ByteArray blockBytes = _readBlock(blockHash, blockHeight);
        if (blockBytes == null) { return null; }

        final BlockInflater blockInflater = _blockInflaters.getBlockInflater();
        final MutableBlock block = blockInflater.fromBytes(blockBytes);
        if (block == null) { return null; }

        final Sha256Hash actualBlockHash = block.getHash();
        if (! Util.areEqual(blockHash, actualBlockHash)) {
            Logger.warn("Block hash mismatch; likely data corruption detected.", new Exception());
            return null;
        }

        return block;
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

    @Override
    public String getBlockDataDirectory() {
        return _blockDataDirectory;
    }
}
