package com.softwareverde.bitcoin.miner;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockHasher;
import com.softwareverde.bitcoin.block.ImmutableBlock;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.inflater.BlockHeaderInflaters;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.unlocking.ImmutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

import java.util.concurrent.atomic.AtomicLong;

public class Miner {
    protected final Container<Boolean> hasBeenFound = new Container<Boolean>(false);

    protected final BlockHeaderInflaters _blockHeaderInflaters;
    protected final GpuSha256 _gpuSha256;
    protected final Integer _cpuThreadCount;
    protected final Integer _gpuThreadCount;
    protected Boolean _shouldMutateTimestamp = true;

    public Miner(final Integer cpuThreadCount, final Integer gpuThreadCount, final GpuSha256 gpuSha256) {
        this(cpuThreadCount, gpuThreadCount, gpuSha256, new CoreInflater());
    }

    public Miner(final Integer cpuThreadCount, final Integer gpuThreadCount, final GpuSha256 gpuSha256, final BlockHeaderInflaters blockHeaderInflaters) {
        _cpuThreadCount = cpuThreadCount;
        _gpuThreadCount = gpuThreadCount;
        _gpuSha256 = gpuSha256;
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
                final long startTime = System.currentTimeMillis();

                while (! hasBeenFound.value) {
                    try { Thread.sleep(5000); } catch (final Exception e) { }

                    long hashCount = 0;
                    for (int j = 0; j < (_cpuThreadCount + _gpuThreadCount); ++j) {
                        hashCount += hashCounts.get(j).get();
                    }

                    final long now = System.currentTimeMillis();
                    final long elapsed = (now - startTime) + 1;
                    final double hashesPerSecond = (((double) hashCount) / elapsed * 1000D);
                    Logger.info(String.format("%.2f h/s", hashesPerSecond));
                }
            }
        };
        threads.add(new Thread(hashCountPrinter));

        int threadIndex = 0;

        if (_gpuSha256 != null) {
            for (int i = 0; i < _gpuThreadCount; ++i) {
                final Integer index = (threadIndex++);
                hashCounts.add(new AtomicLong(0L));

                final Thread thread = (new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final int hashesPerIteration = _gpuSha256.getMaxBatchSize();

                        int mutationCount = 0;
                        final BlockHeaderInflater blockHeaderInflater = _blockHeaderInflaters.getBlockHeaderInflater();
                        final BlockHeaderDeflater blockHeaderDeflater = _blockHeaderInflaters.getBlockHeaderDeflater();

                        final MutableBlock mutableBlock = new MutableBlock(prototypeBlock);
                        final Difficulty difficulty = mutableBlock.getDifficulty();

                        final MutableTransaction coinbaseTransaction = new MutableTransaction(mutableBlock.getTransactions().get(0));
                        final UnlockingScript originalCoinbaseSignature = coinbaseTransaction.getTransactionInputs().get(0).getUnlockingScript();

                        if (_shouldMutateTimestamp) {
                            mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);
                        }

                        long nonce = (long) (Math.random() * Long.MAX_VALUE);

                        boolean isValidDifficulty = false;
                        while ( (! isValidDifficulty) && (! hasBeenFound.value) ) {

                            final MutableList<ByteArray> blockHeaderBytesList = new MutableList<ByteArray>();
                            for (int i = 0; i < hashesPerIteration; ++i) {
                                nonce += 1;
                                mutableBlock.setNonce(nonce);

                                if (nonce % 7777 == 0) {
                                    mutationCount += 1;

                                    if (_shouldMutateTimestamp) {
                                        mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);
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

                                blockHeaderBytesList.add(blockHeaderDeflater.toBytes(mutableBlock));
                            }

                            final List<Sha256Hash> blockHashes = _gpuSha256.sha256(_gpuSha256.sha256(blockHeaderBytesList));

                            for (int i = 0; i < hashesPerIteration; ++i) {
                                final Sha256Hash blockHash = blockHashes.get(i);

                                isValidDifficulty = difficulty.isSatisfiedBy(blockHash.toReversedEndian());

                                if (isValidDifficulty) {
                                    hasBeenFound.value = true;

                                    final BlockHeader blockHeader = blockHeaderInflater.fromBytes(blockHeaderBytesList.get(i));
                                    blockContainer.value = new ImmutableBlock(blockHeader, mutableBlock.getTransactions());
                                }
                            }

                            hashCounts.get(index).addAndGet(hashesPerIteration);
                        }
                    }
                }));
                threads.add(thread);
            }
        }

        for (int i = 0; i < _cpuThreadCount; ++i) {
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
                        mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);
                    }

                    long nonce = (long) (Math.random() * Long.MAX_VALUE);

                    boolean isValidDifficulty = false;
                    while ( (! isValidDifficulty) && (! hasBeenFound.value) ) {
                        nonce += 1;
                        mutableBlock.setNonce(nonce);

                        if (nonce % 7777 == 0) {
                            mutationCount += 1;

                            if (_shouldMutateTimestamp) {
                                mutableBlock.setTimestamp(System.currentTimeMillis() / 1000L);
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

                        final Sha256Hash blockHash = blockHasher.calculateBlockHash(mutableBlock);
                        isValidDifficulty = difficulty.isSatisfiedBy(blockHash);

                        if (isValidDifficulty) {
                            hasBeenFound.value = true;
                            blockContainer.value = mutableBlock;
                        }

                        hashCounts.get(index).incrementAndGet();
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
