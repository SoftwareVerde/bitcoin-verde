package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.server.configuration.Configuration;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.stratum.BitcoinVerdeStratumServer;
import com.softwareverde.bitcoin.server.stratum.socket.StratumServerSocket;
import com.softwareverde.bitcoin.server.stratum.task.MutableStratumMineBlockTaskBuilder;
import com.softwareverde.bitcoin.server.stratum.task.StratumMineBlockTask;
import com.softwareverde.bitcoin.server.stratum.task.StratumUtil;
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
        stratumServer.setValidatePrototypeBlockBeforeMining(false);

        stratumServer.queueFakeJsonResponse(Json.parse("{\"blockHeaders\":[\"00000020491C708BADFD38F0B38A6EDD6FDD949C9A4D109C037EAB010000000000000000093CC8AEF901A2BF304DC6949439AAC6942F75BE376E14E7C38EEA8F0D2B696360C6555C3C9B051848B94556\"],\"errorMessage\":null,\"wasSuccess\":1}"));
        stratumServer.queueFakeJsonResponse(Json.parse("{\"blockHeight\":568009,\"blockHeaderHeight\":568009,\"errorMessage\":null,\"wasSuccess\":1}"));
        stratumServer.queueFakeJsonResponse(Json.parse("{\"difficulty\":\"180597F0\",\"errorMessage\":null,\"wasSuccess\":1}"));
        stratumServer.queueFakeJsonResponse(Json.parse("{\"errorMessage\":null,\"blockReward\":1250000000,\"wasSuccess\":1}"));
        stratumServer.queueFakeJsonResponse(Json.parse("{\"unconfirmedTransactions\":[{\"transactionFee\":243,\"transactionData\":\"0100000001D0ADDDF36837842BF09B8C699DABB01923D6CA5224975863880637E4BFA355ED010000006A47304402203AF85025DBC1EC2318C9C463041502F61115B7BB002373DD6A88A2E3D7ED2D01022001C6E527F76508CA9C76ACF7EDBF5B00049BA1A96CD77A20EEBAB41D35AC4CF1412102F7A672CD7516D4E76D34D68DB1492DD5DD7121DDBD6FAE6FAC49F431BF36E26AFFFFFFFF0289CE0200000000001976A9147A12CF7834A18154377FD50A38C78E7725C0486F88ACAA3B0300000000001976A9148B80501D56D20C1BF8AA94F25CE9EAFADE545E0E88AC00000000\"},{\"transactionFee\":390,\"transactionData\":\"010000000226D4509F9709D74EE7CB4EEE608280CCAC94CB5B259B2DC9C8083A81A42189CD000000006A47304402207FBB1AA623F2F1DB9EE78666BF8150C74883494E5F60AA88E88162AED3D5AAD002204C4E933B791E1332A4518FA2B19B1FC941E6A7C761C97A0296BB013A72A0D314412102353C7984935825F70C60ECF0543892CEFED310B55629E1E5C9A4D23685DD7DE5FFFFFFFF6DEC6DB7021E83B5470D73263854F5B7A609AFB21EF05F2498A418C98F18226B010000006B483045022100DB11AF4F4731D8B453E87BCEED56A28DBFE35FBFE99666B2B8FE546BED32828802207F7A89655E24D60FA8189B4D9EE049590616C3AB8B5D0E588BB272C03D543D8C412103788A855A936EA41414713D0D4F83B20DE49B9D892D4BB3AB52AED8EA2A9E32A1FFFFFFFF021DB90900000000001976A9148B80506A2D60464710BD359AC82400C2BF23E5FE88AC260B0600000000001976A914DB77613B90CA4FFC8DF46422B0021854B9B4C9AE88AC00000000\"},{\"transactionFee\":280,\"transactionData\":\"0100000001970E0DEF3A463DB2CD124FCE1CEC2B0A09129B4D7DC50F1C51A3BB9EB2B4A410000000006B483045022100883CBCBC0781CCF1F4DA8C40E7887B4E863661CC11A83C9C34C0F028AA2150E0022053B2F7DB5362D22BBC7857600B594834F6D8440081784481FE87993A4566772741210337433E3CD5B7B46006D7DF31261FB1EC8679E116072357FDDC05D491E05E7ADFFFFFFFFF020000000000000000216A040101010104343339301501DB673B6C83CF1BEB747A8F5C2AE7FBC50F6A0D001A290200000000001976A9149A43A4319077B6DB6B3F8EC27E756A75F41672CC88AC00000000\"}],\"errorMessage\":null,\"wasSuccess\":1}"));
        stratumServer.queueFakeJsonResponse(Json.parse("{\"errorMessage\":\"\",\"wasSuccess\":0}")); // Fail the ADD_HOOK upgrade since the socket is not real...

        final MutableStratumMineBlockTaskBuilder stratumMineBlockTaskBuilder = stratumServer.createStratumMineBlockTask();
        final StratumMineBlockTask stratumMineBlockTask = stratumMineBlockTaskBuilder.buildMineBlockTask();

        // Action
        final Block block = stratumMineBlockTask.assembleBlock("00000000", "00000000", "00000000");

        // Assert
        final List<Transaction> transactions = block.getTransactions();
        Assert.assertEquals(4, transactions.getCount());

        // Enforce LTOR...
        Assert.assertEquals(Sha256Hash.fromHexString("09DB13063DF69B27786D01A7397A46D46FE3D780A0BDDE433CDC89DA7DBFFAF8"), transactions.get(1).getHash());
        Assert.assertEquals(Sha256Hash.fromHexString("C44A58ECAC5A526218BE2AC26F246C0880E7C14C3FD82DB4ED074B63D948F83F"), transactions.get(2).getHash());
        Assert.assertEquals(Sha256Hash.fromHexString("ED55A3BFE43706886358972452CAD62319B0AB9D698C9BF02B843768F3DDADD0"), transactions.get(3).getHash());

        Assert.assertEquals(Sha256Hash.fromHexString("0000000000000000031D4DC02DF126D9C1130EAC699BC4C8E3F70767042FE72D"), block.getPreviousBlockHash());
        Assert.assertEquals(0L, block.getTimestamp().longValue());
        Assert.assertEquals(Difficulty.decode(ByteArray.fromHexString("180597F0")), block.getDifficulty());
        Assert.assertEquals(0L, block.getNonce().longValue());
        Assert.assertEquals((1250000000L + 243L + 390L + 280L), block.getCoinbaseTransaction().getTransactionOutputs().get(0).getAmount().longValue());

        final UnlockingScript unlockingScript = block.getCoinbaseTransaction().getCoinbaseScript();
        final List<Operation> operations = unlockingScript.getOperations();
        Assert.assertEquals(Long.valueOf(568010L), ((PushOperation) operations.get(0)).getValue().asLong()); // Enforce BlockHeight within coinbase...
        Assert.assertEquals(BitcoinConstants.getCoinbaseMessage(), ((PushOperation) operations.get(1)).getValue().asString()); // Enforce coinbase message...
        Assert.assertEquals(stratumMineBlockTask.getExtraNonce(), HexUtil.toHexString(((PushOperation) operations.get(2)).getValue().getBytes(0, 4))); // Enforce extraNonce...
        Assert.assertEquals("00000000", HexUtil.toHexString(((PushOperation) operations.get(2)).getValue().getBytes(4, 4))); // Enforce extraNonce2...
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

        final MutableList<String> merkleBranches = new MutableList<String>();
        {
            for (int i = 0; i < merkleBranchesJson.length(); ++i) {
                merkleBranches.add(merkleBranchesJson.getString(i));
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

class BitcoinVerdeStratumServerPartialMock extends BitcoinVerdeStratumServer {
    protected static Configuration configuration;

    static {
        try {
            final File tempFile = File.createTempFile("tmp", ".dat");
            tempFile.deleteOnExit();
            configuration = new Configuration(tempFile);
        }
        catch (final Exception exception) {
            exception.printStackTrace();
        }
    }

    protected final MutableList<Json> _fakeJsonResponses = new MutableList<Json>();

    public BitcoinVerdeStratumServerPartialMock() {
        super(configuration.getStratumProperties(), new CachedThreadPool(1, 1L));
        ((CachedThreadPool) _threadPool).start();

        ReflectionUtil.setValue(this, "_stratumServerSocket", new FakeStratumServerSocket());
    }

    @Override
    protected NodeJsonRpcConnection _getNodeJsonRpcConnection() {
        final Socket javaSocket = new Socket(); // Dummy socket, not connected to anything.
        return new NodeJsonRpcConnection(javaSocket, null) {
            @Override
            protected Json _executeJsonRequest(final Json rpcRequestJson) {
                System.out.println("Stratum Sent: " + rpcRequestJson.toString());

                final Json jsonResponse = _fakeJsonResponses.remove(0);
                System.out.println("Stratum Received: " + jsonResponse.toString());
                return jsonResponse;
            }
        };
    }

    public void queueFakeJsonResponse(final Json json) {
        _fakeJsonResponses.add(json);
    }

    public MutableStratumMineBlockTaskBuilder createStratumMineBlockTask() {
        _rebuildNewMiningTask();
        return _stratumMineBlockTaskBuilder;
    }
}