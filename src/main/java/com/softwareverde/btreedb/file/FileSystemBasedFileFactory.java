package com.softwareverde.btreedb.file;

import java.io.File;

public interface FileSystemBasedFileFactory extends FileFactory {
    File getFile();
}
