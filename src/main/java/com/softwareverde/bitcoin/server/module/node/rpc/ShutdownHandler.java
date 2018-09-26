package com.softwareverde.bitcoin.server.module.node.rpc;

import com.softwareverde.bitcoin.server.module.node.JsonRpcSocketServerHandler;
import com.softwareverde.bitcoin.server.module.node.sync.BlockChainBuilder;
import com.softwareverde.bitcoin.server.module.node.sync.BlockHeaderDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;

public class ShutdownHandler implements JsonRpcSocketServerHandler.ShutdownHandler {
    protected final Thread _mainThread;
    protected final BlockHeaderDownloader _blockHeaderDownloader;
    protected final BlockDownloader _blockDownloader;
    protected final BlockChainBuilder _blockChainBuilder;

    public ShutdownHandler(final Thread mainThread, final BlockHeaderDownloader blockHeaderDownloader, final BlockDownloader blockDownloader, final BlockChainBuilder blockChainBuilder) {
        _mainThread = mainThread;
        _blockHeaderDownloader = blockHeaderDownloader;
        _blockDownloader = blockDownloader;
        _blockChainBuilder = blockChainBuilder;
    }

    @Override
    public Boolean shutdown() {
        _blockHeaderDownloader.stop();
        _blockDownloader.stop();
        _blockChainBuilder.stop();
        _mainThread.interrupt();
        return true;
    }
}
