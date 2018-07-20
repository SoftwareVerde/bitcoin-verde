package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.util.timer.Timer;

import java.util.concurrent.ConcurrentLinkedQueue;

public class BlockValidatorThread extends Thread {
    protected final ConcurrentLinkedQueue<Block> _queuedBlocks;
    protected final BlockProcessor _blockProcessor;

    public BlockValidatorThread(final ConcurrentLinkedQueue<Block> queuedBlocks, final BlockProcessor blockProcessor) {
        _queuedBlocks = queuedBlocks;
        _blockProcessor = blockProcessor;
    }

    @Override
    public void run() {
        while (true) {
            final Block block = _queuedBlocks.poll();
            if (block != null) {
                final Timer timer = new Timer();
                timer.start();
                final Boolean isValidBlock = _blockProcessor.processBlock(block);
                timer.stop();
                Logger.log("Process Block Duration: " + String.format("%.2f", timer.getMillisecondsElapsed()));

                if (! isValidBlock) {
                    Logger.log("Invalid block: " + block.getHash());
                    BitcoinUtil.exitFailure();
                }
            }
            else {
                try { Thread.sleep(500L); } catch (final Exception exception) { break; }
            }
        }

        Logger.log("Block Validator Thread exiting...");
    }
}