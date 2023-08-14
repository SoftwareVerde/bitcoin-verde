package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.token.CashToken;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class TransactionOutputInflater {
    protected static final Integer MAX_COMMITMENT_LENGTH = 65535; // The max commitment length that can be parsed (not necessarily what is valid). https://github.com/bitjson/cashtokens#token-prefix-validation

    protected Tuple<LockingScript, CashToken> _fromLegacyScriptBytes(final Integer scriptByteCount, final ByteArrayReader byteArrayReader) {
        final CashToken cashToken;
        final ByteArray lockingScriptBytes;
        final byte prefixByte = byteArrayReader.peakByte();
        if (prefixByte == CashToken.PREFIX) {
            final int cashTokenStartIndex = byteArrayReader.getPosition();
            byteArrayReader.skipBytes(1);
            final Sha256Hash tokenPrefix = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.LITTLE));

            final ByteArray bitfield = MutableByteArray.wrap(new byte[] { byteArrayReader.readByte() });
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

            final ByteArray commitment;
            if (hasCommitmentLength) {
                if (! hasNft) { return null; } // A token prefix encoding HAS_COMMITMENT_LENGTH without HAS_NFT is invalid.
                final CompactVariableLengthInteger commitmentLength = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
                if (! commitmentLength.isCanonical()) { return null; }
                if (commitmentLength.value > MAX_COMMITMENT_LENGTH) { return null; }
                if (commitmentLength.value == 0L) { return null; }

                commitment = MutableByteArray.wrap(byteArrayReader.readBytes(commitmentLength.intValue()));
            }
            else {
                commitment = null;
            }

            final Long amount;
            if (hasAmount) {
                final CompactVariableLengthInteger variableLengthInteger = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
                if (! variableLengthInteger.isCanonical()) { return null; }

                amount = variableLengthInteger.value;

                if (amount < 1L) { return null; }
                // Max value (0x7FFFFFFFFFFFFFFF) is inherently covered by the above <1 check and java Long...
            }
            else {
                if (! hasNft) { return null; } // A token prefix encoding no tokens (both HAS_NFT and HAS_AMOUNT are unset) is invalid.
                amount = null;
            }

            if (byteArrayReader.didOverflow()) { return null; } // Abort parsing before reading the end position to prevent negative ByteArray allocation.

            final int cashTokenEndIndex = byteArrayReader.getPosition();
            final int cashTokenScriptLength = (cashTokenEndIndex - cashTokenStartIndex);
            cashToken = new CashToken(tokenPrefix, nftCapability, commitment, amount);

            final int lockingScriptByteCount = (scriptByteCount - cashTokenScriptLength);
            lockingScriptBytes = MutableByteArray.wrap(byteArrayReader.readBytes(lockingScriptByteCount, Endian.BIG));
        }
        else {
            lockingScriptBytes = MutableByteArray.wrap(byteArrayReader.readBytes(scriptByteCount, Endian.BIG));
            cashToken = null;
        }

        // NOTE: Using an ImmutableLockingScript may be important for the performance of ScriptPatternMatcher::isProvablyUnspendable,
        //  which is used for UTXO acceptance into the UTXO Cache.
        final LockingScript lockingScript = new ImmutableLockingScript(lockingScriptBytes);

        return new Tuple<>(lockingScript, cashToken);
    }

    protected MutableTransactionOutput _fromByteArrayReader(final Integer index, final ByteArrayReader byteArrayReader) {
        final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();

        transactionOutput._amount = byteArrayReader.readLong(8, Endian.LITTLE);
        transactionOutput._index = index;

        final CompactVariableLengthInteger scriptByteCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
        if (! scriptByteCount.isCanonical()) { return null; }
        if ( (scriptByteCount.intValue() > Script.MAX_BYTE_COUNT) || (scriptByteCount.intValue() < 0)) { return null; }

        final Tuple<LockingScript, CashToken> scriptTuple = _fromLegacyScriptBytes(scriptByteCount.intValue(), byteArrayReader);
        if (scriptTuple == null) { return null; }

        transactionOutput._lockingScript = scriptTuple.first;
        transactionOutput._cashToken = scriptTuple.second;

        if (byteArrayReader.didOverflow()) { return null; }

        return transactionOutput;
    }

    public void _debugBytes(final ByteArrayReader byteArrayReader) {
        Logger.debug("Tx Output: Amount: " + MutableByteArray.wrap(byteArrayReader.readBytes(8)));

        final CompactVariableLengthInteger variableLengthInteger = CompactVariableLengthInteger.peakVariableLengthInteger(byteArrayReader);
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

    public Tuple<LockingScript, CashToken> fromLegacyScriptBytes(final ByteArray legacyScriptBytes) {
        final Integer byteCount = legacyScriptBytes.getByteCount();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(legacyScriptBytes);
        return _fromLegacyScriptBytes(byteCount, byteArrayReader);
    }
}
