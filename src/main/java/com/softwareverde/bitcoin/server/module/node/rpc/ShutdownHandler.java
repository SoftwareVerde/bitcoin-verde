package com.softwareverde.bitcoin.server.module.node.rpc;

import com.softwareverde.bitcoin.server.module.node.JsonRpcSocketServerHandler;
import com.softwareverde.bitcoin.server.module.node.sync.BlockHeaderDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainBuilder;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.io.Logger;

public class ShutdownHandler implements JsonRpcSocketServerHandler.ShutdownHandler {
    protected final Thread _mainThread;
    protected final BlockHeaderDownloader _blockHeaderDownloader;
    protected final BlockDownloader _blockDownloader;
    protected final BlockchainBuilder _blockchainBuilder;

    public ShutdownHandler(final Thread mainThread, final BlockHeaderDownloader blockHeaderDownloader, final BlockDownloader blockDownloader, final BlockchainBuilder blockchainBuilder) {
        _mainThread = mainThread;
        _blockHeaderDownloader = blockHeaderDownloader;
        _blockDownloader = blockDownloader;
        _blockchainBuilder = blockchainBuilder;
    }

    @Override
    public Boolean shutdown() {
        Logger.log("[Stopping Syncing Headers]");
        _blockHeaderDownloader.stop();
        Logger.log("[Stopping Block Downloads]");
        _blockDownloader.stop();
        Logger.log("[Stopping Block Processing]");
        _blockchainBuilder.stop();
        Logger.log("[Shutting Down]");
        _mainThread.interrupt();
        return true;
    }
}
