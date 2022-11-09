package com.softwareverde.bitcoin.transaction.token;

import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class CashToken implements Const {
    public static final byte PREFIX = (byte) 0xEF;

    public enum NftCapability {
        NONE(0x00), MUTABLE(0x01), MINTING(0x02);

        public final byte flag;

        NftCapability(final int flag) {
            this.flag = (byte) flag;
        }

        public static NftCapability fromByte(final byte b) {
            for (final NftCapability nftCapability : NftCapability.values()) {
                if (nftCapability.flag == b) {
                    return nftCapability;
                }
            }
            return null;
        }
    }

    protected byte _getBitfield() {
        final MutableByteArray byteArray = new MutableByteArray(1);

        final boolean hasCommitment = ( (_commitment != null) && (! _commitment.isEmpty()) );
        byteArray.setBit(1L, hasCommitment);

        final boolean hasNft = (_nftCapability != null);
        byteArray.setBit(2L, hasNft);

        final boolean hasAmount = ( (_tokenAmount != null) && (_tokenAmount > 0L) );
        byteArray.setBit(3L, hasAmount);

        if (hasNft) {
            final byte currentValue = byteArray.getByte(0);
            final byte newValue = (byte) (currentValue | _nftCapability.flag);
            byteArray.setByte(0, newValue);
        }

        return byteArray.getByte(0);
    }

    protected final Sha256Hash _tokenPrefix;
    protected final NftCapability _nftCapability;
    protected final ByteArray _commitment;
    protected final Long _tokenAmount;

    public CashToken(final Sha256Hash tokenPrefix, final NftCapability nftCapability, final ByteArray commitment, final Long tokenAmount) {
        _tokenPrefix = tokenPrefix;
        _nftCapability = nftCapability;
        _commitment = commitment;
        _tokenAmount = tokenAmount;
    }

    public Sha256Hash getTokenPrefix() {
        return _tokenPrefix;
    }

    public NftCapability getNftCapability() {
        return _nftCapability;
    }

    public ByteArray getCommitment() {
        return _commitment;
    }

    public Long getTokenAmount() {
        return _tokenAmount;
    }

    public ByteArray getBytes() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte(CashToken.PREFIX);
        byteArrayBuilder.appendBytes(_tokenPrefix, Endian.LITTLE);

        final byte bitfield = _getBitfield();
        byteArrayBuilder.appendByte(bitfield);

        final boolean hasCommitment = ( (_commitment != null) && (! _commitment.isEmpty()) );
        if (hasCommitment) {
            final ByteArray commitmentByteCountBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(_commitment.getByteCount());
            byteArrayBuilder.appendBytes(commitmentByteCountBytes);
            byteArrayBuilder.appendBytes(_commitment);
        }

        final boolean hasAmount = ( (_tokenAmount != null) && (_tokenAmount > 0L) );
        if (hasAmount) {
            final ByteArray tokenAmountBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(_tokenAmount);
            byteArrayBuilder.appendBytes(tokenAmountBytes);
        }

        return byteArrayBuilder;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == this) { return true; }
        if (! (object instanceof CashToken)) { return false; }

        final CashToken cashToken = (CashToken) object;
        if (! Util.areEqual(_tokenPrefix, cashToken._tokenPrefix)) { return false; }
        if (! Util.areEqual(_nftCapability, cashToken._nftCapability)) { return false; }
        if (! Util.areEqual(_commitment, cashToken._commitment)) { return false; }
        if (! Util.areEqual(_tokenAmount, cashToken._tokenAmount)) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = _tokenAmount.hashCode();

        if (_nftCapability != null) {
            hashCode += _nftCapability.hashCode();

        }
        if (_commitment != null) {
            hashCode += _commitment.hashCode();
        }

        if (_tokenAmount != null) {
            hashCode += _tokenAmount.hashCode();
        }

        return hashCode;
    }
}
