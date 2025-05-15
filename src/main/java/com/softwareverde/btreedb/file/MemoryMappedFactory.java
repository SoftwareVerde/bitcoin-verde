package com.softwareverde.btreedb.file;

import java.io.File;

public class MemoryMappedFactory implements FileSystemBasedFileFactory {
    protected final MemoryMappedData _memoryMappedData;

    public MemoryMappedFactory(final File file, final int bytesPerSector, final long maxMemoryByteCount) {
        this(new PartiallyMemoryMappedData(file, bytesPerSector, Math.max(1, (int) (maxMemoryByteCount / bytesPerSector))));
    }

    public MemoryMappedFactory(final File file, final int bytesPerSector) {
        this(new FullyMemoryMappedData(file, bytesPerSector));
    }

    public MemoryMappedFactory(final MemoryMappedData memoryMappedData) {
        _memoryMappedData = memoryMappedData;
    }

    @Override
    public InputFile getInputFile() {
        return new MemoryMappedInputOutputFile(_memoryMappedData);
    }

    @Override
    public OutputFile getOutputFile() {
        return new MemoryMappedInputOutputFile(_memoryMappedData);
    }

    @Override
    public InputOutputFile getInputOutputFile() {
        return new MemoryMappedInputOutputFile(_memoryMappedData);
    }

    @Override
    public File getFile() {
        return _memoryMappedData.getFile();
    }

    @Override
    public String toString() {
        return "MemoryMap:" + _memoryMappedData.getFile().toString();
    }
}
