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
import com.softwareverde.btreedb.BucketDb;
import com.softwareverde.btreedb.file.InputFile;
import com.softwareverde.btreedb.file.InputFileStream;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;

public class BlockStoreCore implements BlockStore {
    public static final String SUB_DIRECTORY = "blocks";
    protected static final int GZIP_BUFFER_SIZE = (int) (ByteUtil.Unit.Binary.MEBIBYTES / 2);
    protected static final int READ_BUFFER_SIZE = (int) (16L * ByteUtil.Unit.Binary.KIBIBYTES);

    protected static Boolean isEmpty(final File file) {
        if (! file.exists()) { return true; }
        if (! file.isFile()) { return true; }
        return (file.length() < 1L);
    }

    protected final BlockHeaderInflater _blockHeaderInflater;
    protected final BlockInflater _blockInflater;
    protected final BlockDeflater _blockDeflater;
    protected final File _dataDirectory;
    protected final File _blockDataDirectory;
    protected final BucketDb<Sha256Hash, ByteArray> _bucketDb;

    protected ByteArray _readCompressedInternal(final InputFile inputFile) throws ZipException {
        int byteCount = 0;
        MutableList<byte[]> bytes = new MutableArrayList<>(0);

        try (
            final InputStream inputStream = new InputFileStream(inputFile);
            final GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream, GZIP_BUFFER_SIZE)
        ) {
            final byte[] buffer = new byte[READ_BUFFER_SIZE];

            while (true) {
                final int byteCountRead = gzipInputStream.read(buffer);
                if (byteCountRead < 0) { break; }

                bytes.add(ByteUtil.copyBytes(buffer, 0, byteCountRead));
                byteCount += byteCountRead;
            }
        }
        catch (final Exception exception) {
            if (exception instanceof ZipException) {
                throw (ZipException) exception; // File is not a GZIP'ed file...
            }

            Logger.warn(exception);
            return null;
        }

        int index = 0;
        final MutableByteArray byteArray = new MutableByteArray(byteCount);
        for (final byte[] b : bytes) {
            byteArray.setBytes(index, b);
            index += b.length;
        }
        return byteArray;
    }

    protected ByteArray _readCompressedChunkInternal(final InputFile inputFile, final Long diskOffset, final Integer byteCount) throws ZipException {
        final MutableByteArray byteArray = new MutableByteArray(byteCount);
        try (
            final InputStream inputStream = new InputFileStream(inputFile);
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

    protected void _compressInternal(final File inputFile) throws Exception {
        final File outputFile = new File(inputFile + ".gz");
        final File swapFile = new File(inputFile + ".swp");
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

    protected ByteArray _readBlock(final Sha256Hash blockHash) {
        if (_blockDataDirectory == null) { return null; }

        try {
            final Container<ByteArray> bytesContainer = new Container<>();
            _bucketDb.stream(blockHash, new BucketDb.StreamVisitor() {
                @Override
                public void run(final InputFile stream) throws Exception {
                    bytesContainer.value = _readCompressedInternal(stream);
                }
            });

            return bytesContainer.value;
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return null;
        }
    }

    protected ByteArray _readFromBlock(final Sha256Hash blockHash, final Long diskOffset, final Integer byteCount) {
        if (_blockDataDirectory == null) { return null; }

        try {
            final Container<ByteArray> bytesContainer = new Container<>();
            _bucketDb.stream(blockHash, new BucketDb.StreamVisitor() {
                @Override
                public void run(final InputFile stream) throws Exception {
                    bytesContainer.value = _readCompressedChunkInternal(stream, diskOffset, byteCount);
                }
            });

            return bytesContainer.value;
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return null;
        }
    }

    public BlockStoreCore(final File dataDirectory) {
        this(dataDirectory, new BlockHeaderInflater(), new BlockInflater(), new BlockDeflater());
    }

    public BlockStoreCore(final File dataDirectory, final BlockHeaderInflaters blockHeaderInflaters, final BlockInflaters blockInflaters) {
        this(dataDirectory, blockHeaderInflaters.getBlockHeaderInflater(), blockInflaters.getBlockInflater(), blockInflaters.getBlockDeflater());
    }

    public BlockStoreCore(final File dataDirectory, final BlockHeaderInflater blockHeaderInflater, final BlockInflater blockInflater, final BlockDeflater blockDeflater) {
        final File mainDataDirectory = new File(dataDirectory, BitcoinProperties.DATA_DIRECTORY_NAME);
        final File blocksDataDirectory = new File(mainDataDirectory, BlockStoreCore.SUB_DIRECTORY);

        _dataDirectory = dataDirectory;
        _blockDataDirectory = (dataDirectory != null ? blocksDataDirectory : null);
        _blockHeaderInflater = blockHeaderInflater;
        _blockInflater = blockInflater;
        _blockDeflater = blockDeflater;

        _bucketDb = new BucketDb<>(_blockDataDirectory, new BlockBucketDbEntryInflater(), 14, 1024 * 12, 1, 0L, 0L);
    }

    public void open() throws Exception {
        if (! _blockDataDirectory.exists()) {
            _blockDataDirectory.mkdirs();
        }

        _bucketDb.open();
    }

    public void close() throws Exception {
        _bucketDb.close();
    }

    @Override
    public Boolean storeBlock(final Block block, final Long blockHeight) {
        if (_blockDataDirectory == null) { return false; }
        if (block == null) { return false; }

        final Sha256Hash blockHash = block.getHash();

        final ByteArray blockBytes = _blockDeflater.toBytes(block);
        final ByteArray compressedBytes = IoUtil.compress(blockBytes);

        try {
            _bucketDb.put(blockHash, compressedBytes);
            _bucketDb.commit();
            return true;
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return false;
        }
    }

    @Override
    public void removeBlock(final Sha256Hash blockHash, final Long blockHeight) {
        if (_blockDataDirectory == null) { return; }
        _bucketDb.delete(blockHash);
        try {
            _bucketDb.commit();
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }
    }

    @Override
    public MutableBlockHeader getBlockHeader(final Sha256Hash blockHash, final Long blockHeight) {
        if (_blockDataDirectory == null) { return null; }

        final ByteArray blockBytes = _readFromBlock(blockHash, 0L, BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT);
        if (blockBytes == null) { return null; }

        return _blockHeaderInflater.fromBytes(blockBytes);
    }

    @Override
    public MutableBlock getBlock(final Sha256Hash blockHash, final Long blockHeight) {
        if (_blockDataDirectory == null) { return null; }

        final ByteArray blockBytes = _readBlock(blockHash);
        if (blockBytes == null) { return null; }

        final MutableBlock block = _blockInflater.fromBytes(blockBytes);
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
        try {
            return _bucketDb.containsKey(blockHash);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return false;
        }
    }

    @Override
    public ByteArray readFromBlock(final Sha256Hash blockHash, final Long blockHeight, final Long diskOffset, final Integer byteCount) {
        return _readFromBlock(blockHash, diskOffset, byteCount);
    }

    @Override
    public Long getBlockByteCount(final Sha256Hash blockHash, final Long blockHeight) {
        final ByteArray blockBytes = _readBlock(blockHash);
        if (blockBytes == null) { return null; }
        return (long) blockBytes.getByteCount();
    }

    @Override
    public File getDataDirectory() {
        return _dataDirectory;
    }

    @Override
    public File getBlockDataDirectory() {
        return _blockDataDirectory;
    }
}

class BlockBucketDbEntryInflater implements BucketDb.BucketEntryInflater<Sha256Hash, ByteArray> {

    @Override
    public Sha256Hash getHash(final Sha256Hash bytes) {
        return bytes;
    }

    @Override
    public int getValueByteCount(final ByteArray blockBytes) {
        return blockBytes.getByteCount();
    }

    @Override
    public Sha256Hash keyFromBytes(final ByteArray bytes) {
        return Sha256Hash.wrap(bytes.getBytes());
    }

    @Override
    public ByteArray keyToBytes(final Sha256Hash bytes) {
        return bytes;
    }

    @Override
    public int getKeyByteCount() {
        return Sha256Hash.BYTE_COUNT;
    }

    @Override
    public ByteArray valueFromBytes(final ByteArray compressedBytes) {
        return compressedBytes;
    }

    @Override
    public ByteArray valueToBytes(final ByteArray blockBytes) {
        return blockBytes;
    }
}