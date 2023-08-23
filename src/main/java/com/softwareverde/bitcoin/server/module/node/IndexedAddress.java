package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputDeflater;
import com.softwareverde.bitcoin.transaction.input.TransactionInputInflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputDeflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputInflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.constable.set.Set;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class IndexedAddress {
    public static IndexedAddress fromBytes(final ByteArray byteArray) {
        final AddressInflater addressInflater = new AddressInflater();
        final TransactionOutputInflater transactionOutputInflater = new TransactionOutputInflater();
        final TransactionInputInflater transactionInputInflater = new TransactionInputInflater();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(byteArray);

        final int addressByteCount = byteArrayReader.readInteger(1); // Address Byte Count, 1 byte
        final Address address = addressInflater.fromBytes(MutableByteArray.wrap(byteArrayReader.readBytes(addressByteCount))); // Address

        final MutableHashMap<Sha256Hash, MutableList<TransactionOutput>> outputsMap = new MutableHashMap<>();
        {
            final int transactionCount = byteArrayReader.readInteger(4); // Transaction Count, 4 bytes
            for (int i = 0; i < transactionCount; ++i) {
                final Sha256Hash transactionHash = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT)); // Transaction Hash, 32 bytes
                final CompactVariableLengthInteger compactOutputsCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader); // Outputs Count, variable
                final int outputsCount = compactOutputsCount.intValue();
                final MutableArrayList<TransactionOutput> transactionOutputs = new MutableArrayList<>(outputsCount);
                for (int j = 0; j < outputsCount; ++j) {
                    final CompactVariableLengthInteger compactVariableLengthInteger = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader); // Output Index, variable
                    final Integer outputIndex = compactVariableLengthInteger.intValue();
                    final TransactionOutput transactionOutput = transactionOutputInflater.fromBytes(outputIndex, byteArrayReader); // Output, variable
                    transactionOutputs.add(transactionOutput);
                }
                outputsMap.put(transactionHash, transactionOutputs);
            }
        }

        final MutableHashMap<Sha256Hash, MutableList<TransactionInput>> inputsMap = new MutableHashMap<>();
        {
            final int transactionCount = byteArrayReader.readInteger(4); // Transaction Count, 4 bytes
            for (int i = 0; i < transactionCount; ++i) {
                final Sha256Hash transactionHash = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT)); // Transaction Hash, 32 bytes
                final CompactVariableLengthInteger compactInputsCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader); // Inputs Count, variable
                final int inputsCount = compactInputsCount.intValue();
                final MutableArrayList<TransactionInput> transactionInputs = new MutableArrayList<>(inputsCount);
                for (int j = 0; j < inputsCount; ++j) {
                    final TransactionInput transactionInput = transactionInputInflater.fromBytes(byteArrayReader); // Input, variable
                    transactionInputs.add(transactionInput);
                }
                inputsMap.put(transactionHash, transactionInputs);
            }
        }

        return new IndexedAddress(address, outputsMap, inputsMap);
    }

    protected final Address _address;
    protected final MutableMap<Sha256Hash, MutableList<TransactionOutput>> _receivedOutputs;
    protected final MutableMap<Sha256Hash, MutableList<TransactionInput>> _sentInputs;
    protected ByteArray _cachedBytes;

    protected void _cacheBytes() {
        final TransactionOutputDeflater transactionOutputDeflater = new TransactionOutputDeflater();
        final TransactionInputDeflater transactionInputDeflater = new TransactionInputDeflater();
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        final int addressByteCount = (_address != null ? _address.getByteCount() : 0);
        byteArrayBuilder.appendByte((byte) addressByteCount);
        if (_address != null) {
            byteArrayBuilder.appendBytes(_address);
        }

        { // Transaction Outputs
            final Set<Sha256Hash> receivedTransactionHashes = _receivedOutputs.getKeys();
            final int receivedTransactionCount = receivedTransactionHashes.getCount();
            byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(receivedTransactionCount));
            for (final Sha256Hash transactionHash : receivedTransactionHashes) {
                byteArrayBuilder.appendBytes(transactionHash);

                final List<TransactionOutput> transactionOutputs = _receivedOutputs.get(transactionHash);
                final int outputCount = transactionOutputs.getCount();
                final ByteArray outputCountBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(outputCount);
                byteArrayBuilder.appendBytes(outputCountBytes);

                for (final TransactionOutput transactionOutput : transactionOutputs) {
                    final int outputIndex = transactionOutput.getIndex();
                    final ByteArray outputIndexBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(outputIndex);
                    byteArrayBuilder.appendBytes(outputIndexBytes);

                    final ByteArray transactionOutputBytes = transactionOutputDeflater.toBytes(transactionOutput);
                    byteArrayBuilder.appendBytes(transactionOutputBytes);
                }
            }
        }

        { // Transaction Inputs
            final Set<Sha256Hash> sentTransactionHashes = _sentInputs.getKeys();
            final int sentTransactionCount = sentTransactionHashes.getCount();
            byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(sentTransactionCount));
            for (final Sha256Hash transactionHash : sentTransactionHashes) {
                byteArrayBuilder.appendBytes(transactionHash);

                final List<TransactionInput> transactionInputs = _sentInputs.get(transactionHash);
                final int inputCount = transactionInputs.getCount();
                final ByteArray inputCountBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(inputCount);
                byteArrayBuilder.appendBytes(inputCountBytes);

                for (final TransactionInput transactionInput : transactionInputs) {
                    final ByteArray transactionInputBytes = transactionInputDeflater.toBytes(transactionInput);
                    byteArrayBuilder.appendBytes(transactionInputBytes);
                }
            }
        }

        _cachedBytes = byteArrayBuilder;
    }

    public IndexedAddress(final Address address) {
        _address = address;
        _receivedOutputs = new MutableHashMap<>();
        _sentInputs = new MutableHashMap<>();
    }

    public IndexedAddress(final Address address, final MutableMap<Sha256Hash, MutableList<TransactionOutput>> receivedOutputs, final MutableMap<Sha256Hash, MutableList<TransactionInput>> sentInputs) {
        _address = address;
        _receivedOutputs = receivedOutputs;
        _sentInputs = sentInputs;
    }

    public Address getAddress() {
        return _address;
    }

    public Set<Sha256Hash> getReceivedTransactions() {
        return _receivedOutputs.getKeys();
    }
    
    public Set<Sha256Hash> getSentTransactions() {
        return _sentInputs.getKeys();
    }

    public List<TransactionOutput> getTransactionOutputs(final Sha256Hash transactionHash) {
        return _receivedOutputs.get(transactionHash);
    }

    public List<TransactionInput> getTransactionInputs(final Sha256Hash transactionHash) {
        return _sentInputs.get(transactionHash);
    }

    public void addTransactionOutput(final Sha256Hash transactionHash, final TransactionOutput transactionOutput) {
        _cachedBytes = null;
        MutableList<TransactionOutput> transactionOutputs = _receivedOutputs.get(transactionHash);
        if (transactionOutputs == null) {
            transactionOutputs = new MutableArrayList<>();
            _receivedOutputs.put(transactionHash, transactionOutputs);
        }

        transactionOutputs.add(transactionOutput);
    }

    public void addTransactionInput(final Sha256Hash transactionHash, final TransactionInput transactionInput) {
        _cachedBytes = null;
        MutableList<TransactionInput> transactionInputs = _sentInputs.get(transactionHash);
        if (transactionInputs == null) {
            transactionInputs = new MutableArrayList<>();
            _sentInputs.put(transactionHash, transactionInputs);
        }

        transactionInputs.add(transactionInput);
    }

    public void add(final IndexedAddress indexedAddress) {
        _cachedBytes = null;
        _receivedOutputs.putAll(indexedAddress._receivedOutputs);
        _sentInputs.putAll(indexedAddress._sentInputs);
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
