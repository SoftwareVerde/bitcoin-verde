package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHash;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.mysql.embedded.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.NodeConnection;

public class RequestDataHandler implements BitcoinNode.RequestDataCallback {
    protected final MysqlDatabaseConnectionFactory _connectionFactory;

    public RequestDataHandler(final MysqlDatabaseConnectionFactory connectionFactory) {
        _connectionFactory = connectionFactory;
    }

    @Override
    public void run(final List<DataHash> dataHashes, final NodeConnection nodeConnection) {
        for (final DataHash dataHash : dataHashes) {
            switch (dataHash.getDataHashType()) {
                case BLOCK: {
                    final Sha256Hash blockHash = dataHash.getObjectHash();
                    Logger.log("Unsupported RequestDataMessage Type: " + dataHash.getDataHashType() + " : " + blockHash);
                } break;

                case TRANSACTION: {
                    final Sha256Hash transactionHash = dataHash.getObjectHash();
                    Logger.log("Unsupported RequestDataMessage Type: " + dataHash.getDataHashType() + " : " + transactionHash);
                } break;

                default: {
                    Logger.log("Unsupported RequestDataMessage Type: " + dataHash.getDataHashType());
                } break;
            }
        }
    }
}
