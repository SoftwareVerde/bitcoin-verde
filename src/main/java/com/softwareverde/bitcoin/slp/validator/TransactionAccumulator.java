package com.softwareverde.bitcoin.slp.validator;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.map.Map;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface TransactionAccumulator {
    Map<Sha256Hash, Transaction> getTransactions(List<Sha256Hash> transactionHashes, final Boolean allowUnconfirmedTransactions);
}
