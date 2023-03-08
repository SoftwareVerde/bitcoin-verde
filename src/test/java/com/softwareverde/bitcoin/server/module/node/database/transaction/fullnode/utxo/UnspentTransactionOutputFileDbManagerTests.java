package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.BlockUtxoDiff;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.transaction.output.MutableUnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.BlockUtil;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableTreeMap;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.filedb.FilterFile;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UnspentTransactionOutputFileDbManagerTests extends UnitTest {
    @Before @Override
    public void before() throws Exception {
        super.before();
    }

    @After @Override
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void foo() throws Exception {
        final BlockInflater blockInflater = new BlockInflater();
        // final Block block = blockInflater.fromBytes(ByteArray.wrap(IoUtil.readStream(IoUtil.getResourceAsStream("/blocks/0000000000000000096B19CAAF66F7D94CA681BEA3CF4FED040926C3204B4A12"))));
        final Block block = blockInflater.fromBytes(IoUtil.readCompressed(IoUtil.getResourceAsStream("/blocks/00000000000000000991EB1B73E717CB20D93E9CA3856E8FEB3DD9618C6ED6BD")));

        final NanoTimer nanoTimer = new NanoTimer();

        nanoTimer.start();
        final BlockUtxoDiff blockUtxoDiff = BlockUtil.getBlockUtxoDiff(block);
        nanoTimer.stop();
        System.out.println("blockDiff: " + nanoTimer.getMillisecondsElapsed() + "ms.");

        final int itemCount = (blockUtxoDiff.unspentTransactionOutputIdentifiers.getCount() + blockUtxoDiff.spentTransactionOutputIdentifiers.getCount());
        System.out.println("itemCount=" + itemCount);

        {
            nanoTimer.start();

            final MutableList<TransactionOutputIdentifier> list = new MutableArrayList<>(itemCount);
            final MutableHashMap<TransactionOutputIdentifier, Boolean> hashMap = new MutableHashMap<>(itemCount);

            for (final TransactionOutputIdentifier transactionOutputIdentifier : blockUtxoDiff.unspentTransactionOutputIdentifiers) {
                hashMap.put(transactionOutputIdentifier, true);
                list.add(transactionOutputIdentifier);
            }
            for (final TransactionOutputIdentifier transactionOutputIdentifier : blockUtxoDiff.spentTransactionOutputIdentifiers) {
                hashMap.put(transactionOutputIdentifier, true);
                list.add(transactionOutputIdentifier);
            }
            list.sort(TransactionOutputIdentifier.COMPARATOR);

            nanoTimer.stop();
            System.out.println("hashMap: " + nanoTimer.getMillisecondsElapsed() + "ms");
        }

        {
            nanoTimer.start();

            final MutableTreeMap<TransactionOutputIdentifier, Boolean> treeMap = new MutableTreeMap<>(TransactionOutputIdentifier.COMPARATOR);
            for (final TransactionOutputIdentifier transactionOutputIdentifier : blockUtxoDiff.unspentTransactionOutputIdentifiers) {
                treeMap.put(transactionOutputIdentifier, true);
            }
            for (final TransactionOutputIdentifier transactionOutputIdentifier : blockUtxoDiff.spentTransactionOutputIdentifiers) {
                treeMap.put(transactionOutputIdentifier, true);
            }

            nanoTimer.stop();
            System.out.println("treeMap: " + nanoTimer.getMillisecondsElapsed() + "ms");
        }

        {
            final UnspentTransactionOutputEntryInflater unspentTransactionOutputEntryInflater = new UnspentTransactionOutputEntryInflater();

            nanoTimer.start();

            final int byteCount = MutableBloomFilter.calculateByteCount((long) itemCount, 0.000001D);
            final int functionCount = MutableBloomFilter.calculateFunctionCount(byteCount, (long) itemCount);
            final MutableBloomFilter bloomFilter = FilterFile.createBloomFilter(byteCount, functionCount);

            for (final TransactionOutputIdentifier transactionOutputIdentifier : blockUtxoDiff.unspentTransactionOutputIdentifiers) {
                // unspentTransactionOutputEntryInflater.keyToBytes(transactionOutputIdentifier);
                bloomFilter.addItem(unspentTransactionOutputEntryInflater.keyToBytes(transactionOutputIdentifier));
            }
            for (final TransactionOutputIdentifier transactionOutputIdentifier : blockUtxoDiff.spentTransactionOutputIdentifiers) {
                // bloomFilter.addItem(unspentTransactionOutputEntryInflater.keyToBytes(transactionOutputIdentifier));
                unspentTransactionOutputEntryInflater.keyToBytes(transactionOutputIdentifier);
            }

            nanoTimer.stop();
            System.out.println("bloomFilter: " + nanoTimer.getMillisecondsElapsed() + "ms");
        }

        {
            final UnspentTransactionOutputEntryInflater unspentTransactionOutputEntryInflater = new UnspentTransactionOutputEntryInflater();

            nanoTimer.start();

            final MutableTreeMap<TransactionOutputIdentifier, UnspentTransactionOutput> treeMap = new MutableTreeMap<>(TransactionOutputIdentifier.COMPARATOR);

            final Sha256Hash coinbaseTransactionHash = blockUtxoDiff.coinbaseTransactionHash;
            final List<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers = blockUtxoDiff.unspentTransactionOutputIdentifiers;
            final List<TransactionOutput> transactionOutputs = blockUtxoDiff.unspentTransactionOutputs;
            final List<TransactionOutputIdentifier> spentTransactionOutputIdentifiers = blockUtxoDiff.spentTransactionOutputIdentifiers;

            final int outputCount = unspentTransactionOutputIdentifiers.getCount();
            final int spentOutputCount = spentTransactionOutputIdentifiers.getCount();

            final int byteCount = MutableBloomFilter.calculateByteCount((long) itemCount, 0.000001D);
            final int functionCount = MutableBloomFilter.calculateFunctionCount(byteCount, (long) itemCount);
            final MutableBloomFilter bloomFilter = FilterFile.createBloomFilter(byteCount, functionCount);

            final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
            final ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();

            for (int i = 0; i < outputCount; ++i) {
                writeLock.lock();
                try {
                    final TransactionOutputIdentifier transactionOutputIdentifier = unspentTransactionOutputIdentifiers.get(i);
                    final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                    final Boolean isCoinbase = Util.areEqual(coinbaseTransactionHash, transactionHash);

                    final TransactionOutput transactionOutput = transactionOutputs.get(i);
                    final UnspentTransactionOutput unspentTransactionOutput = new MutableUnspentTransactionOutput(transactionOutput, 377676L, isCoinbase);

                    treeMap.put(transactionOutputIdentifier, unspentTransactionOutput);
                    bloomFilter.addItem(unspentTransactionOutputEntryInflater.keyToBytes(transactionOutputIdentifier));
                }
                finally {
                    writeLock.unlock();
                }
            }

            for (final TransactionOutputIdentifier transactionOutputIdentifier : blockUtxoDiff.spentTransactionOutputIdentifiers) {
                writeLock.lock();
                try {
                    treeMap.put(transactionOutputIdentifier, null);
                    bloomFilter.addItem(unspentTransactionOutputEntryInflater.keyToBytes(transactionOutputIdentifier));
                }
                finally {
                    writeLock.unlock();
                }
            }

            nanoTimer.stop();
            System.out.println("put: " + nanoTimer.getMillisecondsElapsed() + "ms");
        }
    }
}
