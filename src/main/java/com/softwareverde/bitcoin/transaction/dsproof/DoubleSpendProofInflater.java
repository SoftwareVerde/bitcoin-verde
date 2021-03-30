package com.softwareverde.bitcoin.transaction.dsproof;

import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofPreimageInflater;
import com.softwareverde.bitcoin.server.message.type.dsproof.MutableDoubleSpendProofPreimage;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.Endian;

public class DoubleSpendProofInflater {
    public DoubleSpendProof fromBytes(final ByteArrayReader byteArrayReader) {
        final Sha256Hash previousOutputTransactionHash = Sha256Hash.wrap(byteArrayReader.readBytes(Sha256Hash.BYTE_COUNT, Endian.LITTLE));
        final Integer previousOutputIndex = byteArrayReader.readInteger(4, Endian.LITTLE);
        final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(previousOutputTransactionHash, previousOutputIndex);

        final DoubleSpendProofPreimageInflater doubleSpendProofPreimageInflater = new DoubleSpendProofPreimageInflater();
        final MutableDoubleSpendProofPreimage doubleSpendProofPreimage0 = doubleSpendProofPreimageInflater.fromBytes(byteArrayReader);
        final MutableDoubleSpendProofPreimage doubleSpendProofPreimage1 = doubleSpendProofPreimageInflater.fromBytes(byteArrayReader);

        // Parse the DoubleSpendProofPreimage extra data...
        doubleSpendProofPreimageInflater.parseExtraTransactionOutputsDigests(byteArrayReader, doubleSpendProofPreimage0);
        doubleSpendProofPreimageInflater.parseExtraTransactionOutputsDigests(byteArrayReader, doubleSpendProofPreimage1);

        if (byteArrayReader.didOverflow()) { return null; }

        return new DoubleSpendProof(transactionOutputIdentifier, doubleSpendProofPreimage0, doubleSpendProofPreimage1);
    }

    public DoubleSpendProof fromBytes(final ByteArray byteArray) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(byteArray);
        return this.fromBytes(byteArrayReader);
    }
}
