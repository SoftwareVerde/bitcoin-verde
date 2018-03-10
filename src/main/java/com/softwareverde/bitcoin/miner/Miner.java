package com.softwareverde.bitcoin.miner;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockHasher;
import com.softwareverde.bitcoin.block.ImmutableBlock;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.type.bytearray.ByteArray;
import com.softwareverde.bitcoin.type.bytearray.MutableByteArray;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.jocl.GpuSha256;
import com.softwareverde.util.Container;

public class Miner {
    protected final Container<Boolean> hasBeenFound = new Container<Boolean>(false);

    protected static boolean _isValidDifficulty(final Hash hash) {
        final byte zero = 0x00;

        for (int i=0; i<4; ++i) {
            if (i == 3) { Logger.log(BitcoinUtil.toHexString(hash)); }
            if (hash.getByte(i) != zero) { return false; }
        }
        return true;
    }

    protected final Integer _cpuThreadCount;
    protected final Integer _gpuThreadCount;

    public Miner(final Integer cpuThreadCount, final Integer gpuThreadCount) {
        _cpuThreadCount = cpuThreadCount;
        _gpuThreadCount = gpuThreadCount;
    }

    public Block mineBlock(final Block prototypeBlock) throws Exception {
        final MutableList<Thread> threads = new MutableList<Thread>();
        final MutableList<Container<Long>> hashCounts = new MutableList<Container<Long>>();

        final Container<Block> blockContainer = new Container<Block>();

        final Runnable hashCountPrinter = new Runnable() {
            @Override
            public synchronized void run() {
                final long startTime = System.currentTimeMillis();

                while (! hasBeenFound.value) {
                    try { Thread.sleep(5000); } catch (final Exception e) { }

                    long hashCount = 0;
                    for (int j = 0; j < (_cpuThreadCount + _gpuThreadCount); ++j) {
                        hashCount += hashCounts.get(j).value;
                    }

                    final long now = System.currentTimeMillis();
                    final long elapsed = (now - startTime) + 1;
                    final double hashesPerSecond = (((double) hashCount) / elapsed * 1000D);
                    Logger.log(String.format("%.2f h/s", hashesPerSecond));
                }
            }
        };
        threads.add(new Thread(hashCountPrinter));

        int threadIndex = 0;

        final int hashesPerIteration = GpuSha256.maxBatchSize;
        for (int i=0; i<_gpuThreadCount; ++i) {
            final Integer index = (threadIndex++);
            hashCounts.add(new Container<Long>(0L));

            final Thread thread = (new Thread(new Runnable() {
                @Override
                public void run() {
                    final GpuSha256 gpuSha256 = GpuSha256.getInstance();
                    final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
                    final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();

                    final MutableBlock mutableBlock = new MutableBlock(prototypeBlock);

                    mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);

                    long nonce = (long) (Math.random() * Long.MAX_VALUE);

                    boolean isValidDifficulty = false;
                    while ( (! isValidDifficulty) && (! hasBeenFound.value) ) {

                        final MutableList<ByteArray> blockHeaderBytesList = new MutableList<ByteArray>();
                        for (int i=0; i<hashesPerIteration; ++i) {
                            nonce += 1;
                            mutableBlock.setNonce(nonce);

                            if (nonce % 7777 == 0) {
                                mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);
                            }

                            blockHeaderBytesList.add(new MutableByteArray(blockHeaderDeflater.toBytes(mutableBlock)));
                        }

                        final List<Hash> blockHashes = gpuSha256.sha256(gpuSha256.sha256(blockHeaderBytesList));

                        for (int i=0; i<hashesPerIteration; ++i) {
                            final Hash blockHash = blockHashes.get(i);
                            isValidDifficulty = _isValidDifficulty(blockHash.toReversedEndian());;

                            if (isValidDifficulty) {
                                hasBeenFound.value = true;

                                final BlockHeader blockHeader = blockHeaderInflater.fromBytes(blockHeaderBytesList.get(i));
                                final Block block = new ImmutableBlock(blockHeader, mutableBlock.getTransactions());
                                blockContainer.value = block;
                            }
                        }

                        hashCounts.get(index).value += hashesPerIteration;
                    }
                }
            }));
            threads.add(thread);
        }

        for (int i=0; i<_cpuThreadCount; ++i) {
            final Integer index = (threadIndex++);
            hashCounts.add(new Container<Long>(0L));

            final Thread thread = (new Thread(new Runnable() {
                @Override
                public void run() {
                    final BlockHasher blockHasher = new BlockHasher();

                    final MutableBlock mutableBlock = new MutableBlock(prototypeBlock);

                    mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);

                    long nonce = (long) (Math.random() * Long.MAX_VALUE);

                    boolean isValidDifficulty = false;
                    while ( (! isValidDifficulty) && (! hasBeenFound.value) ) {
                        nonce += 1;
                        mutableBlock.setNonce(nonce);

                        if (nonce % 7777 == 0) {
                            mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);
                        }

                        final Hash blockHash = blockHasher.calculateBlockHash(mutableBlock);
                        isValidDifficulty = _isValidDifficulty(blockHash);

                        if (isValidDifficulty) {
                            hasBeenFound.value = true;
                            blockContainer.value = mutableBlock;
                        }

                        hashCounts.get(index).value += 1;
                    }
                }
            }));
            threads.add(thread);
        }

        for (final Thread thread : threads) {
            thread.start();
        }

        for (final Thread thread : threads) {
            thread.join();
        }

        return blockContainer.value;
    }
}
