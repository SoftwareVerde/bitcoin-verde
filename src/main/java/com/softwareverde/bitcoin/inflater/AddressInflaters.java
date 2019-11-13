package com.softwareverde.bitcoin.inflater;

import com.softwareverde.bitcoin.address.AddressInflater;

public interface AddressInflaters extends Inflater {
    AddressInflater getAddressInflater();
}
