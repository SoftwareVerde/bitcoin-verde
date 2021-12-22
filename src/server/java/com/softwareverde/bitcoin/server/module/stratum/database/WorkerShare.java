package com.softwareverde.bitcoin.server.module.stratum.database;

import com.softwareverde.bitcoin.miner.pool.WorkerId;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

public class WorkerShare {
    protected static final SystemTime SYSTEM_TIME = new SystemTime();

    final WorkerId workerId;
    final Long shareDifficulty;
    final Sha256Hash blockHash;
    final Long timestamp;

    public WorkerShare(final WorkerId workerId, final Long shareDifficulty, final Sha256Hash blockHash) {
        this.workerId = workerId;
        this.shareDifficulty = shareDifficulty;
        this.blockHash = blockHash;

        this.timestamp = SYSTEM_TIME.getCurrentTimeInSeconds();
    }

    public boolean isExactlyEqual(final Object object) {
        if (this == object) { return true; }
        if (object == null) { return false; }
        if (! (object instanceof WorkerShare)) { return false; }

        final WorkerShare workerShare = (WorkerShare) object;
        if (! Util.areEqual(this.workerId, workerShare.workerId)) { return false; }
        if (! Util.areEqual(this.shareDifficulty, workerShare.shareDifficulty)) { return false; }
        if (! Util.areEqual(this.blockHash, workerShare.blockHash)) { return false; }
        return true;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) { return true; }
        if (object == null) { return false; }
        if (! (object instanceof WorkerShare)) { return false; }

        final WorkerShare workerShare = (WorkerShare) object;
        return Util.areEqual(this.blockHash, workerShare.blockHash);
    }

    @Override
    public int hashCode() {
        if (this.blockHash == null) { return 0; }
        return this.blockHash.hashCode();
    }
}