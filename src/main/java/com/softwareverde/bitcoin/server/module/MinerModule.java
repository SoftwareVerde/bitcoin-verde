package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.miner.Miner;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.type.address.Address;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;

public class MinerModule {
    public static void execute(final String previousBlockHashString, final String base58CheckAddress, final Integer cpuThreadCount, final Integer gpuThreadCount) {
        final MinerModule minerModule = new MinerModule(previousBlockHashString, base58CheckAddress, cpuThreadCount, gpuThreadCount);
        minerModule.run();
    }

    protected void _exitFailure() {
        System.exit(1);
    }

    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected final String _previousBlockHashString;
    protected final String _base58CheckAddress;
    protected final Integer _cpuThreadCount;
    protected final Integer _gpuThreadCount;

    public MinerModule(final String previousBlockHashString, final String base58CheckAddress, final Integer cpuThreadCount, final Integer gpuThreadCount) {
        this._previousBlockHashString = previousBlockHashString;
        this._base58CheckAddress = base58CheckAddress;
        this._cpuThreadCount = cpuThreadCount;
        this._gpuThreadCount = gpuThreadCount;
    }

    public void run() {
//        try {
//            final Hash previousBlockHash = MutableHash.fromHexString(_previousBlockHashString);
//
//            final Address address = Address.fromBase58Check(_base58CheckAddress);
//            if (address == null) {
//                _printError("Invalid Bitcoin Address: "+ _base58CheckAddress);
//                _exitFailure();
//                return;
//            }
//
//            final MutableBlock prototypeBlock = new MutableBlock();
//            {
//                final MutableTransactionInput coinbaseTransactionInput = new MutableTransactionInput();
//                final MutableTransactionOutput coinbaseTransactionOutput = new MutableTransactionOutput();
//                final MutableTransaction coinbaseTransaction = new MutableTransaction();
//                {
//                    coinbaseTransactionInput.setPreviousOutputTransactionHash(new MutableHash());
//                    coinbaseTransactionInput.setPreviousOutputIndex(0);
//                    coinbaseTransactionInput.setSequenceNumber(TransactionInput.MAX_SEQUENCE_NUMBER);
//                    coinbaseTransactionInput.setUnlockingScript((new ScriptBuilder()).pushString("Mined via Bitcoin-Verde.").buildUnlockingScript());
//
//                    coinbaseTransactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);
//                    coinbaseTransactionOutput.setIndex(0);
//                    coinbaseTransactionOutput.setLockingScript((ScriptBuilder.payToAddress(address)));
//
//                    coinbaseTransaction.setVersion(1);
//                    coinbaseTransaction.setLockTime(new ImmutableLockTime(LockTime.MIN_TIMESTAMP));
//                    coinbaseTransaction.setHasWitnessData(false);
//                    coinbaseTransaction.addTransactionInput(coinbaseTransactionInput);
//                    coinbaseTransaction.addTransactionOutput(coinbaseTransactionOutput);
//                }
//
//                prototypeBlock.setVersion(1);
//                prototypeBlock.setPreviousBlockHash(previousBlockHash);
//                prototypeBlock.setTimestamp(System.currentTimeMillis() / 1000L);
//                prototypeBlock.setNonce(0L);
//                prototypeBlock.setDifficulty(Difficulty.BASE_DIFFICULTY);
//                prototypeBlock.addTransaction(coinbaseTransaction);
//            }
//
//            final Miner miner = new Miner(_cpuThreadCount, _gpuThreadCount);
//            final Block block = miner.mineBlock(prototypeBlock);
//
//            final BlockDeflater blockDeflater = new BlockDeflater();
//            Logger.log(block.getHash());
//            Logger.log(HexUtil.toHexString(blockDeflater.toBytes(block).getBytes()));
//        }
//        catch (final Exception exception) {
//            exception.printStackTrace();
//        }

        try {
            final BlockInflater blockInflater = new BlockInflater();
            final Block prototypeBlock;
            {
                final MutableBlock mutableBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray("010000004B0360D834A330EC7833E30E1F523EE05A0793361E29A73421964F980000000027B64A020AF294E903FEED93768705336A20090612A043F47AF462A2F5E5B564F8EE3A4B6AD8001DD3A437070101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF07046AD8001D0104FFFFFFFF0100F2052A0100000043410428F88CA471C9718C4E52DF12B756BABEDF6A970082C3CC2BDC9F7E0C53479B7F0D9201FD4B0C3EB3E82C48EF6C011B51994EBC18177C85B20FFE8FC844ECA755AC00000000"));
                mutableBlock.setPreviousBlockHash(MutableHash.fromHexString("EE5D644F087DE47F9767C8D7E081D431C596250B79339C63811A4B91CB3A8B1B"));
                prototypeBlock = mutableBlock.asConst();
            }

            final Miner miner = new Miner(_cpuThreadCount, _gpuThreadCount);
            miner.setShouldMutateTimestamp(false);
            final Block block = miner.mineBlock(prototypeBlock);

            final BlockDeflater blockDeflater = new BlockDeflater();
            Logger.log(block.getHash());
            Logger.log(HexUtil.toHexString(blockDeflater.toBytes(block).getBytes()));
        }
        catch (final Exception exception) { }
    }
}
