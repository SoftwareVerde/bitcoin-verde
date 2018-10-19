package com.softwareverde.bitcoin.jni;

import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.io.Logger;
import com.softwareverde.util.jni.NativeUtil;
import org.apache.commons.lang3.SystemUtils;

import static com.softwareverde.bitcoin.jni.bin.NativeUnspentTransactionOutputCache.*;

public class NativeUnspentTransactionOutputCache {
    private static final boolean _libraryLoadedCorrectly;

    static {
        boolean isEnabled = true;
        try {
            final String extension;
            {
                if (SystemUtils.IS_OS_WINDOWS) {
                    extension = "dll";
                }
                else if (SystemUtils.IS_OS_MAC) {
                    extension = "dylib";
                }
                else {
                    extension = "so";
                }
            }

            NativeUtil.loadLibraryFromJar("/lib/utxocache." + extension);
        }
        catch (final Exception exception) {
            Logger.log("NOTICE: utxocache failed to load.");
            isEnabled = false;
        }
        _libraryLoadedCorrectly = isEnabled;
    }

    public static boolean isEnabled() {
        return _libraryLoadedCorrectly;
    }

    public static void init() {
        _init();
    }

    public static void destroy() {
        _destroy();
    }

    protected Integer _cacheId;

    public NativeUnspentTransactionOutputCache() {
        _cacheId = _createCache();
    }

    public void cacheUnspentTransactionOutputId(final Sha256Hash transactionHash, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) {
        if (_cacheId == null) { return; }
        _cacheUnspentTransactionOutputId(_cacheId, transactionHash.getBytes(), transactionOutputIndex, transactionOutputId.longValue());
    }

    public void delete() {
        _deleteCache(_cacheId);
        _cacheId = null;
    }
}
