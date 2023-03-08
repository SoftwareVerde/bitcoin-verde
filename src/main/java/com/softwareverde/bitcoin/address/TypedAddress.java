package com.softwareverde.bitcoin.address;

import com.softwareverde.util.Util;

public interface TypedAddress {
    static TypedAddress wrap(final AddressType addressType, final Address address) {
        return new _TypedAddress(addressType, address);
    }

    Address getBytes();
    AddressType getType();
}

class _TypedAddress implements TypedAddress {
    protected final Address _address;
    protected final AddressType _type;

    public _TypedAddress(final AddressType type, final Address address) {
        _address = address;
        _type = type;
    }

    @Override
    public Address getBytes() {
        return _address;
    }

    @Override
    public AddressType getType() {
        return _type;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == this) { return true; }
        if (! (object instanceof TypedAddress)) { return false; }

        final TypedAddress typedAddress = (TypedAddress) object;
        if (! Util.areEqual(_type, typedAddress.getType())) { return false; }
        if (! Util.areEqual(_address, typedAddress.getBytes())) { return false; }
        return true;
    }

    @Override
    public int hashCode() {
        return (_address.hashCode() + _type.hashCode());
    }
}
