package com.softwareverde.bitcoin.stratum.rpc;

import com.softwareverde.bitcoin.rpc.BitcoinMiningRpcConnector;
import com.softwareverde.bitcoin.rpc.BitcoinMiningRpcConnectorFactory;
import com.softwareverde.bitcoin.rpc.BitcoinNodeRpcAddress;
import com.softwareverde.bitcoin.rpc.BitcoinVerdeRpcConnector;
import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.rpc.RpcCredentials;

public class BitcoinVerdeMiningRpcConnectorFactory implements BitcoinMiningRpcConnectorFactory {
    protected final String _bitcoinRpcUrl;
    protected final Integer _bitcoinRpcPort;
    protected final RpcCredentials _rpcCredentials;

    public BitcoinVerdeMiningRpcConnectorFactory(final String bitcoinRpcUrl, final Integer bitcoinRpcPort, final RpcCredentials rpcCredentials) {
        _bitcoinRpcUrl = bitcoinRpcUrl;
        _bitcoinRpcPort = bitcoinRpcPort;
        _rpcCredentials = rpcCredentials;
    }

    @Override
    public BitcoinMiningRpcConnector newBitcoinMiningRpcConnector() {
        final BitcoinNodeRpcAddress bitcoinNodeRpcAddress = new BitcoinNodeRpcAddress(_bitcoinRpcUrl, _bitcoinRpcPort, false);

        return new BitcoinVerdeRpcConnector(bitcoinNodeRpcAddress, _rpcCredentials) {
            @Override
            protected NodeJsonRpcConnection _getRpcConnection() {
                final String host = _bitcoinNodeRpcAddress.getHost();
                final Integer port = _bitcoinNodeRpcAddress.getPort();
                return new NodeJsonRpcConnection(host, port, _threadPool);
            }
        };
    }
}
