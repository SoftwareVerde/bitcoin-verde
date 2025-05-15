package com.softwareverde.btreedb.file;

public interface FileFactory {
    InputFile getInputFile();
    OutputFile getOutputFile();
    InputOutputFile getInputOutputFile();
}
