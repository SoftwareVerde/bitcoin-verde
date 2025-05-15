package com.softwareverde.bitcoin.server.module.node.indexing;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.transaction.output.identifier.ShortTransactionOutputIdentifier;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;

public interface IndexedAddress {
    void cacheBytes();

    Address getAddress();

    List<ShortTransactionOutputIdentifier> getTransactionOutputs();

    int getTransactionOutputsCount();

    void addTransactionOutput(ShortTransactionOutputIdentifier transactionOutputIdentifier);

    void add(IndexedAddress indexedAddress);

    ByteArray getBytes();

    Integer getByteCount();
}
