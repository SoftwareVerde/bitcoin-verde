package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MerkleBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.merkleroot.MutableMerkleRoot;
import com.softwareverde.bitcoin.server.message.type.bloomfilter.set.SetTransactionBloomFilterMessage;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.MerkleBlockParameters;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Container;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.IoUtil;
import org.junit.Assert;
import org.junit.Test;

public class PartialMerkleTreeTests {

    public void downloadPartialMerkleTree() throws Exception {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final String blockData = IoUtil.getResource("/blocks/0000000000000000012F011B29194439757A67186A54C2614978F0D6192570F2");
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));

        final MutableBloomFilter bloomFilter = MutableBloomFilter.newInstance(128L, 0.01D, 0L);

        int i = 0;
        for (final Transaction transaction : block.getTransactions()) {
            if ( (i == 3) || (i == 4) ) {
                for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    bloomFilter.addItem(transactionOutputIdentifier.toBytes());
                }
            }
            i += 1;
        }

        final BitcoinNode bitcoinNode = new BitcoinNode("btc.softwareverde.com", 8333, new LocalNodeFeatures() {
            @Override
            public NodeFeatures getNodeFeatures() {
                final NodeFeatures nodeFeatures = new NodeFeatures();
                nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                nodeFeatures.enableFeature(NodeFeatures.Feature.BLOOM_CONNECTIONS_ENABLED);
                return nodeFeatures;
            }
        });

        bitcoinNode.connect();

        final CoreInflater coreInflater = new CoreInflater();
        final SetTransactionBloomFilterMessage bloomFilterMessage = new SetTransactionBloomFilterMessage(coreInflater);
        bloomFilterMessage.setBloomFilter(bloomFilter);
        bitcoinNode.queueMessage(bloomFilterMessage);

        Thread.sleep(1000L);
        bitcoinNode.requestMerkleBlock(block.getHash(), null);
        Thread.sleep(3000L);

        bitcoinNode.disconnect();
    }

    @Test
    public void should_inflate_partial_merkle_tree() {
        // Block: 0000000000000000012F011B29194439757A67186A54C2614978F0D6192570F2
        // t0, t1, t2, t3, t4, t5   (1111111111110000)
        // 069511B719C8BDAFF18994EE682A0F62DAAECFFC0F7E0EB1D61367A81B3C4DA5, 82E98803F0148138DCCBDE3720BA407249EA269D4BEF3E0AC86294EB35EFAF48, 840A2056D8BB045FB9025C8ABC8813ADFCE443B6F4E63AF02E41EE2DA4FB76A1, 9F8FCFFDA780929FE069B445BA302064EB2BC30CCEA0C8B3D46FB37D1A2FB487, BF8DC762675906E5C862CF607886573E600FD4A7B244D49515C12558CAFD213A, E73CB816E35F15D1391D7FD27650511A9AF6879EA8F01AA44ED29C08CDCC388F : FFF0 (1111111111110000)
        // t0                       (        11110000)
        // 069511B719C8BDAFF18994EE682A0F62DAAECFFC0F7E0EB1D61367A81B3C4DA5, 82E98803F0148138DCCBDE3720BA407249EA269D4BEF3E0AC86294EB35EFAF48, 37AC5F25240165D9041E9E7F3B4A353D982845F16E756C6584FE83E0F0A1B571, B243F473C8E957707E415408D0A3EC4DE093E7A3D1D7D5FD5D558A1DA3921A14 : F0 (11110000)
        // t0 and t3                (        11110101)
        // 069511B719C8BDAFF18994EE682A0F62DAAECFFC0F7E0EB1D61367A81B3C4DA5, 82E98803F0148138DCCBDE3720BA407249EA269D4BEF3E0AC86294EB35EFAF48, 840A2056D8BB045FB9025C8ABC8813ADFCE443B6F4E63AF02E41EE2DA4FB76A1, 9F8FCFFDA780929FE069B445BA302064EB2BC30CCEA0C8B3D46FB37D1A2FB487, B243F473C8E957707E415408D0A3EC4DE093E7A3D1D7D5FD5D558A1DA3921A14 : 00F5 (11110101)
        // t5                       (        10110100)
        // CE3C30E242B93478F75E55B4BFE71D5476711FE57C3E8A097C8F2A44B8E9FC50, BF8DC762675906E5C862CF607886573E600FD4A7B244D49515C12558CAFD213A, E73CB816E35F15D1391D7FD27650511A9AF6879EA8F01AA44ED29C08CDCC388F : B4 (10110100)
        // t4                       (        10111000)
        // CE3C30E242B93478F75E55B4BFE71D5476711FE57C3E8A097C8F2A44B8E9FC50, BF8DC762675906E5C862CF607886573E600FD4A7B244D49515C12558CAFD213A, E73CB816E35F15D1391D7FD27650511A9AF6879EA8F01AA44ED29C08CDCC388F : B8 (10111000)
        // t3, t4                   (1101011110000000)
        // D3BCEB689660BFDB8CE7DCBACBD9884EB6FB41D176FCC61E00C248B496432C6C, 840A2056D8BB045FB9025C8ABC8813ADFCE443B6F4E63AF02E41EE2DA4FB76A1, 9F8FCFFDA780929FE069B445BA302064EB2BC30CCEA0C8B3D46FB37D1A2FB487, BF8DC762675906E5C862CF607886573E600FD4A7B244D49515C12558CAFD213A, E73CB816E35F15D1391D7FD27650511A9AF6879EA8F01AA44ED29C08CDCC388F : D780 (1101011110000000)

        // 0                 _0
        // 1        _0       |        _1
        // 2   _0   |   _1   |   _2   |   xx
        // 3 t0  t1 | t2  t3 | t4  t5 | xx  xx

        // 0_0 = 8A0DCDB807FA3933F293504B12E6AAED309DAB080A491ACBBDF335CC749D98F4
        // 1_0 = CE3C30E242B93478F75E55B4BFE71D5476711FE57C3E8A097C8F2A44B8E9FC50
        // 1_1 = B243F473C8E957707E415408D0A3EC4DE093E7A3D1D7D5FD5D558A1DA3921A14
        // 2_0 = D3BCEB689660BFDB8CE7DCBACBD9884EB6FB41D176FCC61E00C248B496432C6C
        // 2_1 = 37AC5F25240165D9041E9E7F3B4A353D982845F16E756C6584FE83E0F0A1B571
        // 2_2 = 7F66FE7A5DB988FB7B8DC479F35C368147069F6CF9483EBF5C4B1EFC6F8683EB
        // 2_3 = 0000000000000000000000000000000000000000000000000000000000000000
        // 3_0 = 069511B719C8BDAFF18994EE682A0F62DAAECFFC0F7E0EB1D61367A81B3C4DA5
        // 3_1 = 82E98803F0148138DCCBDE3720BA407249EA269D4BEF3E0AC86294EB35EFAF48
        // 3_2 = 840A2056D8BB045FB9025C8ABC8813ADFCE443B6F4E63AF02E41EE2DA4FB76A1
        // 3_3 = 9F8FCFFDA780929FE069B445BA302064EB2BC30CCEA0C8B3D46FB37D1A2FB487
        // 3_4 = BF8DC762675906E5C862CF607886573E600FD4A7B244D49515C12558CAFD213A
        // 3_5 = E73CB816E35F15D1391D7FD27650511A9AF6879EA8F01AA44ED29C08CDCC388F
        // 3_6 = 0000000000000000000000000000000000000000000000000000000000000000
        // 3_7 = 0000000000000000000000000000000000000000000000000000000000000000

        final BlockInflater blockInflater = new BlockInflater();
        final String blockData = IoUtil.getResource("/blocks/0000000000000000012F011B29194439757A67186A54C2614978F0D6192570F2");
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));

        final ImmutableListBuilder<Sha256Hash> merkleHashes = new ImmutableListBuilder<>(block.getTransactionCount());
        for (final String merkleHashString : new String[]{ "D3BCEB689660BFDB8CE7DCBACBD9884EB6FB41D176FCC61E00C248B496432C6C", "840A2056D8BB045FB9025C8ABC8813ADFCE443B6F4E63AF02E41EE2DA4FB76A1", "9F8FCFFDA780929FE069B445BA302064EB2BC30CCEA0C8B3D46FB37D1A2FB487", "BF8DC762675906E5C862CF607886573E600FD4A7B244D49515C12558CAFD213A", "E73CB816E35F15D1391D7FD27650511A9AF6879EA8F01AA44ED29C08CDCC388F" }) {
            final Sha256Hash merkleHash = Sha256Hash.fromHexString(merkleHashString);
            merkleHashes.add(merkleHash);
        }
        final String flagString = "D780";

        final PartialMerkleTree partialMerkleTree = PartialMerkleTree.build(block.getTransactionCount(), merkleHashes.build(), MutableByteArray.wrap(HexUtil.hexStringToByteArray(flagString)));
        final MerkleRoot merkleRoot = partialMerkleTree.getMerkleRoot();

        Assert.assertEquals(block.getMerkleRoot(), merkleRoot);

        final MutableBloomFilter bloomFilter = MutableBloomFilter.newInstance(128L, 0.01D, 0L);
        {
            int i = 0;
            for (final Transaction transaction : block.getTransactions()) {
                if ((i == 3) || (i == 4)) {
                    for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                        final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                        bloomFilter.addItem(transactionOutputIdentifier.toBytes());
                    }
                }
                i += 1;
            }
        }

        final PartialMerkleTree generatedPartialMerkleTree = block.getPartialMerkleTree(bloomFilter);
        final PartialMerkleTree reinflatedPartialMerkleTree = PartialMerkleTree.build(block.getTransactionCount(), generatedPartialMerkleTree.getHashes(), generatedPartialMerkleTree.getFlags());
        Assert.assertEquals(block.getMerkleRoot(), reinflatedPartialMerkleTree.getMerkleRoot());
    }

    @Test
    public void should_calculate_merkle_root_hash_with_only_two_items() {
        // Setup
        final MerkleRoot expectedMerkleRoot = MutableMerkleRoot.fromHexString("352144A8F453171A9024C0B23979D521B0C98A03DB6D08E4BC5B0B9554FCEAFA");

        final MutableList<Sha256Hash> transactionHashes = new MutableArrayList<>(2);
        transactionHashes.add(Sha256Hash.fromHexString("1D04683045280CC3046880153EB0CDD9B352A2E110409A3302363218F1628DCA"));
        transactionHashes.add(Sha256Hash.fromHexString("BF80D99DE8E1801C5EC93DCA35E03586151691C461229301EF4C3F278079CBB1"));

        final PartialMerkleTree partialMerkleTree = new PartialMerkleTree(2, transactionHashes, ByteArray.fromHexString("A0"));

        // Action
        final MerkleRoot merkleRoot = partialMerkleTree.getMerkleRoot();

        // Assert
        Assert.assertEquals(expectedMerkleRoot, merkleRoot);
    }

    // @Test
    public void downloadPartialMerkleTreeAndTest() throws Exception {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final String blockData = IoUtil.getResource("/blocks/000000000000000082CCF8F1557C5D40B21EDABB18D2D691CFBF87118BAC7254");
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));

        final MutableBloomFilter bloomFilter = MutableBloomFilter.newInstance(128L, 0.01D, 0L);

        for (final Transaction transaction : block.getTransactions()) {
            if ( ((int) (Math.random() * 3)) == 0 ) {
                for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    bloomFilter.addItem(transactionOutputIdentifier.toBytes());
                }
            }
        }

        final BitcoinNode bitcoinNode = new BitcoinNode("btc.softwareverde.com", 8333, new LocalNodeFeatures() {
            @Override
            public NodeFeatures getNodeFeatures() {
                final NodeFeatures nodeFeatures = new NodeFeatures();
                nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                nodeFeatures.enableFeature(NodeFeatures.Feature.BLOOM_CONNECTIONS_ENABLED);
                return nodeFeatures;
            }
        });

        bitcoinNode.connect();

        final CoreInflater coreInflater = new CoreInflater();
        final SetTransactionBloomFilterMessage bloomFilterMessage = new SetTransactionBloomFilterMessage(coreInflater);
        bloomFilterMessage.setBloomFilter(bloomFilter);
        bitcoinNode.queueMessage(bloomFilterMessage);

        final Container<MerkleBlock> merkleBlockContainer = new Container<>();
        Thread.sleep(1000L);
        bitcoinNode.requestMerkleBlock(block.getHash(), new BitcoinNode.DownloadMerkleBlockCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final MerkleBlockParameters merkleBlockParameters) {
                merkleBlockContainer.value = merkleBlockParameters.getMerkleBlock();
            }
        });
        Thread.sleep(3000L);

        bitcoinNode.disconnect();

        final List<Sha256Hash> merkleHashes = merkleBlockContainer.value.getPartialMerkleTree().getHashes();
        final ByteArray flags = merkleBlockContainer.value.getPartialMerkleTree().getFlags();

        final PartialMerkleTree partialMerkleTree = PartialMerkleTree.build(block.getTransactionCount(), merkleHashes, flags);
        final MerkleRoot merkleRoot = partialMerkleTree.getMerkleRoot();

        Assert.assertEquals(block.getMerkleRoot(), merkleRoot);

        final PartialMerkleTree generatedPartialMerkleTree = block.getPartialMerkleTree(bloomFilter);
        final PartialMerkleTree reinflatedPartialMerkleTree = PartialMerkleTree.build(block.getTransactionCount(), generatedPartialMerkleTree.getHashes(), generatedPartialMerkleTree.getFlags());
        Assert.assertEquals(block.getMerkleRoot(), reinflatedPartialMerkleTree.getMerkleRoot());
    }

    @Test
    public void should_produce_correct_merkle_proof() {
        // taken from https://electrumx.readthedocs.io/en/latest/protocol-methods.html#cp-height-example
        final MutableList<String> blockHeaders = new MutableArrayList<>();
        blockHeaders.add("0100000000000000000000000000000000000000000000000000000000000000000000003ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a29ab5f49ffff001d1dac2b7c");
        blockHeaders.add("010000006fe28c0ab6f1b372c1a6a246ae63f74f931e8365e15a089c68d6190000000000982051fd1e4ba744bbbe680e1fee14677ba1a3c3540bf7b1cdb606e857233e0e61bc6649ffff001d01e36299");
        blockHeaders.add("010000004860eb18bf1b1620e37e9490fc8a427514416fd75159ab86688e9a8300000000d5fdcc541e25de1c7a5addedf24858b8bb665c9f36ef744ee42c316022c90f9bb0bc6649ffff001d08d2bd61");
        blockHeaders.add("01000000bddd99ccfda39da1b108ce1a5d70038d0a967bacb68b6b63065f626a0000000044f672226090d85db9a9f2fbfe5f0f9609b387af7be5b7fbb7a1767c831c9e995dbe6649ffff001d05e0ed6d");
        blockHeaders.add("010000004944469562ae1c2c74d9a535e00b6f3e40ffbad4f2fda3895501b582000000007a06ea98cd40ba2e3288262b28638cec5337c1456aaf5eedc8e9e5a20f062bdf8cc16649ffff001d2bfee0a9");
        blockHeaders.add("0100000085144a84488ea88d221c8bd6c059da090e88f8a2c99690ee55dbba4e00000000e11c48fecdd9e72510ca84f023370c9a38bf91ac5cae88019bee94d24528526344c36649ffff001d1d03e477");
        blockHeaders.add("01000000fc33f596f822a0a1951ffdbf2a897b095636ad871707bf5d3162729b00000000379dfb96a5ea8c81700ea4ac6b97ae9a9312b2d4301a29580e924ee6761a2520adc46649ffff001d189c4c97");
        blockHeaders.add("010000008d778fdc15a2d3fb76b7122a3b5582bea4f21f5a0c693537e7a03130000000003f674005103b42f984169c7d008370967e91920a6a5d64fd51282f75bc73a68af1c66649ffff001d39a59c86");
        blockHeaders.add("010000004494c8cf4154bdcc0720cd4a59d9c9b285e4b146d45f061d2b6c967100000000e3855ed886605b6d4a99d5fa2ef2e9b0b164e63df3c4136bebf2d0dac0f1f7a667c86649ffff001d1c4b5666");

        final MerkleTreeNode<BlockHeader> blockHeaderMerkleTree = new MerkleTreeNode<BlockHeader>();
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        for (int i=0; i<blockHeaders.getCount(); i++) {
            final String header = blockHeaders.get(i);
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray(header));
            blockHeaderMerkleTree.addItem(blockHeader);
        }

        final MutableList<Sha256Hash> expectedBranch = new MutableArrayList<>();
        expectedBranch.add(Sha256Hash.fromHexString("000000004ebadb55ee9096c9a2f8880e09da59c0d68b1c228da88e48844a1485"));
        expectedBranch.add(Sha256Hash.fromHexString("96cbbc84783888e4cc971ae8acf86dd3c1a419370336bb3c634c97695a8c5ac9"));
        expectedBranch.add(Sha256Hash.fromHexString("965ac94082cebbcffe458075651e9cc33ce703ab0115c72d9e8b1a9906b2b636"));
        expectedBranch.add(Sha256Hash.fromHexString("89e5daa6950b895190716dd26054432b564ccdc2868188ba1da76de8e1dc7591"));

        final List<Sha256Hash> merkleBranch = blockHeaderMerkleTree.getPartialTree(5); // index 6 = height 5
        Assert.assertEquals(expectedBranch.getCount(), merkleBranch.getCount());
        for (int i=0; i<expectedBranch.getCount(); i++) {
            Assert.assertEquals(expectedBranch.get(i).toString(), merkleBranch.get(i).toString());
        }
    }

    @Test
    public void should_produce_correct_merkle_proof2() {
        final MutableList<String> blockHeaders = new MutableArrayList<>();
        blockHeaders.add("0100000000000000000000000000000000000000000000000000000000000000000000003ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a29ab5f49ffff001d1dac2b7c");
        blockHeaders.add("010000006fe28c0ab6f1b372c1a6a246ae63f74f931e8365e15a089c68d6190000000000982051fd1e4ba744bbbe680e1fee14677ba1a3c3540bf7b1cdb606e857233e0e61bc6649ffff001d01e36299");
        blockHeaders.add("010000004860eb18bf1b1620e37e9490fc8a427514416fd75159ab86688e9a8300000000d5fdcc541e25de1c7a5addedf24858b8bb665c9f36ef744ee42c316022c90f9bb0bc6649ffff001d08d2bd61");
        blockHeaders.add("01000000bddd99ccfda39da1b108ce1a5d70038d0a967bacb68b6b63065f626a0000000044f672226090d85db9a9f2fbfe5f0f9609b387af7be5b7fbb7a1767c831c9e995dbe6649ffff001d05e0ed6d");
        blockHeaders.add("010000004944469562ae1c2c74d9a535e00b6f3e40ffbad4f2fda3895501b582000000007a06ea98cd40ba2e3288262b28638cec5337c1456aaf5eedc8e9e5a20f062bdf8cc16649ffff001d2bfee0a9");
        //blockHeaders.add("0100000085144a84488ea88d221c8bd6c059da090e88f8a2c99690ee55dbba4e00000000e11c48fecdd9e72510ca84f023370c9a38bf91ac5cae88019bee94d24528526344c36649ffff001d1d03e477");
        //blockHeaders.add("01000000fc33f596f822a0a1951ffdbf2a897b095636ad871707bf5d3162729b00000000379dfb96a5ea8c81700ea4ac6b97ae9a9312b2d4301a29580e924ee6761a2520adc46649ffff001d189c4c97");
        //blockHeaders.add("010000008d778fdc15a2d3fb76b7122a3b5582bea4f21f5a0c693537e7a03130000000003f674005103b42f984169c7d008370967e91920a6a5d64fd51282f75bc73a68af1c66649ffff001d39a59c86");
        //blockHeaders.add("010000004494c8cf4154bdcc0720cd4a59d9c9b285e4b146d45f061d2b6c967100000000e3855ed886605b6d4a99d5fa2ef2e9b0b164e63df3c4136bebf2d0dac0f1f7a667c86649ffff001d1c4b5666");


        final MerkleTreeNode<BlockHeader> blockHeaderMerkleTree = new MerkleTreeNode<BlockHeader>();
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        for (int i=0; i<blockHeaders.getCount(); i++) {
            final String header = blockHeaders.get(i);
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray(header));
            blockHeaderMerkleTree.addItem(blockHeader);
        }

        final MutableList<Sha256Hash> expectedBranch = new MutableArrayList<>();
        expectedBranch.add(Sha256Hash.fromHexString("000000004ebadb55ee9096c9a2f8880e09da59c0d68b1c228da88e48844a1485"));
        expectedBranch.add(Sha256Hash.fromHexString("92b9421770580bea765ce97b78f8cf7feb9825bdb47acb5bee51c788890dc7a1"));
        expectedBranch.add(Sha256Hash.fromHexString("965ac94082cebbcffe458075651e9cc33ce703ab0115c72d9e8b1a9906b2b636"));

        final List<Sha256Hash> merkleBranch = blockHeaderMerkleTree.getPartialTree(4);
        Assert.assertEquals(expectedBranch.getCount(), merkleBranch.getCount());
        for (int i=0; i<expectedBranch.getCount(); i++) {
            Assert.assertEquals(expectedBranch.get(i).toString(), merkleBranch.get(i).toString());
        }
    }

}