package com.softwareverde.bitcoin.server.module.node.sync.bootstrap;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.LocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.UtxoCount;
import com.softwareverde.bitcoin.server.module.node.database.BlockHeaderDatabaseManager;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.util.type.time.SystemTime;

import java.io.InputStream;

public class HeadersBootstrapper {
    protected static final Long BOOTSTRAP_BLOCK_COUNT = 575000L;

    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final SystemTime _systemTime = new SystemTime();
    protected Long _currentBlockHeight = 0L;
    protected Boolean _abortInit = false;

    public HeadersBootstrapper(final DatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    public void run() {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final LocalDatabaseManagerCache localDatabaseManagerCache = new LocalDatabaseManagerCache(UtxoCount.wrap(0L));
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, localDatabaseManagerCache);
            final BlockId headBlockHeaderId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            final long maxDatFileHeight = (BOOTSTRAP_BLOCK_COUNT - 1L);
            final long startingHeight = (headBlockHeaderId == null ? 0L : blockHeaderDatabaseManager.getBlockHeight(headBlockHeaderId));
            if (startingHeight < maxDatFileHeight) {
                long currentBlockHeight = startingHeight;

                final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

                try (final InputStream inputStream = HeadersBootstrapper.class.getResourceAsStream("/bootstrap/headers.dat")) {
                    synchronized (BlockHeaderDatabaseManager.MUTEX) {
                        inputStream.skip(startingHeight * BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT);

                        final MutableByteArray buffer = new MutableByteArray(BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT);

                        final int batchSize = 4096;
                        final MutableList<BlockHeader> batchedHeaders = new MutableList<BlockHeader>(batchSize);

                        final Thread currentThread = Thread.currentThread();
                        while ( (! _abortInit) && (! currentThread.isInterrupted()) ) {
                            int readByteCount = inputStream.read(buffer.unwrap());
                            while ( (readByteCount >= 0) && (readByteCount < BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT) ) {
                                final int nextByte = inputStream.read();
                                if (nextByte < 0) { break; }

                                buffer.set(readByteCount, (byte) nextByte);
                                readByteCount += 1;
                            }
                            if (readByteCount != BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT) { break; }

                            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(buffer);
                            if (blockHeader == null) { break; }

                            batchedHeaders.add(blockHeader);
                            if (batchedHeaders.getSize() == batchSize) {
                                TransactionUtil.startTransaction(databaseConnection);
                                final List<BlockId> blockIds = blockHeaderDatabaseManager.insertBlockHeaders(batchedHeaders);
                                TransactionUtil.commitTransaction(databaseConnection);

                                batchedHeaders.clear();

                                if (blockIds == null) { break; }
                                currentBlockHeight += blockIds.getSize();
                            }

                            _currentBlockHeight = currentBlockHeight;
                        }

                        if (! batchedHeaders.isEmpty()) {
                            TransactionUtil.startTransaction(databaseConnection);
                            final List<BlockId> blockIds = blockHeaderDatabaseManager.insertBlockHeaders(batchedHeaders);
                            TransactionUtil.commitTransaction(databaseConnection);

                            batchedHeaders.clear();

                            if (blockIds != null) {
                                _currentBlockHeight += blockIds.getSize();
                            }
                        }

                        if ( (Thread.interrupted()) || (_abortInit) ) { return; } // Intentionally always clear the interrupted flag...
                    }
                }
            }
        }
        catch (final Exception exception) {
            exception.printStackTrace();
            Logger.log(exception);
        }
    }


    public Long getCurrentBlockHeight() {
        return _currentBlockHeight;
    }

    public void stop() {
        _abortInit = true;
    }
}
