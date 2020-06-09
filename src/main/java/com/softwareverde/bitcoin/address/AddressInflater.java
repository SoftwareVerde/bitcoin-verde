package com.softwareverde.bitcoin.address;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.secp256k1.key.PrivateKey;
import com.softwareverde.security.secp256k1.key.PublicKey;
import com.softwareverde.security.util.HashUtil;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

public class AddressInflater {
    protected byte[] _hashPublicKey(final PublicKey publicKey) {
        return HashUtil.ripemd160(HashUtil.sha256(publicKey.getBytes()));
    }

    protected Address _fromPublicKey(final PublicKey publicKey, final Boolean asCompressed) {
        if (asCompressed) {
            final byte[] rawBitcoinAddress = _hashPublicKey(publicKey.compress());
            return new CompressedAddress(rawBitcoinAddress);
        }
        else {
            final byte[] rawBitcoinAddress = _hashPublicKey(publicKey);
            return new Address(rawBitcoinAddress);
        }
    }

    protected Address _fromPrivateKey(final PrivateKey privateKey, final Boolean asCompressed) {
        final PublicKey publicKey = privateKey.getPublicKey();

        if (asCompressed) {
            final PublicKey compressedPublicKey = publicKey.compress();
            final byte[] rawBitcoinAddress = _hashPublicKey(compressedPublicKey);
            return new CompressedAddress(rawBitcoinAddress);
        }
        else {
            final PublicKey uncompressedPublicKey = publicKey.decompress();
            final byte[] rawBitcoinAddress = _hashPublicKey(uncompressedPublicKey);
            return new Address(rawBitcoinAddress);
        }
    }

    protected Address _fromBase58Check(final String base58CheckString, final Boolean isCompressed) {
        final byte[] bytesWithPrefixWithChecksum;

        try {
            bytesWithPrefixWithChecksum = BitcoinUtil.base58StringToBytes(base58CheckString);
        }
        catch (final Exception exception) {
            return null;
        }

        if (bytesWithPrefixWithChecksum.length < (Address.PREFIX_BYTE_COUNT + Address.CHECKSUM_BYTE_COUNT)) { return null; }

        final byte[] bytesWithoutPrefixAndWithoutChecksum = ByteUtil.copyBytes(bytesWithPrefixWithChecksum, Address.PREFIX_BYTE_COUNT, bytesWithPrefixWithChecksum.length - Address.CHECKSUM_BYTE_COUNT - Address.PREFIX_BYTE_COUNT);

        final byte prefix = bytesWithPrefixWithChecksum[0];

        final byte[] checksum = ByteUtil.copyBytes(bytesWithPrefixWithChecksum, bytesWithPrefixWithChecksum.length - Address.CHECKSUM_BYTE_COUNT, Address.CHECKSUM_BYTE_COUNT);

        final byte[] calculatedChecksum = Address._calculateChecksum(prefix, bytesWithoutPrefixAndWithoutChecksum);

        final Boolean checksumIsValid = (ByteUtil.areEqual(calculatedChecksum, checksum));
        if (! checksumIsValid) { return null; }

        switch (prefix) {
            case Address.PREFIX: {
                if (isCompressed) {
                    return new CompressedAddress(bytesWithoutPrefixAndWithoutChecksum);
                }
                else {
                    return new Address(bytesWithoutPrefixAndWithoutChecksum);
                }
            }
            case PayToScriptHashAddress.PREFIX: { return new PayToScriptHashAddress(bytesWithoutPrefixAndWithoutChecksum); }
            default: {
                Logger.warn("Unknown Address Prefix: 0x"+ HexUtil.toHexString(new byte[] { prefix }));
                return null;
            }
        }
    }

    protected Address _fromBase32Check(final String base32String, final Boolean isCompressed) {
        { // Check for mixed-casing...
            boolean hasUpperCase = false;
            boolean hasLowerCase = false;
            for (int i = 0; i < base32String.length(); ++i) {
                final char c = base32String.charAt(i);
                if (Character.isAlphabetic(c)) {
                    if (Character.isUpperCase(c)) {
                        hasUpperCase = true;
                    }
                    else {
                        hasLowerCase = true;
                    }
                }
            }
            if (hasUpperCase && hasLowerCase) { return null; }
        }

        final String prefix;
        final String base32WithoutPrefix;
        if (base32String.contains(":")) {
            final int separatorIndex = base32String.indexOf(':');
            prefix = base32String.substring(0, separatorIndex).toLowerCase();
            base32WithoutPrefix = base32String.substring(separatorIndex + 1).toLowerCase();
        }
        else {
            prefix = "bitcoincash";
            base32WithoutPrefix = base32String.toLowerCase();
        }

        final int checksumBitCount = 40;
        final int checksumCharacterCount = (checksumBitCount / 5);

        final ByteArray payloadBytes;
        final ByteArray checksum;
        {
            if (base32WithoutPrefix.length() < checksumCharacterCount) { return null; }

            final int checksumStartCharacterIndex = (base32WithoutPrefix.length() - checksumCharacterCount);
            payloadBytes = MutableByteArray.wrap(BitcoinUtil.base32StringToBytes(base32WithoutPrefix.substring(0, checksumStartCharacterIndex)));
            checksum = MutableByteArray.wrap(BitcoinUtil.base32StringToBytes(base32WithoutPrefix.substring(checksumStartCharacterIndex)));
        }
        if (payloadBytes == null) { return null; }
        if (checksum == null) { return null; }

        final byte version = payloadBytes.getByte(0);
        if ((version & 0x80) != 0x00) { return null; } // The version byte's most significant bit must be 0...
        final byte addressType = (byte) ((version >> 3) & 0x0F);
        final int hashByteCount = (20 + ((version & 0x07) * 4));

        final ByteArray hash = MutableByteArray.wrap(payloadBytes.getBytes(1, hashByteCount));

        final ByteArray checksumPayload = AddressInflater.buildBase32ChecksumPreImage(prefix, version, hash);

        final ByteArray calculatedChecksum = AddressInflater.calculateBase32Checksum(checksumPayload);
        if (! Util.areEqual(calculatedChecksum, checksum)) { return null; }

        if (addressType == PayToScriptHashAddress.BASE_32_PREFIX) { // P2SH
            return new PayToScriptHashAddress(hash.getBytes());
        }

        if (addressType == Address.BASE_32_PREFIX) { // P2PKH
            if (isCompressed) {
                return new CompressedAddress(hash.getBytes());
            }
            else {
                return new Address(hash.getBytes());
            }
        }

        if (isCompressed) {
            return new CompressedAddress(hash.getBytes());
        }
        else {
            return new Address(hash.getBytes());
        }
    }

    /**
     * Returns the preImage for the provided prefix/version/hash provided.
     *  The preImage is returned as an array of 5-bit integers.
     */
    public static ByteArray buildBase32ChecksumPreImage(final String prefix, final Byte version, final ByteArray hash) {
        final ByteArrayBuilder checksumPayload = new ByteArrayBuilder();
        for (final char c : prefix.toCharArray()) {
            checksumPayload.appendByte((byte) (c & 0x1F));
        }
        checksumPayload.appendByte((byte) 0x00);

        if (version != null) {
            // 0b01234567 >> 3          -> 0bxxx01234
            // 0b01234567 & 0x07 << 2   -> 0bxxx567xx
            final byte versionByte0 = (byte) (version >> 3);
            checksumPayload.appendByte(versionByte0);

            final byte versionByte1 = (byte) ((version & 0x07) << 2);
            if (hash.isEmpty()) {
                checksumPayload.appendByte(versionByte1);
            }
            else {
                final MutableByteArray b = new MutableByteArray(1);
                b.setByte(0, versionByte1);

                int writeBitIndex = 3;
                for (int hashReadBit = 0; hashReadBit < (hash.getByteCount() * 8); ++hashReadBit) {
                    b.setBit(((writeBitIndex % 5) + 3), hash.getBit(hashReadBit));
                    writeBitIndex += 1;

                    if ((writeBitIndex % 5) == 0) {
                        checksumPayload.appendByte(b.getByte(0));
                        b.setByte(0, (byte) 0x00);
                    }
                }
                if ((writeBitIndex % 5) != 0) {
                    checksumPayload.appendByte(b.getByte(0));
                }
            }
        }

        checksumPayload.appendBytes(new MutableByteArray(8));

        return MutableByteArray.wrap(checksumPayload.build());
    }

    /**
     * Creates a checksum as 5-bit integers for byteArray.
     *  byteArray should be formatted as an array of 5-bit integers.
     */
    public static ByteArray calculateBase32Checksum(final ByteArray byteArray) {
        long c = 0x01;
        for (int i = 0; i < byteArray.getByteCount(); ++i) {
            final byte d = byteArray.getByte(i);

            final long c0 = (c >> 35);
            c = (((c & 0x07FFFFFFFFL) << 5) ^ d);

            if ((c0 & 0x01) != 0x00) { c ^= 0x98F2BC8E61L; }
            if ((c0 & 0x02) != 0x00) { c ^= 0x79B76D99E2L; }
            if ((c0 & 0x04) != 0x00) { c ^= 0xF33E5FB3C4L; }
            if ((c0 & 0x08) != 0x00) { c ^= 0xAE2EABE2A8L; }
            if ((c0 & 0x10) != 0x00) { c ^= 0x1E4F43E470L; }
        }

        final long checksum = (c ^ 0x01);
        final byte[] checksumBytes = ByteUtil.longToBytes(checksum);

        final MutableByteArray checksumByteArray = new MutableByteArray(5);
        for (int i = 0; i < 5; ++i) {
            checksumByteArray.setByte(i, checksumBytes[i + 3]);
        }
        return checksumByteArray;
    }

    public Address fromPrivateKey(final PrivateKey privateKey) {
        return _fromPrivateKey(privateKey, false);
    }

    public Address fromPrivateKey(final PrivateKey privateKey, final Boolean asCompressed) {
        return _fromPrivateKey(privateKey, asCompressed);
    }

    public Address fromBytes(final ByteArray bytes) {
        if (bytes.getByteCount() != Address.BYTE_COUNT) { return null; }
        return new Address(bytes.getBytes());
    }

    public Address fromBytes(final ByteArray bytes, final Boolean isCompressed) {
        if (bytes.getByteCount() != Address.BYTE_COUNT) { return null; }
        if (isCompressed) {
            return new CompressedAddress(bytes.getBytes());
        }
        else {
            return new Address(bytes.getBytes());
        }
    }

    public Address fromPublicKey(final PublicKey publicKey) {
        return _fromPublicKey(publicKey, publicKey.isCompressed());
    }

    public Address fromPublicKey(final PublicKey publicKey, final Boolean asCompressed) {
        return _fromPublicKey(publicKey, asCompressed);
    }

    public Address fromBase58Check(final String base58CheckString) {
        return _fromBase58Check(base58CheckString, false);
    }

    /**
     * Returns a CompressedAddress from the base58CheckString.
     *  NOTE: Validation that the string is actually derived from a compressed PublicKey is impossible,
     *  therefore, only use this function if the sourced string is definitely a compressed PublicKey.
     */
    public Address fromBase58Check(final String base58CheckString, final Boolean isCompressed) {
        return _fromBase58Check(base58CheckString, isCompressed);
    }

    public Address fromBase32Check(final String base32String) {
        return _fromBase32Check(base32String, false);
    }

    public Address fromBase32Check(final String base32String, final Boolean isCompressed) {
        return _fromBase32Check(base32String, isCompressed);
    }
}
