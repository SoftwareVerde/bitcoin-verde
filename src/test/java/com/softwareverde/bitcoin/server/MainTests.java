package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.network.p2p.node.Node;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;

public class MainTests {
    protected void _loop() {
        while (true) {
            try { Thread.sleep(500); } catch (final Exception e) { }
        }
    }

    // @Test
    public void execute() {
        final String host = "btc.softwareverde.com";
        final Integer port = 8333;

        final Node node = new Node(host, port);

        node.requestBlockHashesAfter(Block.GENESIS_BLOCK_HEADER_HASH, new Node.QueryCallback() {
            @Override
            public void onResult(final java.util.List<Sha256Hash> blockHashes) {
                Logger.log(blockHashes.size());

                node.requestBlock(Block.GENESIS_BLOCK_HEADER_HASH, new Node.DownloadBlockCallback() {
                    @Override
                    public void onResult(final Block block) {
                        Logger.log("BLOCK: " + HexUtil.toHexString(block.getHash().getBytes()));
                    }
                });
            }
        });

        _loop();
    }
}
