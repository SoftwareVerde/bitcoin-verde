package com.softwareverde.bitcoin.server.module.node.rpc;

import com.softwareverde.bitcoin.server.module.node.JsonRpcSocketServerHandler;
import com.softwareverde.bitcoin.server.module.node.sync.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.BlockHeaderDownloader;

public class ShutdownHandler implements JsonRpcSocketServerHandler.ShutdownHandler {
    protected final Thread _mainThread;
    final BlockHeaderDownloader _blockHeaderDownloader;
    protected final BlockDownloader _blockDownloader;

    public ShutdownHandler(final Thread mainThread, final BlockHeaderDownloader blockHeaderDownloader, final BlockDownloader blockDownloader) {
        _mainThread = mainThread;
        _blockHeaderDownloader = blockHeaderDownloader;
        _blockDownloader = blockDownloader;
    }

    @Override
    public Boolean shutdown() {
        _blockHeaderDownloader.stop();
        _blockDownloader.stop();
        _mainThread.interrupt();
        return true;
    }
}
