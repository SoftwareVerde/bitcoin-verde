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

        for (int i=0; i<3; ++i) {
            final Thread thread = (new Thread(new Runnable() {
                @Override
                public void run() {
                    final BlockInflater blockInflater = new BlockInflater();

                    final Block previousBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D6190000000000982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0EFAA8975AFFFF001DB0E02D5E00"));
                    final MutableBlock mutableBlock = new MutableBlock(blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D6190000000000982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0EFAA8975AFFFF001DB0E02D5E00")));

                    mutableBlock.setPreviousBlockHash(previousBlock.calculateSha256Hash());
                    mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);

                    long nonce = (long) (Math.random() * Long.MAX_VALUE);
                    while ( (! _isValidDifficulty(mutableBlock.calculateSha256Hash())) && (! hasBeenFound.value) ) {
                        nonce += 1;
                        mutableBlock.setNonce(nonce);

                        if (nonce % 7777 == 0) {
                            mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);
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
