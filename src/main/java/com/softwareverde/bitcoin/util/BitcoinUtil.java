package com.softwareverde.bitcoin.util;

import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.Base58Util;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class BitcoinUtil {

    public static byte[] sha1(final byte[] data) {
        try {
            final MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            return messageDigest.digest(data);
        }
        catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static Hash sha256(final ByteArray data) {
        return new MutableHash(sha256(data.getBytes()));
    }

    public static byte[] sha256(final byte[] data) {
        try {
            final MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return messageDigest.digest(data);
        }
        catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] ripemd160(final byte[] data) {
        final RIPEMD160Digest ripemd160Digest = new RIPEMD160Digest();
        ripemd160Digest.update(data, 0, data.length);
        final byte[] output = new byte[ripemd160Digest.getDigestSize()];
        ripemd160Digest.doFinal(output, 0);
        return output;
    }

    public static String toBase58String(final byte[] bytes) {
        return Base58Util.toBase58String(bytes);
    }

    public static byte[] base58StringToBytes(final String base58String) {
        return Base58Util.base58StringToByteArray(base58String);
    }

    public static String reverseEndianString(final String string) {
        final int charCount = string.length();
        final char[] reverseArray = new char[charCount];
        for (int i=0; i<charCount/2; ++i) {
            int index = (charCount - (i*2)) - 1;
            reverseArray[i*2] = string.charAt(index - 1);
            reverseArray[(i*2) + 1] = string.charAt(index);
        }
        return new String(reverseArray);
    }

    public static int log2(int x) {
        int log = 0;

        if ((x & 0xffff0000) != 0) {
            x >>>= 16;
            log = 16;
        }

        if (x >= 256) {
            x >>>= 8;
            log += 8;
        }

        if (x >= 16) {
            x >>>= 4;
            log += 4;
        }

        if (x >= 4) {
            x >>>= 2;
            log += 2;
        }

        return log + (x >>> 1);
    }

    protected BitcoinUtil() { }

}
