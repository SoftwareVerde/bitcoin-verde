package com.softwareverde.bitcoin.server.module.stratum;

import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.rpc.BitcoinMiningRpcConnector;
import com.softwareverde.bitcoin.rpc.BitcoinNodeRpcAddress;
import com.softwareverde.bitcoin.rpc.BitcoinVerdeRpcConnector;
import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.rpc.RpcCredentials;
import com.softwareverde.bitcoin.server.configuration.StratumProperties;
import com.softwareverde.bitcoin.server.properties.PropertiesStore;
import com.softwareverde.bitcoin.server.stratum.task.StagnantStratumMineBlockTaskBuilderFactory;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.json.Json;

public class BitcoinVerdeStratumServer extends BitcoinCoreStratumServer {

    @Override
    protected BitcoinMiningRpcConnector _getBitcoinRpcConnector() {
        final String bitcoinRpcUrl = _stratumProperties.getBitcoinRpcUrl();
        final Integer bitcoinRpcPort = _stratumProperties.getBitcoinRpcPort();

        final RpcCredentials rpcCredentials = _stratumProperties.getRpcCredentials();
        final BitcoinNodeRpcAddress bitcoinNodeRpcAddress = new BitcoinNodeRpcAddress(bitcoinRpcUrl, bitcoinRpcPort, false);

        return new BitcoinVerdeRpcConnector(bitcoinNodeRpcAddress, rpcCredentials) {
            @Override
            protected NodeJsonRpcConnection _getRpcConnection() {
                final String host = _bitcoinNodeRpcAddress.getHost();
                final Integer port = _bitcoinNodeRpcAddress.getPort();
                return new NodeJsonRpcConnection(host, port, _threadPool) {
                    @Override
                    protected Json _executeJsonRequest(final Json rpcRequestJson) {
                        final Json response = super._executeJsonRequest(rpcRequestJson);
                        System.out.println(response);
                        return response;
                    }
                };
            }
        };
    }

    public BitcoinVerdeStratumServer(final StratumProperties stratumProperties, final PropertiesStore propertiesStore, final ThreadPool threadPool) {
        super(stratumProperties, propertiesStore, threadPool);
    }

    public BitcoinVerdeStratumServer(final StratumProperties stratumProperties, final PropertiesStore propertiesStore, final ThreadPool threadPool, final MasterInflater masterInflater) {
        super(stratumProperties, propertiesStore, threadPool, masterInflater);
    }

    public BitcoinVerdeStratumServer(final StratumProperties stratumProperties, final PropertiesStore propertiesStore, final ThreadPool threadPool, final MasterInflater masterInflater, final StagnantStratumMineBlockTaskBuilderFactory stratumMineBlockTaskBuilderFactory) {
        super(stratumProperties, propertiesStore, threadPool, masterInflater, stratumMineBlockTaskBuilderFactory);
    }
}
