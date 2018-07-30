package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.manager.NodeManager;

public class BitcoinNodeManager extends NodeManager<BitcoinNode> {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;

    @Override
    protected void _addNode(final BitcoinNode node) {
        super._addNode(node);

        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            nodeDatabaseManager.storeNode(node);

        }
        catch (final DatabaseException databaseException) {
            Logger.log(databaseException);
        }
    }

    @Override
    public void _onNodeHandshakeComplete(final BitcoinNode node) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            nodeDatabaseManager.updateLastHandshake(node);
            nodeDatabaseManager.updateNodeFeatures(node);
        }
        catch (final DatabaseException databaseException) {
            Logger.log(databaseException);
        }
    }

    public BitcoinNodeManager(final Integer maxNodeCount, final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
        super(maxNodeCount, new BitcoinNodeFactory());
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    protected void _requestBlockHeaders(final List<Sha256Hash> blockHashes, final BitcoinNode.DownloadBlockHeadersCallback callback) {
        this.executeRequest(new NodeApiInvocation<BitcoinNode>() {
            @Override
            public void run(final BitcoinNode bitcoinNode, final NodeApiInvocationCallback nodeApiInvocationCallback) {
                bitcoinNode.requestBlockHeaders(blockHashes, new BitcoinNode.DownloadBlockHeadersCallback() {
                    @Override
                    public void onResult(final List<BlockHeaderWithTransactionCount> result) {
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
                final Sha256Hash firstBlockHash = (blockHashes.isEmpty() ? null : blockHashes.get(0));
                Logger.log("Request failed: BitcoinNodeManager.requestBlockHeader("+ firstBlockHash +")");
            }
        });
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

    public void requestBlockHeadersAfter(final Sha256Hash blockHash, final BitcoinNode.DownloadBlockHeadersCallback callback) {
        final MutableList<Sha256Hash> blockHashes = new MutableList<Sha256Hash>(1);
        blockHashes.add(blockHash);

        _requestBlockHeaders(blockHashes, callback);
    }

    public void requestBlockHeadersAfter(final List<Sha256Hash> blockHashes, final BitcoinNode.DownloadBlockHeadersCallback callback) {
        _requestBlockHeaders(blockHashes, callback);
    }
}
