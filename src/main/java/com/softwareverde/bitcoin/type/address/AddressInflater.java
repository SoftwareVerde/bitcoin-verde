package com.softwareverde.bitcoin.type.address;

import com.softwareverde.bitcoin.type.key.PrivateKey;
import com.softwareverde.bitcoin.type.key.PublicKey;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;

public class AddressInflater {
    protected byte[] _hashPublicKey(final PublicKey publicKey) {
        return BitcoinUtil.ripemd160(BitcoinUtil.sha256(publicKey.getBytes()));
    }

    public Address fromPrivateKey(final PrivateKey privateKey) {
        final PublicKey publicKey = privateKey.getPublicKey();
        final byte[] rawBitcoinAddress = _hashPublicKey(publicKey);
        return new Address(rawBitcoinAddress);
    }

    public CompressedAddress compressedFromPrivateKey(final PrivateKey privateKey) {
        final PublicKey publicKey = privateKey.getPublicKey();
        final byte[] rawBitcoinAddress = _hashPublicKey(publicKey);
        return new CompressedAddress(rawBitcoinAddress);
    }

    public Address fromPublicKey(final PublicKey publicKey) {
        final byte[] rawBitcoinAddress = _hashPublicKey(publicKey);
        return new Address(rawBitcoinAddress);
    }

    public CompressedAddress compressedFromPublicKey(final PublicKey publicKey) {
        final byte[] rawBitcoinAddress = _hashPublicKey(publicKey);
        return new CompressedAddress(rawBitcoinAddress);
    }

    public Address fromBase58Check(final String base58CheckString) {
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
            case Address.PREFIX: { return new Address(bytesWithoutPrefixAndWithoutChecksum); }
            case CompressedAddress.PREFIX: { return new CompressedAddress(bytesWithoutPrefixAndWithoutChecksum); }
            case PayToScriptHashAddress.PREFIX: { return new PayToScriptHashAddress(bytesWithoutPrefixAndWithoutChecksum); }
            default: {
                Logger.log("NOTICE: Unknown Address Prefix: 0x"+ HexUtil.toHexString(new byte[] { prefix }));
                return null;
            }
        }
    }
}
