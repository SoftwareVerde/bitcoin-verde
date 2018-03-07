package com.softwareverde.bitcoin.miner;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.type.bytearray.ByteArray;
import com.softwareverde.bitcoin.type.bytearray.ImmutableByteArray;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.jocl.GpuSha256;
import com.softwareverde.util.Container;

public class Miner {
    protected final Container<Boolean> hasBeenFound = new Container<Boolean>(false);

    protected static boolean _isValidDifficulty(final Hash hash) {
        final byte zero = 0x00;

        for (int i=0; i<4; ++i) {
            if (i == 3) { System.out.println(BitcoinUtil.toHexString(hash)); }
            if (hash.getByte(i) != zero) { return false; }
        }
        return true;
    }

    public void mineBlock(final Block previousBlock, final Block prototypeBlock) throws Exception {
        final MutableList<Thread> threads = new MutableList<Thread>();
        final MutableList<Container<Long>> hashCounts = new MutableList<Container<Long>>();

        final int hashesPerIteration = 1;

        final int THREAD_COUNT = 1;
        for (int i=0; i<THREAD_COUNT; ++i) {
            final Integer index = i;
            hashCounts.add(new Container<Long>(0L));

            final Thread thread = (new Thread(new Runnable() {
                @Override
                public void run() {
                    final GpuSha256 gpuSha256 = new GpuSha256();
                    final BlockDeflater blockDeflater = new BlockDeflater();
                    final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();

                    int lastHashesPerSecond = 2;
                    final MutableBlock mutableBlock = new MutableBlock(prototypeBlock);

                    mutableBlock.setPreviousBlockHash(previousBlock.getHash());
                    mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);

                    final long startTime = System.currentTimeMillis();

                    long nonce = (long) (Math.random() * Long.MAX_VALUE);

                    boolean isValidDifficulty = false;
                    while ( (! isValidDifficulty) && (! hasBeenFound.value) ) {

                        final MutableList<ByteArray> blockHeaderBytesList = new MutableList<ByteArray>();
                        final MutableList<ByteArray> blockBytesList = new MutableList<ByteArray>(); // TODO: Instead of storing the bytes, just store the nonce and the timestamp.

                        for (int i=0; i<hashesPerIteration; ++i) {
                            nonce += 1;
                            mutableBlock.setNonce(nonce);

                            hashCounts.get(index).value += 1;

                            if (nonce % 7777 == 0) {
                                mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);
                            }

                            if (nonce % (lastHashesPerSecond * 10) == 0) {
                                long hashCount = 0;
                                for (int j = 0; j < THREAD_COUNT; ++j) {
                                    hashCount += hashCounts.get(j).value;
                                }

                                final long now = System.currentTimeMillis();
                                final long elapsed = (now - startTime);
                                final double hashesPerSecond = (((double) hashCount) / elapsed * 1000D);
                                System.out.println(String.format("%.2f h/s", hashesPerSecond));
                                lastHashesPerSecond = (int) hashesPerSecond;
                            }

                            blockHeaderBytesList.add(new ImmutableByteArray(blockHeaderDeflater.toBytes(mutableBlock)));
                            blockBytesList.add(new ImmutableByteArray(blockDeflater.toBytes(mutableBlock)));
                        }

                        final List<Hash> blockHashes = gpuSha256.sha256(gpuSha256.sha256(blockHeaderBytesList));
                        for (int i=0; i<hashesPerIteration; ++i) {
                            final Hash blockHash = blockHashes.get(i);
                            isValidDifficulty = _isValidDifficulty(blockHash.toReversedEndian());;

                            if (isValidDifficulty) {
                                final ByteArray blockBytes = blockBytesList.get(i);
                                System.out.println(BitcoinUtil.toHexString(blockHash.toReversedEndian()));
                                System.out.println(BitcoinUtil.toHexString(blockBytes));
                                hasBeenFound.value = true;
                            }
                        }
                    }
                }
            }));
            threads.add(thread);
        }

        for (int i=0; i<THREAD_COUNT; ++i) {
            threads.get(i).start();
        }

        for (final Thread thread : threads) {
            thread.join();
        }
    }
}
