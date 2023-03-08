package com.softwareverde.bitcoin.stratum.rpc;

import com.softwareverde.bitcoin.rpc.BitcoinMiningRpcConnector;
import com.softwareverde.bitcoin.rpc.BitcoinMiningRpcConnectorFactory;
import com.softwareverde.bitcoin.rpc.BitcoinNodeRpcAddress;
import com.softwareverde.bitcoin.rpc.RpcCredentials;
import com.softwareverde.bitcoin.rpc.core.BitcoinCoreRpcConnector;

public class BitcoinCoreMiningRpcConnectorFactory implements BitcoinMiningRpcConnectorFactory {
    protected final String _bitcoinRpcUrl;
    protected final Integer _bitcoinRpcPort;
    protected final RpcCredentials _rpcCredentials;

    public BitcoinCoreMiningRpcConnectorFactory(final String bitcoinRpcUrl, final Integer bitcoinRpcPort, final RpcCredentials rpcCredentials) {
        _bitcoinRpcUrl = bitcoinRpcUrl;
        _bitcoinRpcPort = bitcoinRpcPort;
        _rpcCredentials = rpcCredentials;
    }

    @Override
    public BitcoinMiningRpcConnector newBitcoinMiningRpcConnector() {
        final BitcoinNodeRpcAddress bitcoinNodeRpcAddress = new BitcoinNodeRpcAddress(_bitcoinRpcUrl, _bitcoinRpcPort, false);
        return new BitcoinCoreRpcConnector(bitcoinNodeRpcAddress, _rpcCredentials);
    }
}
