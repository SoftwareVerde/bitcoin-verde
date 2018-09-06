package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.io.Logger;
import com.softwareverde.util.DateUtil;
import com.softwareverde.util.timer.Timer;

import java.util.concurrent.ConcurrentLinkedQueue;

public class BlockValidatorThread {
    protected final ConcurrentLinkedQueue<Block> _queuedBlocks;
    protected final BlockProcessor _blockProcessor;
    protected Thread _thread = null;
    protected volatile Boolean _shouldContinue = true;

    protected void _initThread() {
        _thread = new Thread(new Runnable() {
            @Override
            public void run() {
                _execute();
            }
        });
        _thread.setPriority(Thread.MAX_PRIORITY);
    }

    public BlockValidatorThread(final ConcurrentLinkedQueue<Block> queuedBlocks, final BlockProcessor blockProcessor) {
        _queuedBlocks = queuedBlocks;
        _blockProcessor = blockProcessor;
    }

    protected void _execute() {
        final Thread originalThread = _thread;

        while (_shouldContinue) {
            final Block block = _queuedBlocks.poll();
            if (block != null) {
                final Timer timer = new Timer();
                timer.start();
                final Boolean isValidBlock = _blockProcessor.processBlock(block);
                timer.stop();
                Logger.log("Process Block Duration: " + String.format("%.2f", timer.getMillisecondsElapsed()) + " ("+ String.format("%.2f", (block.getTransactionCount() / timer.getMillisecondsElapsed() * 1000L)) +" tps) | " + DateUtil.timestampToDatetimeString(block.getTimestamp() * 1000L));

                if (! isValidBlock) {
                    Logger.log("Invalid block: " + block.getHash());
                }
            }
            else {
                try { Thread.sleep(100L); } catch (final Exception exception) { break; }
            }
        }

        Logger.log("Block Validator Thread exiting...");

        if (_thread == originalThread) { // If _thread was recreated, don't unset the new thread...
            _thread = null;
        }
    }

    public void start() {
        if (_thread != null) { return; }

        _initThread();
        _shouldContinue = true;
        _thread.start();
    }

    public void stop() {
        _shouldContinue = false;

        if (_thread != null) {
            try {
                _thread.join();
            }
            catch (final Exception exception) { }

            _thread = null;
        }
    }
}