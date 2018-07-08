package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.manager.NodeManager;

import java.util.List;

public class BitcoinNodeManager extends NodeManager<BitcoinNode> {
    public BitcoinNodeManager(final Integer maxNodeCount) {
        super(maxNodeCount, new BitcoinNodeFactory());
    }

    public void requestBlockHashesAfter(final Sha256Hash blockHash, final BitcoinNode.QueryCallback callback) {
        this.executeRequest(new NodeApiInvocation<BitcoinNode>() {
            @Override
            public void run(final BitcoinNode bitcoinNode, final NodeApiInvocationCallback nodeApiInvocationCallback) {
                bitcoinNode.requestBlockHashesAfter(blockHash, new BitcoinNode.QueryCallback() {
                    @Override
                    public void onResult(final List<Sha256Hash> result) {
                        final Boolean requestTimedOut = nodeApiInvocationCallback.didTimeout();
                        if (requestTimedOut) { return; }

                        if (callback != null) {
                            callback.onResult(result);
                        }
                    }
                });
            }

            @Override
            public void onFailure() {
                Logger.log("Request failed: BitcoinNodeManager.requestBlockHashesAfter("+ blockHash +")");
            }
        });
    }

    public void requestBlock(final Sha256Hash blockHash, final BitcoinNode.DownloadBlockCallback callback) {
        this.executeRequest(new NodeApiInvocation<BitcoinNode>() {
            @Override
            public void run(final BitcoinNode bitcoinNode, final NodeApiInvocationCallback nodeApiInvocationCallback) {
                bitcoinNode.requestBlock(blockHash, new BitcoinNode.DownloadBlockCallback() {
                    @Override
                    public void onResult(final Block result) {
                        final Boolean requestTimedOut = nodeApiInvocationCallback.didTimeout();
                        if (requestTimedOut) { return; }

                        if (callback != null) {
                            callback.onResult(result);
                        }
                    }
                });
            }

            @Override
            public void onFailure() {
                Logger.log("Request failed: BitcoinNodeManager.requestBlock("+ blockHash +")");
            }
        });
    }
}
