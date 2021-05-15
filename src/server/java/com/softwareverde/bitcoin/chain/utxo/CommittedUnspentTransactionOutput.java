package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface CommittedUnspentTransactionOutput extends UnspentTransactionOutput {
    Boolean isCoinbaseTransaction();
    Sha256Hash getTransactionHash();
    Integer getByteCount();

    ByteArray getBytes();
}
