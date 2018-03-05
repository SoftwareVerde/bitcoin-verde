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

    public void mineBlock(final Block previousBlock, final Block blockToReplace) throws Exception {
        final List<Thread> threads = new ArrayList<Thread>();

        /*
            final List<Container<Long>> hashCounts = new ArrayList<Container<Long>>();
            hashCounts.add(new Container<Long>(0L));
            hashCounts.add(new Container<Long>(0L));
            hashCounts.add(new Container<Long>(0L));
        */

        for (int i=0; i<3; ++i) {
            // final Integer index = i;

            final Thread thread = (new Thread(new Runnable() {
                @Override
                public void run() {
                    final MutableBlock mutableBlock = new MutableBlock(blockToReplace);

                    mutableBlock.setPreviousBlockHash(previousBlock.getHash());
                    mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);

                    // final long startTime = System.currentTimeMillis();

                    long nonce = (long) (Math.random() * Long.MAX_VALUE);

                    boolean isValidDifficulty = _isValidDifficulty(mutableBlock.getHash());
                    while ( (! isValidDifficulty) && (! hasBeenFound.value) ) {
                        nonce += 1;
                        mutableBlock.setNonce(nonce);

                        // hashCounts.get(index).value += 1;

                        if (nonce % 7777 == 0) {
                            mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);
                        }

                        /*
                            if (nonce % 7777777 == 0) {
                                long hashCount = hashCounts.get(0).value + hashCounts.get(1).value + hashCounts.get(2).value;

                                final long now = System.currentTimeMillis();
                                final long elapsed = (now - startTime);
                                final long hashesPerSecond = (hashCount / elapsed * 1000);
                                System.out.println(hashesPerSecond + " h/s");
                            }
                        */

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
            thread.start();
        }

        for (final Thread thread : threads) {
            thread.join();
        }
    }
}
