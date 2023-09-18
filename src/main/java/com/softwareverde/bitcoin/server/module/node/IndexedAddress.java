package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.transaction.output.identifier.ShortTransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class IndexedAddress {
    public static IndexedAddress fromBytes(final ByteArray byteArray) {
        final AddressInflater addressInflater = new AddressInflater();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(byteArray);

        final int addressByteCount = byteArrayReader.readInteger(1); // Address Byte Count, 1 byte
        final Address address = addressInflater.fromBytes(MutableByteArray.wrap(byteArrayReader.readBytes(addressByteCount))); // Address

        final MutableList<ShortTransactionOutputIdentifier> receivedOutputs;
        {
            final CompactVariableLengthInteger compactOutputsCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader); // Outputs Count, variable
            final int outputsCount = compactOutputsCount.intValue();
            receivedOutputs = new MutableArrayList<>(outputsCount);
            for (int j = 0; j < outputsCount; ++j) {
                final Long transactionId = ByteUtil.bytesToLong(byteArrayReader.readBytes(4)); // NOTE: Only 4 bytes.
                final CompactVariableLengthInteger compactVariableLengthInteger = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader); // Output Index, variable
                final Integer outputIndex = compactVariableLengthInteger.intValue();

                final ShortTransactionOutputIdentifier transactionOutputIdentifier = new ShortTransactionOutputIdentifier(transactionId, outputIndex);
                receivedOutputs.add(transactionOutputIdentifier);
            }
        }

        return new IndexedAddress(address, receivedOutputs);
    }

    protected final Address _address;
    protected final MutableList<ShortTransactionOutputIdentifier> _receivedOutputs;
    protected ByteArray _cachedBytes;

    protected void _cacheBytes() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        final int addressByteCount = (_address != null ? _address.getByteCount() : 0);
        byteArrayBuilder.appendByte((byte) addressByteCount);
        if (_address != null) {
            byteArrayBuilder.appendBytes(_address);
        }

        { // Transaction Outputs
            final int receivedOutputsCount = _receivedOutputs.getCount();
            final ByteArray outputCountBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(receivedOutputsCount);
            byteArrayBuilder.appendBytes(outputCountBytes);
            for (final ShortTransactionOutputIdentifier transactionOutputIdentifier : _receivedOutputs) {
                final Long outputTransactionId = transactionOutputIdentifier.getTransactionId();
                final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

                final byte[] outputTransactionIdBytes = ByteUtil.integerToBytes(outputTransactionId); // NOTE: Only stored as 4 bytes.
                byteArrayBuilder.appendBytes(outputTransactionIdBytes);

                final ByteArray outputIndexBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(outputIndex);
                byteArrayBuilder.appendBytes(outputIndexBytes);
            }
        }

        _cachedBytes = byteArrayBuilder;
    }

    public IndexedAddress(final Address address) {
        _address = address;
        _receivedOutputs = new MutableArrayList<>();
    }

    public IndexedAddress(final Address address, final MutableList<ShortTransactionOutputIdentifier> receivedOutputs) {
        _address = address;
        _receivedOutputs = receivedOutputs;
    }

    public Address getAddress() {
        return _address;
    }

    public List<ShortTransactionOutputIdentifier> getTransactionOutputs() {
        return _receivedOutputs;
    }

    public void addTransactionOutput(final ShortTransactionOutputIdentifier transactionOutputIdentifier) {
        _cachedBytes = null;
        _receivedOutputs.add(transactionOutputIdentifier);
    }

    public void add(final IndexedAddress indexedAddress) {
        _cachedBytes = null;
        _receivedOutputs.addAll(indexedAddress._receivedOutputs);
    }

    public ByteArray getBytes() {
        if (_cachedBytes == null) {
            _cacheBytes();
        }
        return _cachedBytes;
    }

    public Integer getByteCount() {
        if (_cachedBytes == null) {
            _cacheBytes();
        }

        return _cachedBytes.getByteCount();
    }
}
