package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.security.hash.sha256.Sha256Hash;

public interface TransactionWhitelist {
    void addTransactionHash(Sha256Hash transactionHash);
}
