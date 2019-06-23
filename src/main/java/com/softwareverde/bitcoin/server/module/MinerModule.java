package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.miner.GpuSha256;
import com.softwareverde.bitcoin.miner.Miner;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.io.Logger;

public class MinerModule {
    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected final String _previousBlockHashString;
    protected final String _base58CheckAddress;
    protected final Integer _cpuThreadCount;
    protected final Integer _gpuThreadCount;
    protected final GpuSha256 _gpuSha256;

    public MinerModule(final String previousBlockHashString, final String base58CheckAddress, final Integer cpuThreadCount, final Integer gpuThreadCount, final GpuSha256 gpuSha256) {
        _previousBlockHashString = previousBlockHashString;
        _base58CheckAddress = base58CheckAddress;
        _cpuThreadCount = cpuThreadCount;
        _gpuThreadCount = gpuThreadCount;
        _gpuSha256 = gpuSha256;
    }

    public void run() {
        try {
            final Sha256Hash previousBlockHash = Sha256Hash.fromHexString(_previousBlockHashString);
            final AddressInflater addressInflater = new AddressInflater();

            final Address address = addressInflater.fromBase58Check(_base58CheckAddress);
            if (address == null) {
                _printError("Invalid Bitcoin Address: "+ _base58CheckAddress);
                BitcoinUtil.exitFailure();
                return;
            }

            final MutableBlock prototypeBlock = new MutableBlock();
            {
                final MutableTransactionInput coinbaseTransactionInput = new MutableTransactionInput();
                final MutableTransactionOutput coinbaseTransactionOutput = new MutableTransactionOutput();
                final MutableTransaction coinbaseTransaction = new MutableTransaction();
                {
                    coinbaseTransactionInput.setPreviousOutputTransactionHash(new MutableSha256Hash());
                    coinbaseTransactionInput.setPreviousOutputIndex(0);
                    coinbaseTransactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
                    coinbaseTransactionInput.setUnlockingScript((new ScriptBuilder()).pushString("Mined via Bitcoin-Verde.").buildUnlockingScript());

                    coinbaseTransactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);
                    coinbaseTransactionOutput.setIndex(0);
                    coinbaseTransactionOutput.setLockingScript((ScriptBuilder.payToAddress(address)));

                    coinbaseTransaction.setVersion(1L);
                    coinbaseTransaction.setLockTime(new ImmutableLockTime(LockTime.MIN_TIMESTAMP));
                    coinbaseTransaction.addTransactionInput(coinbaseTransactionInput);
                    coinbaseTransaction.addTransactionOutput(coinbaseTransactionOutput);
                }

                prototypeBlock.setVersion(1L);
                prototypeBlock.setPreviousBlockHash(previousBlockHash);
                prototypeBlock.setTimestamp(System.currentTimeMillis() / 1000L);
                prototypeBlock.setNonce(0L);
                prototypeBlock.setDifficulty(Difficulty.BASE_DIFFICULTY);
                prototypeBlock.addTransaction(coinbaseTransaction);
            }

            final Miner miner = new Miner(_cpuThreadCount, _gpuThreadCount, _gpuSha256);
            final Block block = miner.mineBlock(prototypeBlock);

            final BlockDeflater blockDeflater = new BlockDeflater();
            Logger.log(block.getHash());
            Logger.log(blockDeflater.toBytes(block));
        }
        catch (final Exception exception) {
            exception.printStackTrace();
        }
    }
}
