package com.softwareverde.bitcoin.server.module.stratum.key;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.aes.AesKey;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

import java.io.File;

public class ServerEncryptionKey {
    protected static final ByteArray KEY_MASK = ByteArray.fromHexString("F920372405A2CB1B41D07BFD60D40FA94209C38369788571DE9BEB613C2E9BFE");
    protected static final Integer KEY_BYTE_COUNT = KEY_MASK.getByteCount();
    public static final String ENCRYPTION_KEY_FILE_NAME = "key.dat";

    public static ServerEncryptionKey load(final File dataDirectory) {
        final File encryptionKeyFile = new File(dataDirectory, ServerEncryptionKey.ENCRYPTION_KEY_FILE_NAME);
        if (! encryptionKeyFile.exists()) {
            final PrivateKey privateKey = PrivateKey.createNewKey();
            final String privateKeyHexString = privateKey.toString();
            IoUtil.putFileContents(encryptionKeyFile, StringUtil.stringToBytes(privateKeyHexString));
        }

        return new ServerEncryptionKey(dataDirectory);
    }

    protected final AesKey _key;

    protected static AesKey loadEncryptionKey(final File encryptionKeyFile) {
        try {
            if (! encryptionKeyFile.canRead()) {
                Logger.info("Unable to read encryption key: " + encryptionKeyFile);
                return null;
            }

            final String serverEncryptionKeyHexString = StringUtil.bytesToString(IoUtil.getFileContents(encryptionKeyFile));
            final ByteArray serverEncryptionKey = ByteArray.fromHexString(serverEncryptionKeyHexString);
            final int keyByteCount = serverEncryptionKey.getByteCount();
            if (! Util.areEqual(keyByteCount, ServerEncryptionKey.KEY_BYTE_COUNT)) {
                Logger.info("Invalid server key length found.");
                return null;
            }

            final MutableByteArray xorBytes = new MutableByteArray(keyByteCount);
            for (int i = 0; i < keyByteCount; i++) {
                final byte keyMaskByte = ServerEncryptionKey.KEY_MASK.getByte(i);
                final byte keyByte = serverEncryptionKey.getByte(i);
                xorBytes.setByte(i, (byte) (keyMaskByte ^ keyByte));
            }

            return new AesKey(xorBytes.unwrap());
        }
        catch (final Exception exception) {
            Logger.debug(exception);
            return null;
        }
    }

    protected ServerEncryptionKey(final File dataDirectory) {
        final File encryptionKeyFile = new File(dataDirectory, ServerEncryptionKey.ENCRYPTION_KEY_FILE_NAME);
        _key = ServerEncryptionKey.loadEncryptionKey(encryptionKeyFile);

        if (_key == null) {
            throw new RuntimeException("Unable to load server key.");
        }
    }

    public ByteArray encrypt(final ByteArray data) {
        final byte[] dataBytes = data.getBytes();
        return ByteArray.wrap(_key.encrypt(dataBytes));
    }

    public ByteArray decrypt(final ByteArray cipherData) {
        final byte[] cipherBytes = cipherData.getBytes();
        return ByteArray.wrap(_key.decrypt(cipherBytes));
    }
}
