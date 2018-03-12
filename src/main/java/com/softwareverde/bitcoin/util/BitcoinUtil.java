package com.softwareverde.bitcoin.util;

import com.softwareverde.bitcoin.type.bytearray.ByteArray;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.security.encoding.Base58;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class BitcoinUtil {
    private final static char[] HEX_ALPHABET = "0123456789ABCDEF".toCharArray();

    /**
     * Returns an uppercase hex representation of the provided bytes without any prefix.
     */
    public static String toHexString(final byte[] bytes) {
        final char[] hexChars = new char[bytes.length * 2];
        for (int j=0; j<bytes.length; ++j) {
            final int v = (bytes[j] & 0xFF);
            hexChars[j * 2] = HEX_ALPHABET[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ALPHABET[v & 0x0F];
        }
        return new String(hexChars);
    }
    public static String toHexString(final ByteArray bytes) {
        return toHexString(bytes.getBytes());
    }

    /**
     * Returns the decoded bytes from an uppercase (or lowercase) hex representation without any prefix.
     */
    public static byte[] hexStringToByteArray(final String hexString) {
        final Integer stringLength = hexString.length();
        if (stringLength % 2 != 0) { return null; }

        final byte[] data = new byte[stringLength / 2];
        for (int i = 0; i < stringLength; i += 2) {
            data[i/2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) + Character.digit(hexString.charAt(i+1), 16));
        }
        return data;
    }

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
        return Base58.encode(bytes);
    }

    public static byte[] base58StringToBytes(final String base58String) {
        return Base58.decode(base58String);
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
