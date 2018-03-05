package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.ImmutableDifficulty;
import com.softwareverde.bitcoin.miner.Miner;
import com.softwareverde.bitcoin.server.node.Node;
import com.softwareverde.bitcoin.transaction.ImmutableTransaction;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.util.ConstUtil;
import com.softwareverde.io.Logger;
import org.junit.Test;

public class MainTests {
    protected void _loop() {
        while (true) {
            try { Thread.sleep(500); } catch (final Exception e) { }
        }
    }

//    @Test
    public void execute() {
        final String host = "btc.softwareverde.com";
        final Integer port = 8333;

        final Node node = new Node(host, port);

        node.getBlockHashesAfter(Block.GENESIS_BLOCK_HEADER_HASH, new Node.QueryCallback() {
            @Override
            public void onResult(final java.util.List<Hash> blockHashes) {
                Logger.log(blockHashes.size());

                node.requestBlock(Block.GENESIS_BLOCK_HEADER_HASH, new Node.DownloadBlockCallback() {
                    @Override
                    public void onResult(final Block block) {
                        Logger.log("BLOCK: " + BitcoinUtil.toHexString(block.getHash()));
                    }
                });
            }
        });

        _loop();
    }

    @Test
    public void mineBlock() {
        // Mine Hardcoded Block...
        try {
            final BlockInflater blockInflater = new BlockInflater();

            final Block previousBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000004860EB18BF1B1620E37E9490FC8A427514416FD75159AB86688E9A8300000000E2F61C3F71D1DEFD3FA999DFA36953755C690689799962B48BEBD836974E8CF9E5E59C5AFFFF001DA4A938F900"));

            final MutableBlock prototypeBlock = new MutableBlock();
            {
                final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
                mutableTransactionInput.setPreviousTransactionOutputHash(new MutableHash());
                mutableTransactionInput.setPreviousTransactionOutputIndex(0);
                mutableTransactionInput.setSequenceNumber(TransactionInput.MAX_SEQUENCE_NUMBER);
                mutableTransactionInput.setUnlockingScript((new ScriptBuilder()).pushString("Mined via Bitcoin-Verde.").build());

                final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
                mutableTransactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);
                mutableTransactionOutput.setIndex(0);
                mutableTransactionOutput.setLockingScript((ScriptBuilder.payToAddress("13TXBs1AonKbypUZCRYRCFnuLppqm69odd")));

                final MutableTransaction coinbaseTransaction = new MutableTransaction();
                coinbaseTransaction.setVersion(1);
                coinbaseTransaction.setLockTime(new ImmutableLockTime(LockTime.MIN_TIMESTAMP));
                coinbaseTransaction.setHasWitnessData(false);
                coinbaseTransaction.addTransactionInput(mutableTransactionInput);
                coinbaseTransaction.addTransactionOutput(mutableTransactionOutput);

                // Logger.log(BitcoinUtil.toHexString(coinbaseTransaction.getBytes()));
                // _exitFailure();

                prototypeBlock.setVersion(1);
                prototypeBlock.setPreviousBlockHash(previousBlock.getHash());
                prototypeBlock.setTimestamp(System.currentTimeMillis() / 1000L);
                prototypeBlock.setNonce(0L);
                prototypeBlock.setDifficulty(new ImmutableDifficulty(ByteUtil.integerToBytes(Difficulty.BASE_DIFFICULTY_SIGNIFICAND), Difficulty.BASE_DIFFICULTY_EXPONENT));
                prototypeBlock.addTransaction(coinbaseTransaction);
            }

            {
                final BlockHeader blockHeader = prototypeBlock.asConst();
                final ImmutableBlockHeader immutableBlockHeader = blockHeader.asConst();
                immutableBlockHeader.asConst();
            }

            final Miner miner = new Miner();
            miner.mineBlock(previousBlock, prototypeBlock);
        }
        catch (final Exception exception) {
            exception.printStackTrace();
        }
    }
}
