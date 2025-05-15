package com.softwareverde.bitcoin.server.module.node.indexing;

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
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class DeflatedIndexedAddress implements IndexedAddress {
    public static DeflatedIndexedAddress fromBytes(final ByteArray byteArray) {
        final AddressInflater addressInflater = new AddressInflater();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(byteArray);

        final int addressByteCount = byteArrayReader.readInteger(1); // Address Byte Count, 1 byte
        final Address address = addressInflater.fromBytes(MutableByteArray.wrap(byteArrayReader.readBytes(addressByteCount))); // Address
        final DeflatedIndexedAddress deflatedIndexedAddress = new DeflatedIndexedAddress(address);

        final CompactVariableLengthInteger compactOutputsCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader); // Outputs Count, variable
        deflatedIndexedAddress._outputsCount = compactOutputsCount.intValue();

        final int remainingByteCount = byteArrayReader.remainingByteCount();
        deflatedIndexedAddress._receivedOutputsData.appendBytes(byteArrayReader.readBytes(remainingByteCount));

        deflatedIndexedAddress._cachedBytes = byteArray;
        return deflatedIndexedAddress;
    }

    protected final Address _address;
    protected int _outputsCount = 0;
    protected ByteArrayBuilder _receivedOutputsData = new ByteArrayBuilder();
    protected ByteArray _cachedBytes;

    protected List<ShortTransactionOutputIdentifier> _parseOutputsData() {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(_receivedOutputsData.build());
        final MutableList<ShortTransactionOutputIdentifier> receivedOutputs = new MutableArrayList<>(_outputsCount);
        for (int j = 0; j < _outputsCount; ++j) {
            final Long transactionId = ByteUtil.bytesToLong(byteArrayReader.readBytes(4)); // NOTE: Only 4 bytes.
            final CompactVariableLengthInteger compactVariableLengthInteger = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader); // Output Index, variable
            final Integer outputIndex = compactVariableLengthInteger.intValue();

            final ShortTransactionOutputIdentifier transactionOutputIdentifier = new ShortTransactionOutputIdentifier(transactionId, outputIndex);
            receivedOutputs.add(transactionOutputIdentifier);
        }
        return receivedOutputs;
    }

    public DeflatedIndexedAddress(final Address address) {
        _address = address;
    }

    public void cacheBytes() {
        this.getBytes();
    }

    public Address getAddress() {
        return _address;
    }

    public List<ShortTransactionOutputIdentifier> getTransactionOutputs() {
        return _parseOutputsData();
    }

    @Override
    public int getTransactionOutputsCount() {
        return _outputsCount;
    }

    public void addTransactionOutput(final ShortTransactionOutputIdentifier transactionOutputIdentifier) {
        _cachedBytes = null;
        _outputsCount += 1;

        final Long outputTransactionId = transactionOutputIdentifier.getTransactionId();
        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

        final byte[] outputTransactionIdBytes = ByteUtil.integerToBytes(outputTransactionId); // NOTE: Only stored as 4 bytes.
        _receivedOutputsData.appendBytes(outputTransactionIdBytes);

        final ByteArray outputIndexBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(outputIndex);
        _receivedOutputsData.appendBytes(outputIndexBytes);
    }

    public void add(final IndexedAddress indexedAddress) {
        _cachedBytes = null;

        if (indexedAddress instanceof DeflatedIndexedAddress) {
            final DeflatedIndexedAddress deflatedIndexedAddress = (DeflatedIndexedAddress) indexedAddress;
            _outputsCount += deflatedIndexedAddress.getTransactionOutputsCount();
            _receivedOutputsData.appendBytes(deflatedIndexedAddress._receivedOutputsData);
        }
        else {
            final List<ShortTransactionOutputIdentifier> receivedOutputs = indexedAddress.getTransactionOutputs();
            for (final ShortTransactionOutputIdentifier transactionOutputIdentifier : receivedOutputs) {
                this.addTransactionOutput(transactionOutputIdentifier);
            }
        }
    }

    public ByteArray getBytes() {
        if (_cachedBytes != null) {
            return _cachedBytes;
        }

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        final int addressByteCount = (_address != null ? _address.getByteCount() : 0);
        byteArrayBuilder.appendByte((byte) addressByteCount);
        if (_address != null) {
            byteArrayBuilder.appendBytes(_address);
        }

        final ByteArray outputCountBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(_outputsCount);
        byteArrayBuilder.appendBytes(outputCountBytes);

        byteArrayBuilder.appendBytes(_receivedOutputsData.build());
        _cachedBytes = MutableByteArray.wrap(byteArrayBuilder.build());
        return _cachedBytes;
    }

    public Integer getByteCount() {
        if (_cachedBytes != null) {
            return _cachedBytes.getByteCount();
        }

        final int addressByteCountSize = 1;
        final ByteArray outputCountBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(_outputsCount);
        return addressByteCountSize + _address.getByteCount() + outputCountBytes.getByteCount() + _receivedOutputsData.getByteCount();
    }
}
