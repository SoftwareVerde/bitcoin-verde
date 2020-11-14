package com.softwareverde.bitcoin.secp256k1.privatekey;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.util.Base58Util;
import com.softwareverde.util.Util;

public class PrivateKeyInflater {
    public static class WalletImportFormat {
        protected static final int NETWORK_PREFIX_BYTE_COUNT = 1;
        protected static final int CHECKSUM_BYTE_COUNT = 4;
        protected static final int OPTIONAL_COMPRESSED_FLAG_BYTE_COUNT = 1;
        protected static final int UNCOMPRESSED_EXTENDED_KEY_BYTE_COUNT = (NETWORK_PREFIX_BYTE_COUNT + PrivateKey.KEY_BYTE_COUNT);
        protected static final int COMPRESSED_EXTENDED_KEY_BYTE_COUNT = (NETWORK_PREFIX_BYTE_COUNT + PrivateKey.KEY_BYTE_COUNT + OPTIONAL_COMPRESSED_FLAG_BYTE_COUNT);

        public static byte MAIN_NET_PREFIX = (byte) 0x80;
        public static byte TEST_NET_PREFIX = (byte) 0xEF;
        public static final Integer UNCOMPRESSED_BYTE_COUNT = (UNCOMPRESSED_EXTENDED_KEY_BYTE_COUNT + CHECKSUM_BYTE_COUNT);
        public static final Integer COMPRESSED_BYTE_COUNT = (COMPRESSED_EXTENDED_KEY_BYTE_COUNT + CHECKSUM_BYTE_COUNT);
    }

    public PrivateKey fromWalletImportFormat(final String wifString) {
        final ByteArray decodedBase58 = ByteArray.wrap(Base58Util.base58StringToByteArray(wifString));

        final int decodedByteCount = decodedBase58.getByteCount();
        if ( (decodedByteCount != WalletImportFormat.COMPRESSED_BYTE_COUNT) && (decodedByteCount != WalletImportFormat.UNCOMPRESSED_BYTE_COUNT) ) { return null; }

        final byte networkPrefix = decodedBase58.getByte(0);
        if (! Util.areEqual(networkPrefix, WalletImportFormat.MAIN_NET_PREFIX)) { return null; } // TODO: Support/Detect TestNet...

        final int checksumByteCount = WalletImportFormat.CHECKSUM_BYTE_COUNT;
        final int checksumStartIndex = (decodedBase58.getByteCount() - checksumByteCount);
        final ByteArray checksum = ByteArray.wrap(decodedBase58.getBytes(checksumStartIndex, checksumByteCount));

        final int compressedByteCount = WalletImportFormat.COMPRESSED_BYTE_COUNT;
        final boolean isCompressed = Util.areEqual(decodedByteCount, compressedByteCount);

        final int extendedKeyByteCount = (isCompressed ? WalletImportFormat.COMPRESSED_EXTENDED_KEY_BYTE_COUNT : WalletImportFormat.UNCOMPRESSED_EXTENDED_KEY_BYTE_COUNT);
        final ByteArray extendedKeyBytes = ByteArray.wrap(decodedBase58.getBytes(0, extendedKeyByteCount));
        final ByteArray recalculatedFullChecksum = HashUtil.doubleSha256(extendedKeyBytes);
        final ByteArray recalculatedChecksum = ByteArray.wrap(recalculatedFullChecksum.getBytes(0, checksumByteCount));

        if (! Util.areEqual(recalculatedChecksum, checksum)) { return null; } // Checksum mismatch...

        return PrivateKey.fromBytes(extendedKeyBytes.getBytes(1, PrivateKey.KEY_BYTE_COUNT));
    }
}
