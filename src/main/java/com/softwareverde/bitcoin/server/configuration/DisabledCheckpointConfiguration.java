package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class DisabledCheckpointConfiguration extends CheckpointConfiguration {
    @Override
    public Boolean violatesCheckpoint(final Long blockHeight, final Sha256Hash blockHash) {
        return false;
    }
}
