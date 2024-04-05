package com.softwareverde.bitcoin.util;

import com.google.leveldb.LevelDb;
import com.google.leveldb.StringEntryInflater;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

public class LevelDbTests {

    @Test
    public void should_put_and_get() throws Exception {
        Logger.setLogLevel(LogLevel.ON);

        final File file = Files.createTempDirectory("leveldb").toFile();
        file.deleteOnExit();

        System.out.println(file.getAbsolutePath());

        try (final LevelDb<String, String> levelDb = new LevelDb<>(file, new StringEntryInflater())) {
            levelDb.open();
            levelDb.put("key", "firstValue");
            Assert.assertEquals("firstValue", levelDb.get("key"));

            levelDb.put("key2", "value2");
            Assert.assertEquals("value2", levelDb.get("key2"));

            levelDb.remove("key");
            final String value = levelDb.get("key");
            Assert.assertNull(value);

            levelDb.put("key2", null);
            final String value2 = levelDb.get("key2");
            Assert.assertNull(value2);

            levelDb.put("key3", "");
            final String value3 = levelDb.get("key3");
            Assert.assertNull(value3);
        }
    }
}
