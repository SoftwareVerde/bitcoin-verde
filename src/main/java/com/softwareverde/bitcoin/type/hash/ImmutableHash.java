package com.softwareverde.bitcoin.type.hash;

import com.softwareverde.bitcoin.util.ByteUtil;

public class ImmutableHash implements Hash {
    private final MutableHash _mutableHash;

    public ImmutableHash() {
        _mutableHash = new MutableHash();
    }

    public ImmutableHash(final Hash mutableHash) {
        _mutableHash = new MutableHash();
        _mutableHash.setBytes(mutableHash);
    }

    public ImmutableHash(final byte[] bytes) {
        _mutableHash = new MutableHash(ByteUtil.copyBytes(bytes));
    }

    @Override
    public byte get(final int index) {
        return _mutableHash.get(index);
    }

    @Override
    public byte[] toReversedEndian() {
        return ByteUtil.reverseEndian(_mutableHash.getBytes());
    }

    @Override
    public byte[] getBytes() {
        return ByteUtil.copyBytes(_mutableHash.getBytes());
    }

    @Override
    public int hashCode() {
        return _mutableHash.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) { return false; }
        if (! (obj instanceof Hash)) { return false; }
        final Hash object = (Hash) obj;
        return ByteUtil.areEqual(_mutableHash._bytes, object.getBytes());
    }

    @Override
    public String toString() {
        return _mutableHash.toString();
    }
}
