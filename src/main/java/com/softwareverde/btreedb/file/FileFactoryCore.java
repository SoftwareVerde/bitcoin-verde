package com.softwareverde.btreedb.file;

import java.io.File;

public class FileFactoryCore implements FileSystemBasedFileFactory {
    protected final File _file;

    public FileFactoryCore(final File file) {
        _file = file;
    }

    @Override
    public InputFile getInputFile() {
        return this.getInputOutputFile();
    }

    @Override
    public OutputFile getOutputFile() {
        return this.getInputOutputFile();
    }

    @Override
    public InputOutputFile getInputOutputFile() {
        return new InputOutputFileCore(_file, InputOutputFileCore.Mode.READ_WRITE);
    }

    @Override
    public File getFile() {
        return _file;
    }

    @Override
    public String toString() {
        return "FileFactory:" + _file.toString();
    }
}
