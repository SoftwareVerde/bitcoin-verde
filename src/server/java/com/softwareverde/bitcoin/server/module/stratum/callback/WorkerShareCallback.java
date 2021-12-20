package com.softwareverde.bitcoin.server.module.stratum.callback;

public interface WorkerShareCallback {
    void onNewWorkerShare(String workerUsername, Long shareDifficulty);
}
