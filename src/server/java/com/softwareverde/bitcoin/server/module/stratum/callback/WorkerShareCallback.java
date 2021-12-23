package com.softwareverde.bitcoin.server.module.stratum.callback;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface WorkerShareCallback {
    Boolean onNewWorkerShare(String workerUsername, Long shareDifficulty, Sha256Hash blockHash);
}
