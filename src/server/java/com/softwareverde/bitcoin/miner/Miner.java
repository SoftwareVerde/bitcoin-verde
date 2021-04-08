package com.softwareverde.bitcoin.miner;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockHasher;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.inflater.BlockHeaderInflaters;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.unlocking.ImmutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.type.time.SystemTime;

import java.util.concurrent.atomic.AtomicLong;

public class Miner {
    protected final Container<Boolean> _hasBeenFound = new Container<Boolean>(false);

    protected final SystemTime _systemTime = new SystemTime();
    protected final BlockHeaderInflaters _blockHeaderInflaters;
    protected final Integer _cpuThreadCount;
    protected final Integer _gpuThreadCount;
    protected Boolean _shouldMutateTimestamp = true;

    public Miner(final Integer cpuThreadCount, final Integer gpuThreadCount) {
        this(cpuThreadCount, gpuThreadCount, new CoreInflater());
    }

    public Miner(final Integer cpuThreadCount, final Integer gpuThreadCount, final BlockHeaderInflaters blockHeaderInflaters) {
        _cpuThreadCount = cpuThreadCount;
        _gpuThreadCount = gpuThreadCount;
        _blockHeaderInflaters = blockHeaderInflaters;
    }

    public void setShouldMutateTimestamp(final Boolean shouldMutateTimestamp) {
        _shouldMutateTimestamp = shouldMutateTimestamp;
    }

    public Block mineBlock(final Block prototypeBlock) throws Exception {
        final MutableList<Thread> threads = new MutableList<Thread>();
        final MutableList<AtomicLong> hashCounts = new MutableList<AtomicLong>();

        final Container<Block> blockContainer = new Container<Block>();

        final Runnable hashCountPrinter = new Runnable() {
            @Override
            public synchronized void run() {
                final long startTime = _systemTime.getCurrentTimeInMilliSeconds();

                while (! _hasBeenFound.value) {
                    try {
                        Thread.sleep(5000);
                    }
                    catch (final Exception exception) {
                        _hasBeenFound.value = true;
                        break;
                    }

                    long hashCount = 0;
                    for (int j = 0; j < (_cpuThreadCount + _gpuThreadCount); ++j) {
                        hashCount += hashCounts.get(j).get();
                    }

                    final long now = _systemTime.getCurrentTimeInMilliSeconds();
                    final long elapsed = (now - startTime) + 1;
                    final double hashesPerSecond = (((double) hashCount) / elapsed * 1000D);
                    Logger.info(String.format("%.2f h/s", hashesPerSecond));
                }
            }
        };
        threads.add(new Thread(hashCountPrinter));

        int threadIndex = 0;

        final long nonceCountPerThread = ((2L << 32) / _cpuThreadCount.longValue());
        for (int i = 0; i < _cpuThreadCount; ++i) {
            final long startingNonce = (i * nonceCountPerThread);
            final long endingNonce = (((i + 1) * nonceCountPerThread) - 1L);

            final Integer index = (threadIndex++);
            hashCounts.add(new AtomicLong(0L));

            final Thread thread = (new Thread(new Runnable() {
                @Override
                public void run() {
                    int mutationCount = 0;
                    final BlockHasher blockHasher = new BlockHasher();

                    final MutableBlock mutableBlock = new MutableBlock(prototypeBlock);
                    final Difficulty difficulty = mutableBlock.getDifficulty();

                    final MutableTransaction coinbaseTransaction = new MutableTransaction(mutableBlock.getTransactions().get(0));
                    final UnlockingScript originalCoinbaseSignature = coinbaseTransaction.getTransactionInputs().get(0).getUnlockingScript();

                    if (_shouldMutateTimestamp) {
                        mutableBlock.setTimestamp(_systemTime.getCurrentTimeInSeconds());
                    }

                    long nonce = startingNonce;

                    boolean isValidDifficulty = false;
                    while (! isValidDifficulty) {
                        nonce += 1;
                        mutableBlock.setNonce(nonce);

                        if (nonce == endingNonce) {
                            if (_hasBeenFound.value) { break; }

                            mutationCount += 1;
                            nonce = startingNonce;

                            if (_shouldMutateTimestamp) {
                                mutableBlock.setTimestamp(_systemTime.getCurrentTimeInSeconds());
                            }
                            else {
                                final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput(coinbaseTransaction.getTransactionInputs().get(0));
                                final ScriptBuilder scriptBuilder = new ScriptBuilder();
                                scriptBuilder.pushString(String.valueOf(mutationCount));
                                final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
                                byteArrayBuilder.appendBytes(originalCoinbaseSignature.getBytes());
                                byteArrayBuilder.appendBytes(scriptBuilder.build().getBytes());
                                mutableTransactionInput.setUnlockingScript(new ImmutableUnlockingScript(MutableByteArray.wrap(byteArrayBuilder.build())));
                                coinbaseTransaction.setTransactionInput(0, mutableTransactionInput);
                                mutableBlock.replaceTransaction(0, coinbaseTransaction);
                            }
                        }

                        final Sha256Hash blockHash = blockHasher.calculateBlockHash(mutableBlock, (_cpuThreadCount > 8));
                        isValidDifficulty = difficulty.isSatisfiedBy(blockHash);

                        if (isValidDifficulty) {
                            _hasBeenFound.value = true;
                            blockContainer.value = mutableBlock;
                        }

                        hashCounts.get(index).incrementAndGet();
                    }
                }
            }));
            thread.setPriority(Thread.MAX_PRIORITY);
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
