package com.softwareverde.bitcoin.server.module.stratum.callback;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface WorkerShareCallback {
    void onNewWorkerShare(String workerUsername, Long shareDifficulty, Sha256Hash blockHash);
}
