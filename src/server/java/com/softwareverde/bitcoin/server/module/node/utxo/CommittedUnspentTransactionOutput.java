package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface CommittedUnspentTransactionOutput extends UnspentTransactionOutput {
    static int compare(final CommittedUnspentTransactionOutput transactionOutput0, final CommittedUnspentTransactionOutput transactionOutput1) {
        final Sha256Hash transactionHash0 = transactionOutput0.getTransactionHash();
        final Sha256Hash transactionHash1 = transactionOutput1.getTransactionHash();
        final int hashCompare = transactionHash0.compareTo(transactionHash1);
        if (hashCompare != 0) {
            return hashCompare;
        }

        final Integer index0 = transactionOutput0.getIndex();
        final Integer index1 = transactionOutput1.getIndex();
        return index0.compareTo(index1);
    }

    static int compare(final TransactionOutputIdentifier outputIdentifier0, final CommittedUnspentTransactionOutput outputIdentifier1) {
        final Sha256Hash transactionHash0 = outputIdentifier0.getTransactionHash();
        final Sha256Hash transactionHash1 = outputIdentifier1.getTransactionHash();
        final int hashCompare = transactionHash0.compareTo(transactionHash1);
        if (hashCompare != 0) {
            return hashCompare;
        }

        final Integer index0 = outputIdentifier0.getOutputIndex();
        final Integer index1 = outputIdentifier1.getIndex();
        return index0.compareTo(index1);
    }

    static int compare(final CommittedUnspentTransactionOutput outputIdentifier0, final TransactionOutputIdentifier outputIdentifier1) {
        final Sha256Hash transactionHash0 = outputIdentifier0.getTransactionHash();
        final Sha256Hash transactionHash1 = outputIdentifier1.getTransactionHash();
        final int hashCompare = transactionHash0.compareTo(transactionHash1);
        if (hashCompare != 0) {
            return hashCompare;
        }

        final Integer index0 = outputIdentifier0.getIndex();
        final Integer index1 = outputIdentifier1.getOutputIndex();
        return index0.compareTo(index1);
    }

    // The original specification for the UTXO set was contradictory/broken, and instructs that the LSB bit is
    //  set when flagging the UTXO's isCoinbase flag: https://github.com/tomasvdw/bips/blob/master/ecmh-utxo-commitment-0.mediawiki#specification
    //  This is contradictory to allowing 31 bits for the blockHeight.  Unfortunately, other implementations followed
    //  the specification (which only allows for 24 bits for the blockHeight).
    Integer IS_COINBASE_FLAG_BIT_INDEX = 0; // (7 is "incorrect" but is what BCHD uses)

    Sha256Hash getTransactionHash();
    Integer getByteCount();

    ByteArray getBytes();

    default TransactionOutputIdentifier getTransactionOutputIdentifier() {
        final Sha256Hash transactionHash = this.getTransactionHash();
        final Integer outputIndex = this.getIndex();
        return new TransactionOutputIdentifier(transactionHash, outputIndex);
    }
}
