package com.softwareverde.bitcoin.transaction.dsproof;

import com.softwareverde.bitcoin.block.merkleroot.Hashable;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimage;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimageDeflater;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class DoubleSpendProof implements Hashable, Const {
    protected final TransactionOutputIdentifier _transactionOutputIdentifier;
    protected final DoubleSpendProofPreimage _doubleSpendProofPreimage0;
    protected final DoubleSpendProofPreimage _doubleSpendProofPreimage1;

    protected ByteArray _cachedBytes = null;
    protected Sha256Hash _cachedHash = null;

    protected ByteArray _getBytes() {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        { // Serialize the previous output identifier...
            final Sha256Hash previousTransactionOutputHash = _transactionOutputIdentifier.getTransactionHash();
            final Integer previousTransactionOutputIndex = _transactionOutputIdentifier.getOutputIndex();
            byteArrayBuilder.appendBytes(previousTransactionOutputHash, Endian.LITTLE);
            byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(previousTransactionOutputIndex), Endian.LITTLE);
        }

        { // Serialize the double-spend-proofs...
            final DoubleSpendProofPreimageDeflater doubleSpendProofPreimageDeflater = new DoubleSpendProofPreimageDeflater();
            final ByteArray doubleSpendProofDigestBytes0 = doubleSpendProofPreimageDeflater.toBytes(_doubleSpendProofPreimage0);
            final ByteArray doubleSpendProofDigestBytes1 = doubleSpendProofPreimageDeflater.toBytes(_doubleSpendProofPreimage1);
            byteArrayBuilder.appendBytes(doubleSpendProofDigestBytes0, Endian.BIG);
            byteArrayBuilder.appendBytes(doubleSpendProofDigestBytes1, Endian.BIG);
        }

        return byteArrayBuilder;
    }

    public DoubleSpendProof(final TransactionOutputIdentifier transactionOutputIdentifier, final DoubleSpendProofPreimage doubleSpendProofPreimage0, final DoubleSpendProofPreimage doubleSpendProofPreimage1) {
        _transactionOutputIdentifier = transactionOutputIdentifier;
        _doubleSpendProofPreimage0 = doubleSpendProofPreimage0;
        _doubleSpendProofPreimage1 = doubleSpendProofPreimage1;
    }

    public TransactionOutputIdentifier getTransactionOutputIdentifierBeingDoubleSpent() {
        return _transactionOutputIdentifier;
    }

    public DoubleSpendProofPreimage getDoubleSpendProofPreimage0() {
        return _doubleSpendProofPreimage0;
    }

    public DoubleSpendProofPreimage getDoubleSpendProofPreimage1() {
        return _doubleSpendProofPreimage0;
    }

    public ByteArray getBytes() {
        if (_cachedBytes == null) {
            _cachedBytes = _getBytes();
        }

        return _cachedBytes;
    }

    @Override
    public Sha256Hash getHash() {
        if (_cachedHash == null) {
            if (_cachedBytes == null) {
                _cachedBytes = _getBytes();
            }

            _cachedHash = HashUtil.doubleSha256(_cachedBytes);
        }

        return _cachedHash;
    }
}
