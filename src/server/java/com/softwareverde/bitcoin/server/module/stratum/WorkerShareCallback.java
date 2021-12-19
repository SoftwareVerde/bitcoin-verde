package com.softwareverde.bitcoin.server.module.stratum;

public interface WorkerShareCallback {
    void onNewWorkerShare(String workerUsername, Long shareDifficulty);
}
