package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class ChipNetCheckpointConfiguration extends CheckpointConfiguration {
    public ChipNetCheckpointConfiguration() {
        _checkpoints.clear();

        _checkpoints.put(115252L, Sha256Hash.fromHexString("00000000040BA9641BA98A37B2E5CEEAD38E4E2930AC8F145C8094F94C708727"));
        _checkpoints.put(121957L, Sha256Hash.fromHexString("0000000056087DEE73FB66178CA70DA89DFD0BE098B1A63CF6FE93934CD04C78"));
    }
}
