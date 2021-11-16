package com.softwareverde.bitcoin.server.module.electrum;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTree;
import com.softwareverde.bitcoin.server.configuration.ElectrumProperties;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeSocket;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.socket.JsonSocket;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ElectrumModuleTests extends UnitTest {
    protected ElectrumProperties _electrumProperties;

    public ElectrumModuleTests() {
        _electrumProperties = new ElectrumProperties() {{
            _bitcoinRpcPort = 8334;
            _bitcoinRpcUrl = "localhost";
            _httpPort = 50001;
            _tlsPort = 50002;
        }};
    }

    @Before
    public void before() throws Exception {
        super.before();

        Logger.setLogLevel("com.softwareverde.network.socket", LogLevel.INFO);
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    // @Test
    public void should_create_electrum_block_header_merkle_root() {
        // Setup
        final ElectrumModule electrumModule = new ElectrumModule(_electrumProperties);

        final Sha256Hash expectedMerkleRoot = Sha256Hash.fromHexString("900215AEBA9EFC8D04FD2706F98C345CCD01E7EF92D160E5CCCB575F7CB19835");

        // Action
        final MerkleTree<BlockHeader> merkleTree = electrumModule._calculateCheckpointMerkle(700261L);

        // Assert
        Assert.assertEquals(expectedMerkleRoot, merkleTree.getMerkleRoot());
    }

    @Test
    public void should_list_available_outputs() {
        // Setup
        final Json request = Json.parse("{\"method\":\"blockchain.scripthash.get_history\",\"id\":82,\"params\":[\"e4ccc4c5c542ffdd2b7610dbc076536f5c4d15e7ed8c5764593a16aff882e11e\"]}");
        final ElectrumModule electrumModule = new ElectrumModule(_electrumProperties);

        final JsonSocket jsonSocket = new JsonSocket(new FakeSocket(), null) {
            @Override
            public Boolean write(final ProtocolMessage outboundMessage) {
                return true;
            }
        };

        // Action
        electrumModule._handleGetUnspentOutputs(jsonSocket, request);

        // Assert
    }
}
