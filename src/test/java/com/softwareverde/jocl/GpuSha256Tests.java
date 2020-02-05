package com.softwareverde.jocl;

import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.miner.GpuSha256;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.security.util.HashUtil;
import org.junit.Assert;
import org.junit.Test;

public class GpuSha256Tests {
    @Test
    public void should_hash_batch_correctly() {
        // Setup
        final int totalHashCount = 1024 * 512;
        final int batchSize = JoclGpuSha256.maxBatchSize;

        final GpuSha256 gpuSha256 = JoclGpuSha256.getInstance();

        final long cpuStartTime = System.currentTimeMillis();
        final List<Sha256Hash> expectedValues;
        {
            final MutableByteArray mutableByteArray = new MutableByteArray(32);
            final ImmutableListBuilder<Sha256Hash> immutableListBuilder = new ImmutableListBuilder<Sha256Hash>(totalHashCount);
            for (int i = 0; i < totalHashCount; ++i) {
                for (int j = 0; j < mutableByteArray.getByteCount(); ++j) {
                    mutableByteArray.setByte(j, (byte) i);
                }

                final Sha256Hash hash = HashUtil.doubleSha256(mutableByteArray);
                immutableListBuilder.add(hash);
            }
            expectedValues = immutableListBuilder.build();
        }
        final long cpuEndTime = System.currentTimeMillis();

        // Action
        final long gpuStartTime = System.currentTimeMillis();
        final List<Sha256Hash> values;
        {
            final ImmutableListBuilder<Sha256Hash> immutableListBuilder = new ImmutableListBuilder<Sha256Hash>(totalHashCount);

            final ImmutableListBuilder<ByteArray> hashBatchBuilder = new ImmutableListBuilder<ByteArray>(batchSize);
            for (int hashCount=0; hashCount<totalHashCount;) {
                for (int i = 0; i < Math.min(batchSize, (totalHashCount - hashCount)); ++i) {
                    final MutableByteArray mutableByteArray = new MutableByteArray(32);
                    for (int j = 0; j < mutableByteArray.getByteCount(); ++j) {
                        mutableByteArray.setByte(j, (byte) (hashCount + i));
                    }
                    hashBatchBuilder.add(mutableByteArray);
                }

                hashCount += hashBatchBuilder.getCount();

                final List<Sha256Hash> hashes = gpuSha256.sha256(gpuSha256.sha256(hashBatchBuilder.build()));
                // final List<Hash> hashes = gpuSha256.sha256(hashBatchBuilder.build());
                immutableListBuilder.addAll(hashes);
            }

            values = immutableListBuilder.build();
        }
        final long gpuEndTime = System.currentTimeMillis();

        // Assert
        final long cpuElapsed = (cpuEndTime - cpuStartTime);
        final long gpuElapsed = (gpuEndTime - gpuStartTime);

        System.out.println("CPU Elapsed: "+ cpuElapsed + " (" + (((float) totalHashCount * 2F) / cpuElapsed * 1000) + " h/s)");
        System.out.println("GPU Elapsed: "+ gpuElapsed + " (" + (((float) totalHashCount * 2F) / gpuElapsed * 1000) + " h/s)");
        System.out.println("Ratio: "+ ( ((float) cpuElapsed) / gpuElapsed ));

        Assert.assertEquals(expectedValues.getCount(), values.getCount());

        for (int i = 0; i < values.getCount(); ++i) {
            final Sha256Hash expectedHash = expectedValues.get(i);
            final Sha256Hash hash = values.get(i);

            final boolean areEqual = expectedHash.equals(hash);
            if (! areEqual) {
                System.out.println("Not equal at index: "+ i);
            }
            Assert.assertEquals(expectedHash, hash);
        }
    }
}
