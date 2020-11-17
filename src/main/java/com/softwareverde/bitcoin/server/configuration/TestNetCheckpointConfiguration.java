package com.softwareverde.bitcoin.server.configuration;

public class TestNetCheckpointConfiguration extends CheckpointConfiguration {
    public TestNetCheckpointConfiguration() {
        _checkpoints.clear(); // Disable checkpoints on TestNet.
    }
}
