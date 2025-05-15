package com.softwareverde.btreedb.file;

import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.Util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FullyMemoryMappedData implements MemoryMappedData {
    public final File file;
    public final int bytesPerSector;
    protected final ArrayList<MutableByteArray> _sectors;
    protected volatile int _lastSectorUnusedByteCount = 0;

    protected final AtomicInteger _openCount = new AtomicInteger(0);

    protected final ReentrantReadWriteLock.ReadLock _sectorsReadLock;
    protected final ReentrantReadWriteLock.WriteLock _sectorsWriteLock;

    protected long _getEof() {
        final int sectorCount = _sectors.size();
        if (sectorCount == 0) { return 0L; }

        final int totalByteCount = (sectorCount * this.bytesPerSector);
        return (totalByteCount - _lastSectorUnusedByteCount);
    }

    protected long _getSectorStartPosition(final Integer sectorIndex) {
        return (sectorIndex.longValue() * this.bytesPerSector);
    }

    protected Integer _getSector(final long position, final boolean allowCreationOfSector) {
        final int sectorCount = _sectors.size();

        final int sectorIndex = (int) (position / this.bytesPerSector);
        if (sectorIndex < sectorCount) {
            return sectorIndex;
        }

        if (! allowCreationOfSector) { return null; }

        // final long eof = _getEof();
        // if (position != eof) {
        //     return null;
        // }

        // NOTICE: Requires WriteLock.
        int newSectorIndex = _sectors.size();
        while (sectorIndex >= newSectorIndex) {
            final MutableByteArray newSector = new MutableByteArray(this.bytesPerSector);
            _sectors.add(newSector);
            _lastSectorUnusedByteCount = this.bytesPerSector;
            newSectorIndex += 1;
        }
        return sectorIndex;
    }

    public FullyMemoryMappedData(final File file) {
        this(file, 1024);
    }

    public FullyMemoryMappedData(final File file, final int bytesPerSector) {
        if (bytesPerSector < 1) { throw new RuntimeException("Invalid Sector size."); }
        this.file = file;
        this.bytesPerSector = bytesPerSector;
        _sectors = new ArrayList<>();

        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _sectorsReadLock = readWriteLock.readLock();
        _sectorsWriteLock = readWriteLock.writeLock();
    }

    @Override
    public void open() throws Exception {
        final int openCount = _openCount.incrementAndGet();
        if (openCount > 1) { return; } // Already loaded.

        if (! this.file.exists()) { return; }

        _sectorsWriteLock.lock();
        _lastSectorUnusedByteCount = 0;
        try (final FileInputStream fileInputStream = new FileInputStream(this.file)) {
            final long fileLength = this.file.length();
            final long estimatedPageCount = (fileLength + this.bytesPerSector - 1L) / this.bytesPerSector;
            final int buffer = 1024;
            _sectors.ensureCapacity((int) (estimatedPageCount + buffer));

            // int i = 0;
            while (true) {
                final byte[] bytes = fileInputStream.readNBytes(this.bytesPerSector);
                if (bytes.length == 0) { break; }
                if (bytes.length != this.bytesPerSector) {
                    final MutableByteArray newSector = new MutableByteArray(this.bytesPerSector);
                    newSector.setBytes(0, bytes);
                    _sectors.add(newSector);
                    _lastSectorUnusedByteCount = (this.bytesPerSector - bytes.length);
                    break;
                }

                final MutableByteArray sector = MutableByteArray.wrap(bytes);
                _sectors.add(sector);

                // i += 1;
            }
        }
        finally {
            _sectorsWriteLock.unlock();
        }
    }

    @Override
    public void close() throws Exception {
        final int openCount = _openCount.decrementAndGet();
        if (openCount > 0) { return; }

        _sectorsWriteLock.lock();
        try (
            final FileOutputStream fileOutputStream = new FileOutputStream(this.file, false);
            final BufferedOutputStream outputStream = new BufferedOutputStream(fileOutputStream)
        ) {
            final int sectorCount = _sectors.size();
            for (int i = 0; i < sectorCount; ++i) {
                final MutableByteArray sector = _sectors.get(i);
                outputStream.write(sector.unwrap());
            }

            _sectors.clear();
            _lastSectorUnusedByteCount = 0;
        }
        finally {
            _sectorsWriteLock.unlock();
        }
    }

    @Override
    public void doRead(long position, final byte[] buffer, int bufferOffset, int byteCountRemaining) throws Exception {
        final int originalByteCount = byteCountRemaining;
        while (byteCountRemaining > 0) {

            final long sectorStartPosition;
            final boolean isLastSector;
            final MutableByteArray sector;
            final int lastSectorUnusedByteCount;

            _sectorsReadLock.lock();
            try {
                final Integer sectorIndex = _getSector(position, false);
                if (sectorIndex == null) {
                    throw new Exception("EOF");
                }

                sectorStartPosition = _getSectorStartPosition(sectorIndex);
                isLastSector = Util.areEqual(sectorIndex, (_sectors.size() - 1));
                sector = _sectors.get(sectorIndex);

                lastSectorUnusedByteCount = _lastSectorUnusedByteCount;
            }
            finally {
                _sectorsReadLock.unlock();
            }

            final int sectorOffset = (int) (position - sectorStartPosition);

            final int bytesRemainingInSector;
            {
                final int bytesInSector = (isLastSector ? (this.bytesPerSector - lastSectorUnusedByteCount) : this.bytesPerSector);
                bytesRemainingInSector = (bytesInSector - sectorOffset);
            }

            final int byteCountToRead = Math.min(bytesRemainingInSector, byteCountRemaining);
            byteCountRemaining -= byteCountToRead;

            final byte[] unwrappedSector = sector.unwrap();
            // for (int i = 0; i < byteCountToRead; ++i) {
            //     buffer[bufferOffset + i] = unwrappedSector[sectorOffset + i];
            // }
            System.arraycopy(unwrappedSector, sectorOffset, buffer, bufferOffset, byteCountToRead);

            bufferOffset += byteCountToRead;
            position += byteCountToRead;

            if ( (byteCountRemaining > 0L) && isLastSector ) {
                throw new Exception("EOF: failed to read " + originalByteCount + " bytes (" + byteCountRemaining + " remained at EOF)");
            }
        }
    }

    @Override
    public void doWrite(long position, final byte[] buffer, int bufferOffset, int byteCountRemaining) throws Exception {
        while (byteCountRemaining > 0) {

            final long sectorStartPosition;
            final boolean isLastSector;
            final MutableByteArray sector;
            final int sectorOffset;
            final int byteCountToWrite;

            _sectorsWriteLock.lock();
            try {
                final Integer sectorIndex = _getSector(position, true);
                if (sectorIndex == null) {
                    throw new Exception("EOF");
                }

                isLastSector = Util.areEqual(sectorIndex, (_sectors.size() - 1));
                sectorStartPosition = _getSectorStartPosition(sectorIndex);
                sector = _sectors.get(sectorIndex);
                sectorOffset = (int) (position - sectorStartPosition);

                final int bytesRemainingInSector = (this.bytesPerSector - sectorOffset);
                byteCountToWrite = Math.min(bytesRemainingInSector, byteCountRemaining);

                if (isLastSector) {
                    final int lastWrittenIndex = (sectorOffset + byteCountToWrite);
                    final int unwrittenTailByteCount = (this.bytesPerSector - lastWrittenIndex);
                    if (unwrittenTailByteCount < _lastSectorUnusedByteCount) {
                        _lastSectorUnusedByteCount = unwrittenTailByteCount;
                    }
                }
            }
            finally {
                _sectorsWriteLock.unlock();
            }

            byteCountRemaining -= byteCountToWrite;

            final byte[] unwrappedSector = sector.unwrap();
            // for (int i = 0; i < byteCountToWrite; ++i) {
            //     unwrappedSector[sectorOffset + i] = buffer[bufferOffset + i];
            // }
            System.arraycopy(buffer, bufferOffset, unwrappedSector, sectorOffset, byteCountToWrite);

            bufferOffset += byteCountToWrite;
            position += byteCountToWrite;
        }
    }

    @Override
    public void truncate() throws Exception {
        if (_openCount.get() > 0) {
            _sectorsWriteLock.lock();
            try {
                _sectors.clear();
                _lastSectorUnusedByteCount = 0;
            }
            finally {
                _sectorsWriteLock.unlock();
            }
        }
        else {
            Files.write(this.file.toPath(), new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    @Override
    public long getByteCount() throws Exception {
        if (_openCount.get() > 0) {
            _sectorsReadLock.lock();
            try {
                final long sectorCount = _sectors.size();
                return ((sectorCount * this.bytesPerSector) - _lastSectorUnusedByteCount);
            }
            finally {
                _sectorsReadLock.unlock();
            }
        }
        else {
            return this.file.length();
        }
    }

    @Override
    public File getFile() {
        return this.file;
    }
}