package com.softwareverde.bitcoin.stratum.callback;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface WorkerShareCallback {
    Boolean onNewWorkerShare(String workerUsername, Long shareDifficulty, Long blockHeight, Sha256Hash blockHash);
}
