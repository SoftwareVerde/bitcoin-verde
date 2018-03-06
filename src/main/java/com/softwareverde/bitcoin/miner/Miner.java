package com.softwareverde.bitcoin.miner;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.util.Container;

import java.util.ArrayList;
import java.util.List;

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
        final List<Thread> threads = new ArrayList<Thread>();

        final List<Container<Long>> hashCounts = new ArrayList<Container<Long>>();

        final int THREAD_COUNT = 5;
        for (int i=0; i<THREAD_COUNT; ++i) {
            final Integer index = i;
            hashCounts.add(new Container<Long>(0L));

            final Thread thread = (new Thread(new Runnable() {
                @Override
                public void run() {
                    int lastHashesPerSecond = 2;
                    final MutableBlock mutableBlock = new MutableBlock(prototypeBlock);

                    mutableBlock.setPreviousBlockHash(previousBlock.getHash());
                    mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);

                    final long startTime = System.currentTimeMillis();

                    long nonce = (long) (Math.random() * Long.MAX_VALUE);

                    boolean isValidDifficulty = _isValidDifficulty(mutableBlock.getHash());
                    while ( (! isValidDifficulty) && (! hasBeenFound.value) ) {
                        nonce += 1;
                        mutableBlock.setNonce(nonce);

                        hashCounts.get(index).value += 1;

                        if (nonce % 7777 == 0) {
                            mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);
                        }

                        if (nonce % (lastHashesPerSecond * 10) == 0) {
                            long hashCount = 0;
                            for (int i=0; i<THREAD_COUNT; ++i) {
                                hashCount += hashCounts.get(i).value;
                            }

                            final long now = System.currentTimeMillis();
                            final long elapsed = (now - startTime);
                            final double hashesPerSecond = (((double) hashCount) / elapsed * 1000D);
                            System.out.println(String.format("%.2f h/s", hashesPerSecond));
                            lastHashesPerSecond = (int) hashesPerSecond;
                        }

                        isValidDifficulty = _isValidDifficulty(mutableBlock.getHash());
                    }

                    if (isValidDifficulty) {
                        final BlockDeflater blockDeflater = new BlockDeflater();
                        System.out.println(BitcoinUtil.toHexString(mutableBlock.getHash()));
                        System.out.println(BitcoinUtil.toHexString(blockDeflater.toBytes(mutableBlock)));
                        hasBeenFound.value = true;
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
