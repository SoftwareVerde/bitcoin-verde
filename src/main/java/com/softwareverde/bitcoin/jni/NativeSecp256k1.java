/*
 * Copyright 2013 Google Inc.
 * Copyright 2014-2016 the libsecp256k1 contributors
 * Copyright 2018 Software Verde, LLC <license@softwareverde.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.softwareverde.bitcoin.jni;

import com.softwareverde.logging.Logger;
import com.softwareverde.util.SystemUtil;
import com.softwareverde.util.jni.NativeUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.bitcoin.NativeSecp256k1.secp256k1_destroy_context;
import static org.bitcoin.NativeSecp256k1.secp256k1_ecdsa_verify;
import static org.bitcoin.Secp256k1Context.secp256k1_init_context;

// NOTE: The last time this message was updated, the included secp256k1 library was built from git hash: 452d8e4

/**
 * <p>This class holds native methods to handle ECDSA verification.</p>
 *
 * <p>You can find an example library that can be used for this at https://github.com/bitcoin/secp256k1</p>
 *
 * <p>To recompile libsecp256k1 for use with java, ensure JAVA_HOME is set, run
 * `./autogen.sh`
 * `./configure --enable-jni --enable-experimental --enable-module-ecdh`
 * and `make`.
 * Then, copy `.libs/libsecp256k1.*` to `src/main/resources/lib`.
 * </p>
 */
public class NativeSecp256k1 {
    private static final boolean _libraryLoadedCorrectly;
    private static final long _context;
    private static final ReentrantReadWriteLock _reentrantReadWriteLock = new ReentrantReadWriteLock();
    private static final Lock _readLock = _reentrantReadWriteLock.readLock();
    private static final Lock _writeLock = _reentrantReadWriteLock.writeLock();
    private static final ThreadLocal<ByteBuffer> _nativeECDSABuffer = new ThreadLocal<ByteBuffer>();

    static {
        boolean isEnabled = true;
        long contextRef = -1;
        try {
            final String extension;
            {
                if (SystemUtil.isWindowsOperatingSystem()) {
                    extension = "dll";
                }
                else if (SystemUtil.isMacOperatingSystem()) {
                    extension = "dylib";
                }
                else {
                    extension = "so";
                }
            }

            NativeUtil.loadLibraryFromJar("/lib/libsecp256k1." + extension);
            contextRef = secp256k1_init_context();
        }
        catch (final Exception exception) {
            Logger.warn("NOTICE: libsecp256k1 failed to load.");
            isEnabled = false;
        }
        _libraryLoadedCorrectly = isEnabled;
        _context = contextRef;
    }

    protected static ByteBuffer _getByteBuffer() {
        ByteBuffer byteBuff = _nativeECDSABuffer.get();
        if ((byteBuff == null) || (byteBuff.capacity() < 520)) {
            byteBuff = ByteBuffer.allocateDirect(520);
            byteBuff.order(ByteOrder.nativeOrder());
            _nativeECDSABuffer.set(byteBuff);
        }
        return byteBuff;
    }

    public static boolean isEnabled() {
        return _libraryLoadedCorrectly;
    }

    public static long getContext() {
        return _context;
    }

    /**
     * Verifies the given secp256k1 signature in native code.
     *
     * @param data The data which was signed, must be exactly 32 bytes
     * @param signature The signature
     * @param pub The public key which did the signing
     */
    public static boolean verify(byte[] data, byte[] signature, byte[] pub) {
        if (data.length != 32) { throw new RuntimeException("Invalid data length. Required 32 bytes; found "+ data.length + " bytes."); }
        if (! _libraryLoadedCorrectly) { throw new RuntimeException("Cannot run NativeSecp256k1. Library failed to load."); }

        final ByteBuffer byteBuff = _getByteBuffer();

        byteBuff.rewind();
        byteBuff.put(data);
        byteBuff.put(signature);
        byteBuff.put(pub);

        _readLock.lock();
        try {
            return (secp256k1_ecdsa_verify(byteBuff, _context, signature.length, pub.length) == 1);
        }
        finally {
            _readLock.unlock();
        }
    }

    /**
     * libsecp256k1 Cleanup - This destroys the secp256k1 context object.
     * This should be called at the end of the program for proper cleanup of the context.
     */
    public static synchronized void shutdown() {
        _writeLock.lock();
        try {
            secp256k1_destroy_context(_context);
        }
        finally {
          _writeLock.unlock();
        }
    }
}
