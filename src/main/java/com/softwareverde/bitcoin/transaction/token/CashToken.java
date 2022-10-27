package com.softwareverde.bitcoin.transaction.token;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

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
