package com.softwareverde.bitcoin.address;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

public class AddressInflater {
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

    protected byte[] _hashPublicKey(final PublicKey publicKey) {
        return HashUtil.ripemd160(HashUtil.sha256(publicKey.getBytes()));
    }

    protected Address _fromPublicKey(final PublicKey publicKey, final Boolean asCompressed) {
        final byte[] rawBitcoinAddress = _hashPublicKey(asCompressed ? publicKey.compress() : publicKey.decompress());
        return new Address(rawBitcoinAddress);
    }

    protected Address _fromPrivateKey(final PrivateKey privateKey, final Boolean asCompressed) {
        final PublicKey publicKey = privateKey.getPublicKey();
        return _fromPublicKey(publicKey, asCompressed);
    }

    protected ParsedAddress _fromBase58Check(final String base58CheckString) {
        final ByteArray bytesWithPrefixWithChecksum;

        try {
            bytesWithPrefixWithChecksum = BitcoinUtil.base58StringToBytes(base58CheckString);
        }
        catch (final Exception exception) { return null; }

        if (bytesWithPrefixWithChecksum.getByteCount() < (ParsedAddress.PREFIX_BYTE_COUNT + ParsedAddress.CHECKSUM_BYTE_COUNT)) { return null; }

        final int byteCount = (bytesWithPrefixWithChecksum.getByteCount() - ParsedAddress.CHECKSUM_BYTE_COUNT - ParsedAddress.PREFIX_BYTE_COUNT);
        final Address bytesWithoutPrefixAndWithoutChecksum = new Address(bytesWithPrefixWithChecksum.getBytes(ParsedAddress.PREFIX_BYTE_COUNT, byteCount));

        final byte prefixByte = bytesWithPrefixWithChecksum.getByte(0);

        final int checksumStartIndex = (bytesWithPrefixWithChecksum.getByteCount() - ParsedAddress.CHECKSUM_BYTE_COUNT);
        final ByteArray checksum = ByteArray.wrap(ByteUtil.copyBytes(bytesWithPrefixWithChecksum, checksumStartIndex, ParsedAddress.CHECKSUM_BYTE_COUNT));

        final ByteArray calculatedChecksum = ParsedAddress.calculateChecksum(prefixByte, bytesWithoutPrefixAndWithoutChecksum);

        final Boolean checksumIsValid = ByteUtil.areEqual(calculatedChecksum, checksum);
        if (! checksumIsValid) { return null; }

        final AddressType addressType = AddressType.fromBase58Prefix(prefixByte);
        if (addressType == null) {
            Logger.info("Unknown Address Prefix: 0x"+ HexUtil.toHexString(new byte[] { prefixByte }));
            return null;
        }

        final boolean isTokenAware = AddressType.P2PKH.isTokenAware(prefixByte);
        return new ParsedAddress(addressType, isTokenAware, bytesWithoutPrefixAndWithoutChecksum);
    }

    protected ParsedAddress _fromBase32Check(final String base32String) {
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

        final boolean hadPrefix;
        final String prefix;
        final String base32WithoutPrefix;
        if (base32String.contains(":")) {
            final int separatorIndex = base32String.indexOf(':');
            prefix = base32String.substring(0, separatorIndex).toLowerCase();
            base32WithoutPrefix = base32String.substring(separatorIndex + 1).toLowerCase();
            hadPrefix = true;
        }
        else {
            prefix = "bitcoincash";
            base32WithoutPrefix = base32String.toLowerCase();
            hadPrefix = false;
        }

        final int checksumBitCount = 40;
        final int checksumCharacterCount = (checksumBitCount / 5);

        final ByteArray payloadBytes;
        final ByteArray checksum;
        {
            if (base32WithoutPrefix.length() < checksumCharacterCount) { return null; }

            final int checksumStartCharacterIndex = (base32WithoutPrefix.length() - checksumCharacterCount);
            payloadBytes = BitcoinUtil.base32StringToBytes(base32WithoutPrefix.substring(0, checksumStartCharacterIndex));
            checksum = BitcoinUtil.base32StringToBytes(base32WithoutPrefix.substring(checksumStartCharacterIndex));
        }
        if (payloadBytes == null) { return null; }
        if (checksum == null) { return null; }

        final byte version = payloadBytes.getByte(0);
        if ((version & 0x80) != 0x00) { return null; } // The version byte's most significant bit must be 0...
        final byte addressTypeByte = (byte) ((version >> 3) & 0x0F);

        final int hashByteCount;
        {
            final int sizeIndex = (version & 0x07);
            if (sizeIndex < 4) {
                // 0=20; 1=24; 2=28; 3=32
                 hashByteCount = (20 + (sizeIndex * 4));
            }
            else {
                // 4=40; 5=48; 6=56; 7=64
                hashByteCount = ((sizeIndex + 1) * 8);
            }
        }


        if (payloadBytes.getByteCount() < (hashByteCount + 1)) { return null; }
        final Address hashBytes = new Address(payloadBytes.getBytes(1, hashByteCount));

        final ByteArray checksumPayload = AddressInflater.buildBase32ChecksumPreImage(prefix, version, hashBytes);

        final ByteArray calculatedChecksum = AddressInflater.calculateBase32Checksum(checksumPayload);
        if (! Util.areEqual(calculatedChecksum, checksum)) { return null; }

        final AddressType addressType = AddressType.fromBase32Prefix(addressTypeByte);
        // if (addressType == null) { return null; }

        final boolean isTokenAware = AddressType.P2SH.isTokenAware(addressTypeByte);

        return new ParsedAddress(addressType, isTokenAware, hashBytes, AddressFormat.BASE_32, (hadPrefix ? prefix : null));
    }

    public Address fromPrivateKey(final PrivateKey privateKey) {
        return _fromPrivateKey(privateKey, false);
    }

    public Address fromPrivateKey(final PrivateKey privateKey, final Boolean asCompressed) {
        return _fromPrivateKey(privateKey, asCompressed);
    }

    public Address fromBytes(final ByteArray bytes) {
        if (! Address.isValidByteCount(bytes)) { return null; }
        return new Address(bytes);
    }

    public Address fromPublicKey(final PublicKey publicKey) {
        if (! publicKey.isValid()) { return null; }
        return _fromPublicKey(publicKey, publicKey.isCompressed());
    }

    public Address fromPublicKey(final PublicKey publicKey, final Boolean asCompressed) {
        if (! publicKey.isValid()) { return null; }
        return _fromPublicKey(publicKey, asCompressed);
    }

    public ParsedAddress fromBase58Check(final String base58CheckString) {
        return _fromBase58Check(base58CheckString);
    }

    public ParsedAddress fromBase32Check(final String base32String) {
        return _fromBase32Check(base32String);
    }
}
