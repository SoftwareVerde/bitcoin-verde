package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.security.hash.sha256.MutableSha256Hash;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.inflater.MasterInflater;
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
import com.softwareverde.logging.Logger;

public class MinerModule {
    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected final MasterInflater _masterInflater;
    protected final Integer _cpuThreadCount;
    protected final MutableBlock _prototypeBlock;

    public MinerModule(final Integer cpuThreadCount, final String prototypeBlock) {
        _masterInflater = new CoreInflater();
        _cpuThreadCount = cpuThreadCount;

        final BlockInflater blockInflater = _masterInflater.getBlockInflater();
        _prototypeBlock = blockInflater.fromBytes(ByteArray.fromHexString(prototypeBlock));
    }

    public void run() {
        try {
            final Miner miner = new Miner(_cpuThreadCount, 0, null, _masterInflater);
            miner.setShouldMutateTimestamp(true);
            final Block block = miner.mineBlock(_prototypeBlock);
            if (block == null) {
                Logger.info("No block found.");
                return;
            }

            final BlockDeflater blockDeflater = _masterInflater.getBlockDeflater();
            Logger.info(block.getHash());
            Logger.info(blockDeflater.toBytes(block));
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }
    }
}
