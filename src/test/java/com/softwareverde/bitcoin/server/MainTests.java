package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.node.Node;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import org.junit.Test;

public class MainTests {
    protected void _loop() {
        while (true) {
            try { Thread.sleep(500); } catch (final Exception e) { }

            if ((Math.random() * 777) % 1000 < 10) {
                System.gc();
            }

            // _printMemoryUsage();
            // _checkForDeadlockedThreads();
        }
    }

    // @Test
    public void execute() {
        final String host = "btc.softwareverde.com";
        final Integer port = 8333;

        final Node node = new Node(host, port);

        node.requestBlock(Block.GENESIS_BLOCK_HEADER_HASH, new Node.DownloadBlockCallback() {
            @Override
            public void onBlockDownloaded(final Block block) {
                System.out.println("BLOCK: "+ BitcoinUtil.toHexString(block.calculateSha256Hash()));
            }
        });

        _loop();
    }
}
