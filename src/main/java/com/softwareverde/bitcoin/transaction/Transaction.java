package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.stack.ScriptSignature;
import com.softwareverde.bitcoin.type.hash.Hash;

import java.util.List;

public interface Transaction {
    static final Integer SATOSHIS_PER_BITCOIN = 100_000_000;

    Hash calculateSha256Hash();
    Hash calculateSha256HashForSigning(Integer inputIndexToBeSigned, TransactionOutput transactionOutputBeingSpent, ScriptSignature.HashType hashType);
    byte[] getBytesForSigning(Integer inputIndexToBeSigned, TransactionOutput transactionOutputBeingSpent, ScriptSignature.HashType hashType);

    Integer getVersion();
    Boolean hasWitnessData();
    List<TransactionInput> getTransactionInputs();
    List<TransactionOutput> getTransactionOutputs();
    LockTime getLockTime();
    Long getTotalOutputValue();
    Integer getByteCount();
    byte[] getBytes();
}
