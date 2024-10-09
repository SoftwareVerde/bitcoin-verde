package com.softwareverde.btreedb.file;

import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.map.mutable.MutableTreeMap;
import com.softwareverde.util.Util;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PartiallyMemoryMappedData implements MemoryMappedData {
    public final File file;
    public final int bytesPerSector;
    protected final MutableTreeMap<Integer, MutableByteArray> _sectors;
    protected final AtomicInteger _sectorCount = new AtomicInteger(0);
    protected final Integer _maxMemorySectorCount;
    protected volatile int _lastSectorUnusedByteCount = 0;

    protected final AtomicInteger _openCount = new AtomicInteger(0);
    protected final InputOutputFile _inputOutputFile;

    protected final ReentrantReadWriteLock.ReadLock _sectorsReadLock;
    protected final ReentrantReadWriteLock.WriteLock _sectorsWriteLock;

    protected long _getSectorStartPosition(final Integer sectorIndex) {
        return (sectorIndex.longValue() * this.bytesPerSector);
    }

    protected Integer _getSector(final long position, final boolean allowCreationOfSector) throws Exception {
        final int sectorIndex = (int) (position / this.bytesPerSector);
        if (sectorIndex < _sectorCount.get()) {
            return sectorIndex;
        }

        if (! allowCreationOfSector) { return null; }

        // final long eof = _getEof();
        // if (position != eof) {
        //     return null;
        // }

        // NOTICE: Requires WriteLock.
        int newSectorIndex = _sectorCount.get();
        while (sectorIndex >= newSectorIndex) {
            final int memorySectorCount = _sectors.getCount();
            if (memorySectorCount >= _maxMemorySectorCount) {
                final int removedSectorIndex = _sectorCount.get() - _maxMemorySectorCount;
                final MutableByteArray sector = _sectors.remove(removedSectorIndex);

                final long sectorPosition = _getSectorStartPosition(removedSectorIndex);
                synchronized (_inputOutputFile) { // Flush the sector to disk before dropping it.
                    _inputOutputFile.setPosition(sectorPosition);
                    _inputOutputFile.write(sector.unwrap());
                }
            }

            final MutableByteArray newSector = new MutableByteArray(this.bytesPerSector);
            _sectors.put(newSectorIndex, newSector);
            _sectorCount.incrementAndGet();
            _lastSectorUnusedByteCount = this.bytesPerSector;
            newSectorIndex += 1;
        }
        return sectorIndex;
    }

    public PartiallyMemoryMappedData(final File file) {
        this(file, 1024, 131072);
    }

    public PartiallyMemoryMappedData(final File file, final int bytesPerSector, final int maxMemorySectorCount) {
        if (bytesPerSector < 1) { throw new RuntimeException("Invalid Sector size."); }
        this.file = file;
        this.bytesPerSector = bytesPerSector;
        _maxMemorySectorCount = maxMemorySectorCount;
        _sectors = new MutableTreeMap<>();

        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _sectorsReadLock = readWriteLock.readLock();
        _sectorsWriteLock = readWriteLock.writeLock();

        _inputOutputFile = new InputOutputFileCore(this.file);
    }

    protected static void skipNBytes(final InputStream inputStream, long byteCount) throws Exception {
        while (byteCount > 0) {
            long ns = inputStream.skip(byteCount);
            if (ns > 0 && ns <= byteCount) {
                byteCount -= ns;
            }
            else if (ns == 0) {
                if (inputStream.read() < 0) {
                    throw new EOFException();
                }
                byteCount -= 1;
            }
            else { break; }
        }
    }

    @Override
    public void open() throws Exception {
        final int openCount = _openCount.incrementAndGet();
        if (openCount > 1) { return; } // Already loaded.

        _sectorsWriteLock.lock();
        _lastSectorUnusedByteCount = 0;
        _inputOutputFile.open();

        if (! this.file.exists()) { return; }

        try (final FileInputStream fileInputStream = new FileInputStream(this.file)) {
            final long fileLength = this.file.length();
            final int sectorCount = (int) ((fileLength + this.bytesPerSector - 1L) / this.bytesPerSector);
            _sectorCount.set(sectorCount);
            int i = Math.max(0, (sectorCount - 1) - _maxMemorySectorCount);
            PartiallyMemoryMappedData.skipNBytes(fileInputStream, (i * (long) this.bytesPerSector));

            while (true) {
                final byte[] bytes = fileInputStream.readNBytes(this.bytesPerSector);
                if (bytes.length == 0) { break; }
                if (bytes.length != this.bytesPerSector) {
                    final MutableByteArray newSector = new MutableByteArray(this.bytesPerSector);
                    newSector.setBytes(0, bytes);
                    _sectors.put(i, newSector);
                    _lastSectorUnusedByteCount = (this.bytesPerSector - bytes.length);
                    break;
                }

                final MutableByteArray sector = MutableByteArray.wrap(bytes);
                _sectors.put(i, sector);

                i += 1;
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
            final int sectorCount = _sectors.getCount();
            final int maxMemorySectorCount = Math.min(_maxMemorySectorCount, sectorCount);
            for (int i = 0; i < sectorCount; ++i) {
                final MutableByteArray sector = _sectors.get(_sectorCount.get() - maxMemorySectorCount + i);
                outputStream.write(sector.unwrap());
            }

            _sectors.clear();
            _sectorCount.set(0);
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
                isLastSector = Util.areEqual(sectorIndex, (_sectorCount.get() - 1));

                final boolean isMemorySector = (sectorIndex >= (_sectorCount.get() - _maxMemorySectorCount));
                if (isMemorySector) {
                    sector = _sectors.get(sectorIndex);
                }
                else {
                    synchronized (_inputOutputFile) {
                        _inputOutputFile.setPosition(sectorStartPosition);
                        final byte[] bytes = _inputOutputFile.read(this.bytesPerSector); // As long as _sectors is not empty, _inputOutputFile must have at least bytesPerSector available.
                        sector = MutableByteArray.wrap(bytes);
                    }
                }

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
            final boolean isMemorySector;

            _sectorsWriteLock.lock();
            try {
                final Integer sectorIndex = _getSector(position, true);
                if (sectorIndex == null) {
                    throw new Exception("EOF");
                }

                isLastSector = Util.areEqual(sectorIndex, (_sectorCount.get() - 1));
                sectorStartPosition = _getSectorStartPosition(sectorIndex);
                sectorOffset = (int) (position - sectorStartPosition);

                isMemorySector = (sectorIndex >= (_sectorCount.get() - _maxMemorySectorCount));
                if (isMemorySector) {
                    sector = _sectors.get(sectorIndex);
                }
                else {
                    synchronized (_inputOutputFile) {
                        _inputOutputFile.setPosition(sectorStartPosition);
                        final byte[] bytes = _inputOutputFile.read(this.bytesPerSector); // As long as _sectors is not empty, _inputOutputFile must have at least bytesPerSector available.
                        sector = MutableByteArray.wrap(bytes);
                    }
                }

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

            if (! isMemorySector) { // Flush write to disk since it is not stored in memory.
                synchronized (_inputOutputFile) {
                    _inputOutputFile.setPosition(sectorStartPosition);
                    _inputOutputFile.write(sector.unwrap());
                }
            }

            bufferOffset += byteCountToWrite;
            position += byteCountToWrite;
        }
    }

    @Override
    public void truncate() throws Exception {
        if (_openCount.get() > 0) {
            _sectorsWriteLock.lock();
            try {
                _inputOutputFile.truncate();
                _sectors.clear();
                _sectorCount.set(0);
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
                final long sectorCount = _sectorCount.get();
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