package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.transaction.token.CashToken;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.bytearray.Endian;

public class TransactionOutputInflater {
    protected MutableTransactionOutput _fromByteArrayReader(final Integer index, final ByteArrayReader byteArrayReader) {
        final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();

        transactionOutput._amount = byteArrayReader.readLong(8, Endian.LITTLE);
        transactionOutput._index = index;

        final byte prefixByte = byteArrayReader.peakByte();
        final Sha256Hash tokenPrefix;
        final ByteArray lockingScriptBytes;
        if (prefixByte == CashToken.PREFIX) {
            byteArrayReader.skipBytes(1); // Pop the CashToken prefix byte...
            final int tokenPrefixAndScriptByteCount = byteArrayReader.readVariableLengthInteger().intValue();
            tokenPrefix = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT));

            final byte bitfieldMask = (byte) 0xF0;
            final ByteArray bitfield = MutableByteArray.wrap(new byte[] { (byte) (byteArrayReader.readByte() & bitfieldMask) });
            if (bitfield.getBit(0L)) { return null; } // RESERVED_BIT must be unset.
            final boolean hasCommitmentLength = bitfield.getBit(1L);
            final boolean hasNft = bitfield.getBit(2L);
            final boolean hasAmount = bitfield.getBit(3L);

            final CashToken.NftCapability nftCapability;
            final byte nftBitField = (byte) (bitfield.getByte(0) & 0x0F);
            if (hasNft) {
                nftCapability = CashToken.NftCapability.fromByte(nftBitField);
                if (nftCapability == null) { return null; } // Reserved values are not permitted.
            }
            else {
                if (nftBitField != 0x00) { return null; } // NFT flags must be unset if the bitfield did not register as an NFT.
                nftCapability = null;
            }

            if (hasCommitmentLength) {
                final int commitmentLength = byteArrayReader.readVariableLengthInteger().intValue();

            }

            final int scriptByteCount = (tokenPrefixAndScriptByteCount - Sha256Hash.BYTE_COUNT);
            lockingScriptBytes = MutableByteArray.wrap(byteArrayReader.readBytes(scriptByteCount, Endian.LITTLE));
        }
        else {
            tokenPrefix = null;
            final Integer scriptByteCount = byteArrayReader.readVariableLengthInteger().intValue();
            lockingScriptBytes = MutableByteArray.wrap(byteArrayReader.readBytes(scriptByteCount, Endian.BIG));
        }

        // NOTE: Using an ImmutableLockingScript may be important for the performance of ScriptPatternMatcher::isProvablyUnspendable,
        //  which is used for UTXO acceptance into the UTXO Cache.
        transactionOutput._lockingScript = new ImmutableLockingScript(lockingScriptBytes);

        if (byteArrayReader.didOverflow()) { return null; }

        return transactionOutput;
    }

    public void _debugBytes(final ByteArrayReader byteArrayReader) {
        Logger.debug("Tx Output: Amount: " + MutableByteArray.wrap(byteArrayReader.readBytes(8)));

        final ByteArrayReader.CompactVariableLengthInteger variableLengthInteger = byteArrayReader.peakVariableLengthInteger();
        Logger.debug("Tx Output: Script Byte Count: " + HexUtil.toHexString(byteArrayReader.readBytes(variableLengthInteger.bytesConsumedCount)));
        Logger.debug("Tx Output: Script: " + HexUtil.toHexString(byteArrayReader.readBytes((int) variableLengthInteger.value)));
    }

    public MutableTransactionOutput fromBytes(final Integer index, final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(index, byteArrayReader);
    }

    public MutableTransactionOutput fromBytes(final Integer index, final ByteArray byteArray) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(byteArray);
        return _fromByteArrayReader(index, byteArrayReader);
    }

    public MutableTransactionOutput fromBytes(final Integer index, final byte[] bytes) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
        return _fromByteArrayReader(index, byteArrayReader);
    }
}
