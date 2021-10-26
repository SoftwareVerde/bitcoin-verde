package com.softwareverde.bitcoin.server.module.stratum;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTree;
import com.softwareverde.bitcoin.server.configuration.ElectrumProperties;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

public class ElectrumModuleTests extends UnitTest {
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
        final ElectrumProperties electrumProperties = new ElectrumProperties() {{
            _bitcoinRpcPort = 8334;
            _bitcoinRpcUrl = "localhost";
            _httpPort = 50001;
            _tlsPort = 50002;
        }};
        final ElectrumModule electrumModule = new ElectrumModule(electrumProperties);

        final Sha256Hash expectedMerkleRoot = Sha256Hash.fromHexString("900215AEBA9EFC8D04FD2706F98C345CCD01E7EF92D160E5CCCB575F7CB19835");

        // Action
        final MerkleTree<BlockHeader> merkleTree = electrumModule._calculateCheckpointMerkle(700261L);

        // Assert
        Assert.assertEquals(expectedMerkleRoot, merkleTree.getMerkleRoot());
    }
}
