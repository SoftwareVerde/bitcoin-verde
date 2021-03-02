package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MerkleBlock;
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

        final CachedThreadPool threadPool = new CachedThreadPool(1, 1000L);
        threadPool.start();

        final BitcoinNode bitcoinNode = new BitcoinNode("btc.softwareverde.com", 8333, threadPool, new LocalNodeFeatures() {
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
        threadPool.stop();
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

        final ImmutableListBuilder<Sha256Hash> merkleHashes = new ImmutableListBuilder<Sha256Hash>(block.getTransactionCount());
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

        final MutableList<Sha256Hash> transactionHashes = new MutableList<Sha256Hash>(2);
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

        final CachedThreadPool threadPool = new CachedThreadPool(1, 1000L);
        threadPool.start();

        final BitcoinNode bitcoinNode = new BitcoinNode("btc.softwareverde.com", 8333, threadPool, new LocalNodeFeatures() {
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

        final Container<MerkleBlock> merkleBlockContainer = new Container<MerkleBlock>();
        Thread.sleep(1000L);
        bitcoinNode.requestMerkleBlock(block.getHash(), new BitcoinNode.DownloadMerkleBlockCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final MerkleBlockParameters merkleBlockParameters) {
                merkleBlockContainer.value = merkleBlockParameters.getMerkleBlock();
            }
        });
        Thread.sleep(3000L);

        bitcoinNode.disconnect();
        threadPool.stop();

        final List<Sha256Hash> merkleHashes = merkleBlockContainer.value.getPartialMerkleTree().getHashes();
        final ByteArray flags = merkleBlockContainer.value.getPartialMerkleTree().getFlags();

        final PartialMerkleTree partialMerkleTree = PartialMerkleTree.build(block.getTransactionCount(), merkleHashes, flags);
        final MerkleRoot merkleRoot = partialMerkleTree.getMerkleRoot();

        Assert.assertEquals(block.getMerkleRoot(), merkleRoot);

        final PartialMerkleTree generatedPartialMerkleTree = block.getPartialMerkleTree(bloomFilter);
        final PartialMerkleTree reinflatedPartialMerkleTree = PartialMerkleTree.build(block.getTransactionCount(), generatedPartialMerkleTree.getHashes(), generatedPartialMerkleTree.getFlags());
        Assert.assertEquals(block.getMerkleRoot(), reinflatedPartialMerkleTree.getMerkleRoot());
    }
}
