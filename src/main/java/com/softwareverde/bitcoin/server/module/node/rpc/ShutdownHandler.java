package com.softwareverde.bitcoin.server.module.node.rpc;

import com.softwareverde.bitcoin.server.module.node.JsonRpcSocketServerHandler;
import com.softwareverde.bitcoin.server.module.node.sync.BlockSynchronizer;
import com.softwareverde.bitcoin.server.module.node.sync.BlockHeaderDownloader;

public class ShutdownHandler implements JsonRpcSocketServerHandler.ShutdownHandler {
    protected final Thread _mainThread;
    final BlockHeaderDownloader _blockHeaderDownloader;
    protected final BlockSynchronizer _blockSynchronizer;

    public ShutdownHandler(final Thread mainThread, final BlockHeaderDownloader blockHeaderDownloader, final BlockSynchronizer blockSynchronizer) {
        _mainThread = mainThread;
        _blockHeaderDownloader = blockHeaderDownloader;
        _blockSynchronizer = blockSynchronizer;
    }

    @Override
    public Boolean shutdown() {
        _blockHeaderDownloader.stop();
        _blockSynchronizer.stop();
        _mainThread.interrupt();
        return true;
    }
}
