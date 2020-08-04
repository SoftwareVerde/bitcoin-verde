package com.softwareverde.bitcoin.slp.validator;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

import java.util.Map;

public interface TransactionAccumulator {
    Map<Sha256Hash, Transaction> getTransactions(List<Sha256Hash> transactionHashes, final Boolean allowUnconfirmedTransactions);
}
