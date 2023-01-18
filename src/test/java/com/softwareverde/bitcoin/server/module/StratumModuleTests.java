package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.rpc.BitcoinMiningRpcConnector;
import com.softwareverde.bitcoin.rpc.BitcoinMiningRpcConnectorFactory;
import com.softwareverde.bitcoin.rpc.BitcoinVerdeRpcConnector;
import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.configuration.Configuration;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.stratum.task.StratumUtil;
import com.softwareverde.bitcoin.stratum.BitcoinCoreStratumServer;
import com.softwareverde.bitcoin.stratum.socket.StratumServerSocket;
import com.softwareverde.bitcoin.stratum.task.StratumMineBlockTask;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.ReflectionUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.Socket;

public class StratumModuleTests extends UnitTest {
    @Before
    public void before() throws Exception {
        super.before();
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_mine_valid_prototype_block() {
        // Setup
        final BitcoinVerdeStratumServerPartialMock stratumServer = new BitcoinVerdeStratumServerPartialMock();
        stratumServer.setCoinbaseAddress((new AddressInflater()).fromBase58Check("12c6DSiU4Rq3P4ZxziKxzrL5LmMBrzjrJX"));

        stratumServer.queueFakeJsonResponse(Json.parse("{\"blockHeight\":1478548,\"errorMessage\":null,\"block\":\"04000000D6D6713A14CDA33142810937DE7C91B66FFE9DD559EC317DF201000000000000E8F14BCBA6C1EB326B3F3E01F26F8680996D87B96856CDD0C3A95061822E15685160BA61957A021A000000000402000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF2B03948F161D2F706F6F6C2E626974636F696E76657264652E6F72672F56455244452F080000000000000000FFFFFFFF01C1125402000000001976A91491B24BF9F5288532960AC687ABB035127B1D28A588AC0000000001000000013738AE82437E2AFCA4557303D374A90FE0B33CEE031544AB63F8E1310D7DDDB5010000006441AC0CBAA97A44BD5AC208BC144B502CCD893E29E8CEAF5B37F017D280B10DB85401AE4BC17E3E6C4DB527645B9D206B298AFD330C4D95A7748A51D3394BFC6D9C412102FFD4203A55C849BE7B2D328AB0D49452ECDBC12E69E41202566A6576D26C6B21FEFFFFFF0280969800000000001976A914A9A520E12E256B01B916BEA95A721B62893A0C4888AC3C6E8A20000000001976A914715F304302D42E2EAE2CF34734EA6E1D0DACA59F88AC938F1600010000000792766D8F688FA927706D6F31239929323F422BF041E020DE880CDD9A6349A50600000000644151717BA1065560FD02E423252F4F523D73F5FCBF9F3BA0A41C272CCDDD83703980A23D18CC4D8349333BF4C307AFA491912879162F0B94D512905B40A856DABD412102FFD4203A55C849BE7B2D328AB0D49452ECDBC12E69E41202566A6576D26C6B21FEFFFFFF1D463D8530CCD2CDD744CF436ADE4036A8908EDB022023DA82E2DAB3BE41BA0D00000000644105AE053C5F8EC93E4BC38916CAFF8510EB6C704C054047335EC11217098FFAAFCE444791A3E22450FCDDF5B96FF350307B9CE07B72F34DCCB68644E4E319DD5A412102FFD4203A55C849BE7B2D328AB0D49452ECDBC12E69E41202566A6576D26C6B21FEFFFFFF86B90B6030CDA688935C0A0C63FE14CC4AA5A02DE5E8B61428C9D9BFFB67B6160000000064414DDBE828F45565EA234177A061C5380DFEB45D44E3EE7B915217AAAEF844E52FEF4D9812A2F0D731712F8CD5C198EF91797799ED4607D9D43C77EF86033EF0AE412102FFD4203A55C849BE7B2D328AB0D49452ECDBC12E69E41202566A6576D26C6B21FEFFFFFF825986959B7BCFFC7761724816FE21A8A22BEC917ACCD32BD9AE2E52EA9D11200000000064414B770BD030130448BDC3D7D7779136442939617C12D3CC7F082D6F76D4A8F0942CC03E15888D1BD86BA8B0A35EB79DF4C0B1186A509782241F2D6BCD21FE6A52412102FFD4203A55C849BE7B2D328AB0D49452ECDBC12E69E41202566A6576D26C6B21FEFFFFFF492695F07D7783E2F4BBDA5AD79261DF66ED84BB11FBADC7701AC71114314390000000006441D0D865FAA9E85F2C380E063A58F7550D113C89DC8C1F96DED999B21595573E0556009471AC6DCD17A6A6F479623F2F39C7144599C57B4CD2E1C129B4D72FB5D1412102FFD4203A55C849BE7B2D328AB0D49452ECDBC12E69E41202566A6576D26C6B21FEFFFFFFEDAC3365588B249CD94250C83229BE8ECA1509BFD5C498C23CFAA591286283B2000000006441EE00B1EB2F2323D940A35EF381113C30E4110DB7439A05F5FEC819F7E1F95D15200A24CC879612B32290B9F1514F06FCF244CB39D91D7FB700700F0228DB061B412102FFD4203A55C849BE7B2D328AB0D49452ECDBC12E69E41202566A6576D26C6B21FEFFFFFF123E6BCF42E6E72B35152762E3F0E10C8A4E6252C76B61E42FA288BA6F8E4BF700000000644126590B0502DC8D7E7669C19FA90678A07336A75E47749DE179A1203BC0394502F72E0EF8BACBA36453627068F689682E1B44D4902715B6FEB1FDB4A9D4CE0CED412102FFD4203A55C849BE7B2D328AB0D49452ECDBC12E69E41202566A6576D26C6B21FEFFFFFF0280C3C901000000001976A914A9A520E12E256B01B916BEA95A721B62893A0C4888ACE8052321000000001976A914715F304302D42E2EAE2CF34734EA6E1D0DACA59F88AC938F160001000000019E1008F71C6786309AE3A8B9AF276DA6F66CCA0D6645C9F850CED4382A762862010000006441FFCB1582D8AD9B441F05BBB780E281FA0B3E4A8249F8D7103AEA1A2562D1D111414AC4DBEB031BB9D003902C1A19390EBF189BA4F9480128FB939714935F80C4412102FFD4203A55C849BE7B2D328AB0D49452ECDBC12E69E41202566A6576D26C6B21FEFFFFFF0280969800000000001976A914A9A520E12E256B01B916BEA95A721B62893A0C4888AC90D6F11F000000001976A914715F304302D42E2EAE2CF34734EA6E1D0DACA59F88AC938F1600\",\"wasSuccess\":1}"));
        stratumServer.queueFakeJsonResponse(Json.parse("{\"errorMessage\":null,\"wasSuccess\":1}"));

        stratumServer.start();
        final StratumMineBlockTask stratumMineBlockTask = stratumServer.buildMineBlockTask();

        // Action
        final Block block = stratumMineBlockTask.assembleBlock("00000000", "00000000", "00000000");

        // Assert
        final List<Transaction> transactions = block.getTransactions();
        Assert.assertEquals(4, transactions.getCount());

        // Enforce LTOR...
        // Assert.assertEquals(Sha256Hash.fromHexString("D4730FAF4054EC4A442A3FFA6570C8FEF1F94BFCD15775B932499947C127F8A6"), transactions.get(0).getHash());
        Assert.assertEquals(Sha256Hash.fromHexString("6228762A38D4CE50F8C945660DCA6CF6A66D27AFB9A8E39A3086671CF708109E"), transactions.get(1).getHash());
        Assert.assertEquals(Sha256Hash.fromHexString("B5DD7D0D31E1F863AB441503EE3CB3E00FA974D3037355A4FC2A7E4382AE3837"), transactions.get(2).getHash());
        Assert.assertEquals(Sha256Hash.fromHexString("D085E39992A2C6DC1FB27E89A8713EDAE28F724E67C52E84045193D26C22945E"), transactions.get(3).getHash());

        Assert.assertEquals(Sha256Hash.fromHexString("00000000000001F27D31EC59D59DFE6FB6917CDE3709814231A3CD143A71D6D6"), block.getPreviousBlockHash());
        Assert.assertEquals(0L, block.getTimestamp().longValue());
        Assert.assertEquals(Difficulty.decode(ByteArray.fromHexString("1A027A95")), block.getDifficulty());
        Assert.assertEquals(0L, block.getNonce().longValue());
        Assert.assertEquals(39064257L, block.getCoinbaseTransaction().getTransactionOutputs().get(0).getAmount().longValue());

        final UnlockingScript unlockingScript = block.getCoinbaseTransaction().getCoinbaseScript();
        final List<Operation> operations = unlockingScript.getOperations();

        final PushOperation firstOperation = (PushOperation) operations.get(0);
        final PushOperation secondOperation = (PushOperation) operations.get(1);
        final PushOperation lastOperation = ((PushOperation) operations.get(operations.getCount() - 1));
        Assert.assertEquals(Long.valueOf(1478548L), firstOperation.getValue().asLong()); // Enforce BlockHeight within coinbase...
        Assert.assertEquals(BitcoinConstants.getCoinbaseMessage(), secondOperation.getValue().asString()); // Enforce coinbase message...
        Assert.assertEquals(stratumMineBlockTask.getExtraNonce(), HexUtil.toHexString(lastOperation.getValue().getBytes(0, 4))); // Enforce extraNonce...
        Assert.assertEquals("00000000", HexUtil.toHexString(lastOperation.getValue().getBytes(4, 4))); // Enforce extraNonce2...
    }

    @Test
    public void should_reinflate_mined_share() {
        final String miningTaskMessage = "{\"method\":\"mining.notify\",\"id\":3351436,\"params\":[\"0000002C\",\"6C1EB2564E74701849B46B8FA6E9C2B504DB70B704C9EEDE0000000000000000\",\"010000000100000000000000000000000000000000000000000000000000000000000000000000000025036AB208172F706F6F6C2E626974636F696E76657264652E6F72672F08\",\"FFFFFFFF01A30A824A000000001976A91426CBB2966AC6AABC18135E101038D39FBAE3F2D688AC00000000\",[\"2FEDBE1A5D2D30D78DA7227B1538DAA84BDF39F52DAD30CD3B22F4A483A43A07\",\"D63AA3ECDFD93D43B4214FC5898C4A51A32A56B61B4B18A71E88895EAA87C1C5\",\"7597C5DA59EBC99644C2099045136C7DC7716602B136419A28D1DB2DCE03AAC4\",\"8A68336A9F7DA2FC26865123E6684F45C279AD074036E40A4C4EC42AC1EDC66A\",\"C1C731F64485BFC42761B53A1A48998870E07F4F3F4E708DE8FF5C2C3173FAA2\"],\"00000004\",\"18054EA4\",\"5C67A470\",true]}";
        final String miningSubmissionMessage = "{\"method\":\"mining.submit\",\"id\":3351435,\"params\":[\"makoinfused.worker0\",\"0000002C\",\"a9030000\",\"5C67A470\",\"d4fb12d7\"]}";

        final Json miningTaskParameters = Json.parse(miningTaskMessage).get("params");
        final String taskId = miningTaskParameters.getString(0);
        final Sha256Hash previousBlockHash = Sha256Hash.fromHexString(BitcoinUtil.reverseEndianString(StratumUtil.swabHexString(miningTaskParameters.getString(1))));
        final String coinbaseHead = miningTaskParameters.getString(2);
        final String coinbaseTail = miningTaskParameters.getString(3);
        final Json merkleBranchesJson = miningTaskParameters.get(4);
        final Long blockVersion = ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(miningTaskParameters.getString(5)));
        final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString(miningTaskParameters.getString(6)));
        final Long timestampInSeconds = ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(miningTaskParameters.getString(7)));
        final Boolean abandonOldJobs = miningTaskParameters.getBoolean(8);

        final Json miningSubmissionParameters = Json.parse(miningSubmissionMessage).get("params");
        final String submissionWorkerName = miningSubmissionParameters.getString(0);
        final ByteArray submissionTaskId = MutableByteArray.wrap(HexUtil.hexStringToByteArray(miningSubmissionParameters.getString(1)));
        final String submissionExtraNonce2 = miningSubmissionParameters.getString(2);
        final String submissionTimestamp = miningSubmissionParameters.getString(3);
        final String submissionNonce = miningSubmissionParameters.getString(4);

        final Transaction coinbaseTransaction;
        {
            final TransactionInflater transactionInflater = new TransactionInflater();
            final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
            byteArrayBuilder.appendBytes(HexUtil.hexStringToByteArray(coinbaseHead));
            byteArrayBuilder.appendBytes(HexUtil.hexStringToByteArray("42C93632"));
            byteArrayBuilder.appendBytes(HexUtil.hexStringToByteArray(submissionExtraNonce2));
            byteArrayBuilder.appendBytes(HexUtil.hexStringToByteArray(coinbaseTail));
            coinbaseTransaction = transactionInflater.fromBytes(byteArrayBuilder.build());
        }

        final MutableList<Sha256Hash> merkleBranches = new MutableArrayList<>();
        {
            for (int i = 0; i < merkleBranchesJson.length(); ++i) {
                merkleBranches.add(Sha256Hash.fromHexString(merkleBranchesJson.getString(i)));
            }
        }

        final MerkleRoot merkleRoot = StratumMineBlockTask.calculateMerkleRoot(coinbaseTransaction, merkleBranches);

        final MutableBlockHeader mutableBlockHeader = new MutableBlockHeader();
        mutableBlockHeader.setPreviousBlockHash(previousBlockHash);
        mutableBlockHeader.setMerkleRoot(merkleRoot);
        mutableBlockHeader.setNonce(ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(submissionNonce)));
        mutableBlockHeader.setTimestamp(ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(submissionTimestamp)));
        mutableBlockHeader.setDifficulty(difficulty);
        mutableBlockHeader.setVersion(blockVersion);
        Assert.assertEquals(Sha256Hash.fromHexString("00000000001AC3616DB5F502DE9DCB45EC7192A8643525A3EAEC025894E6D85F"), mutableBlockHeader.getHash());
    }
}

class FakeStratumServerSocket extends StratumServerSocket {

    public FakeStratumServerSocket() {
        super(null, null);
    }

    @Override
    public void start() {
        // Nothing.
    }
}

class BitcoinVerdeStratumServerPartialMock extends BitcoinCoreStratumServer {
    static class BitcoinVerdeRpcConnectorFactory implements BitcoinMiningRpcConnectorFactory {
        protected final MutableList<Json> _fakeJsonResponses = new MutableArrayList<>();

        public MutableList<Json> getFakeJsonResponses() {
            return _fakeJsonResponses;
        }

        @Override
        public BitcoinMiningRpcConnector newBitcoinMiningRpcConnector() {
            return new BitcoinVerdeRpcConnector(null, null) {
                @Override
                protected NodeJsonRpcConnection _getRpcConnection() {
                    return new NodeJsonRpcConnection(new Socket(), null) {
                        @Override
                        protected Json _executeJsonRequest(final Json rpcRequestJson) {
                            System.out.println("Stratum Sent: " + rpcRequestJson);

                            final Json jsonResponse = (_fakeJsonResponses.isEmpty() ? null : _fakeJsonResponses.remove(0));
                            System.out.println("Stratum Received: " + jsonResponse);
                            return jsonResponse;
                        }
                    };
                }
            };
        }
    };

    protected static Configuration CONFIGURATION;

    static {
        try {
            final File tempFile = File.createTempFile("tmp", ".dat");
            tempFile.deleteOnExit();
            CONFIGURATION = new Configuration(tempFile);
        }
        catch (final Exception exception) {
            exception.printStackTrace();
        }
    }

    protected final MutableList<Json> _fakeJsonResponses;

    public BitcoinVerdeStratumServerPartialMock() {
        super(
            new BitcoinVerdeRpcConnectorFactory(),
            CONFIGURATION.getStratumProperties().getPort(),
            new CachedThreadPool(1, 1L),
            new CoreInflater()
        );

        _fakeJsonResponses = ((BitcoinVerdeRpcConnectorFactory) _rpcConnectionFactory).getFakeJsonResponses();
        ((CachedThreadPool) _threadPool).start();

        ReflectionUtil.setValue(this, "_stratumServerSocket", new FakeStratumServerSocket());
    }

    public void queueFakeJsonResponse(final Json json) {
        _fakeJsonResponses.add(json);
    }

    public StratumMineBlockTask buildMineBlockTask() {
        return _buildNewMiningTask(1L);
    }
}