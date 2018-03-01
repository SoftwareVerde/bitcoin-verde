package com.softwareverde.bitcoin.miner;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
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
            if (hash.get(i) != zero) { return false; }
        }
        return true;
    }

    public void mineFakeBlock() throws Exception {
        final List<Thread> threads = new ArrayList<Thread>();

        final List<Container<Long>> hashCounts = new ArrayList<Container<Long>>();
        hashCounts.add(new Container<Long>(0L));
        hashCounts.add(new Container<Long>(0L));
        hashCounts.add(new Container<Long>(0L));

        for (int i=0; i<3; ++i) {
            final Integer index = i;
            final Thread thread = (new Thread(new Runnable() {
                @Override
                public void run() {
                    final BlockInflater blockInflater = new BlockInflater();

                    final Block previousBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000004860EB18BF1B1620E37E9490FC8A427514416FD75159AB86688E9A8300000000D5FDCC541E25DE1C7A5ADDEDF24858B8BB665C9F36EF744EE42C316022C90F9B6E6A985AFFFF001D38D34AD000"));
                    final MutableBlock mutableBlock = new MutableBlock(blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("01000000BDDD99CCFDA39DA1B108CE1A5D70038D0A967BACB68B6B63065F626A0000000044F672226090D85DB9A9F2FBFE5F0F9609B387AF7BE5B7FBB7A1767C831C9E995DBE6649FFFF001D05E0ED6D0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D010EFFFFFFFF0100F2052A0100000043410494B9D3E76C5B1629ECF97FFF95D7A4BBDAC87CC26099ADA28066C6FF1EB9191223CD897194A08D0C2726C5747F1DB49E8CF90E75DC3E3550AE9B30086F3CD5AAAC00000000")));

                    mutableBlock.setPreviousBlockHash(previousBlock.calculateSha256Hash());
                    mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);

                    final long startTime = System.currentTimeMillis();

                    long nonce = (long) (Math.random() * Long.MAX_VALUE);
                    while ( (! _isValidDifficulty(mutableBlock.calculateSha256Hash())) && (! hasBeenFound.value) ) {
                        nonce += 1;
                        mutableBlock.setNonce(nonce);

                        hashCounts.get(index).value += 1;

                        if (nonce % 7777 == 0) {
                            mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);
                        }

                        if (nonce % 7777777 == 0) {
                            long hashCount = hashCounts.get(0).value + hashCounts.get(1).value + hashCounts.get(2).value;

                            final long now = System.currentTimeMillis();
                            final long elapsed = (now - startTime);
                            final long hashesPerSecond = (hashCount / elapsed * 1000);
                            System.out.println(hashesPerSecond + " h/s");
                        }
                    }

                    System.out.println(BitcoinUtil.toHexString(mutableBlock.calculateSha256Hash()));
                    System.out.println(BitcoinUtil.toHexString(mutableBlock.getBytes()));
                    hasBeenFound.value = true;
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
