package com.softwareverde.bitcoin.secp256k1.privatekey;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.util.Base58Util;

public class PrivateKeyDeflater {
    public static class WalletImportFormat extends PrivateKeyInflater.WalletImportFormat { }

    public String toWalletImportFormat(final PrivateKey privateKey, final Boolean asCompressed) {
        final MutableByteArray extendedKeyBytes = new MutableByteArray(asCompressed ? WalletImportFormat.COMPRESSED_EXTENDED_KEY_BYTE_COUNT : WalletImportFormat.UNCOMPRESSED_EXTENDED_KEY_BYTE_COUNT);
        extendedKeyBytes.setByte(0, WalletImportFormat.MAIN_NET_PREFIX);
        if (asCompressed) {
            final int compressedFlagByteIndex = (WalletImportFormat.NETWORK_PREFIX_BYTE_COUNT + PrivateKey.KEY_BYTE_COUNT);
            extendedKeyBytes.setByte(compressedFlagByteIndex, (byte) 0x01);
        }
        extendedKeyBytes.setBytes(WalletImportFormat.NETWORK_PREFIX_BYTE_COUNT, privateKey);

        final ByteArray fullChecksum = HashUtil.doubleSha256(extendedKeyBytes);

        final int byteCount = (asCompressed ? WalletImportFormat.COMPRESSED_BYTE_COUNT : WalletImportFormat.UNCOMPRESSED_BYTE_COUNT);
        final MutableByteArray wifKeyBytes = new MutableByteArray(byteCount);
        wifKeyBytes.setBytes(0, extendedKeyBytes);
        wifKeyBytes.setBytes(extendedKeyBytes.getByteCount(), fullChecksum.getBytes(0, WalletImportFormat.CHECKSUM_BYTE_COUNT));

        return Base58Util.toBase58String(wifKeyBytes.unwrap());
    }
}
