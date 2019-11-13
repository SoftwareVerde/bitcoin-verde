package com.softwareverde.bitcoin.miner;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;

public interface GpuSha256 {
    Integer getMaxBatchSize();
    List<Sha256Hash> sha256(List<? extends ByteArray> inputs);
}
