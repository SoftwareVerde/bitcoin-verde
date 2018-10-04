package com.softwareverde.bitcoin.server.module.node.sync.block.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.io.Logger;
import com.softwareverde.util.DateUtil;
import com.softwareverde.util.timer.NanoTimer;

public class BlockValidatorThread {
    public interface InvalidBlockCallback {
        void onInvalidBlock(Block invalidBlock);
    }

    public interface BlockQueue {
        Block getNextBlock();
    }

    public static final InvalidBlockCallback IGNORE_INVALID_BLOCKS_CALLBACK = new InvalidBlockCallback() {
        @Override
        public void onInvalidBlock(final Block invalidBlock) { }
    };

    protected final InvalidBlockCallback _invalidBlockCallback;
    protected final BlockQueue _blockQueue;
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

    public BlockValidatorThread(final BlockQueue blockQueue, final BlockProcessor blockProcessor, final InvalidBlockCallback invalidBlockCallback) {
        _blockQueue = blockQueue;
        _blockProcessor = blockProcessor;
        _invalidBlockCallback = invalidBlockCallback;
    }

    protected void _execute() {
        final Thread originalThread = _thread;

        while (_shouldContinue) {
            final Block block = _blockQueue.getNextBlock(); // TODO: Consider blocking instead of polling...
            if (block != null) {
                final NanoTimer timer = new NanoTimer();

                timer.start();
                final Boolean isValidBlock = (_blockProcessor.processBlock(block) != null);
                timer.stop();

                if (! isValidBlock) {
                    Logger.log("Invalid block: " + block.getHash());
                    _invalidBlockCallback.onInvalidBlock(block);
                }
                else {
                    Logger.log("Process Block Duration: " + String.format("%.2f", timer.getMillisecondsElapsed()) + " (" + String.format("%.2f", (block.getTransactionCount() / timer.getMillisecondsElapsed() * 1000L)) + " tps) | " + DateUtil.timestampToDatetimeString(block.getTimestamp() * 1000L));
                }
            }
            else {
                try { Thread.sleep(200L); } catch (final Exception exception) { break; }
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