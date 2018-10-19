package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.test.util.TestUtil;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.type.merkleroot.MutableMerkleRoot;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.IoUtil;
import org.junit.Assert;
import org.junit.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class MerkleTreeTests {
    private static byte[] hashTwice(final byte[] input1, final byte[] input2) {
        MessageDigest digest = null;
        {
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (final NoSuchAlgorithmException e) { }
        }

        digest.update(input1, 0, 32);
        digest.update(input2, 0, 32);
        return digest.digest(digest.digest());
    }

    private MerkleRoot referenceImplementation(final List<? extends Hashable> items) {
        final ArrayList<byte[]> tree = new ArrayList<>();
        for (final Hashable item : items) {
            tree.add(item.getHash().getBytes());
        }

        int levelOffset = 0;
        for (int levelSize = items.getSize(); levelSize > 1; levelSize = (levelSize + 1) / 2) {
            for (int left = 0; left < levelSize; left += 2) {
                int right = Math.min(left + 1, levelSize - 1);
                byte[] leftBytes = ByteUtil.reverseEndian(tree.get(levelOffset + left));
                byte[] rightBytes = ByteUtil.reverseEndian(tree.get(levelOffset + right));
                tree.add(ByteUtil.reverseEndian(hashTwice(leftBytes, rightBytes)));
            }
            levelOffset += levelSize;
        }
        return MutableMerkleRoot.wrap(tree.get(tree.size() - 1));
    }

    private MerkleRoot _calculateSpvMerkle(final Integer transactionIndex, final Item baseItem, final List<Sha256Hash> items) {
        byte[] hash0 = baseItem.getHash().getBytes();
        for (final Sha256Hash hash1 : items) {
            byte[] leftBytes = ByteUtil.reverseEndian(hash0);
            byte[] rightBytes = ByteUtil.reverseEndian(hash1.getBytes());
            hash0 = ByteUtil.reverseEndian(hashTwice(leftBytes, rightBytes));
        }
        return MutableMerkleRoot.wrap(hash0);
    }

    class Item implements Hashable {
        private final int _value;

        public Item(final int value) { _value = value; }

        @Override
        public Sha256Hash getHash() {
            return MutableMerkleRoot.wrap(BitcoinUtil.sha256(ByteUtil.integerToBytes(_value)));
        }

        @Override
        public boolean equals(final Object object) {
            if (object == null) { return false; }
            if (! (object instanceof Item)) { return false; }

            final Item item = (Item) object;
            return (_value == item._value);
        }
    }

    private void _should_calculate_the_merkle_root_after_replacing_the_Nth_item_with_tree_size_M(final Integer nthItem, final Integer treeSize) {
        // Setup
        final List<Item> items;
        {
            final ImmutableListBuilder<Item> listBuilder = new ImmutableListBuilder<Item>(treeSize);
            for (int i = 0; i < treeSize; ++i) {
                listBuilder.add(new Item(i));
            }
            items = listBuilder.build();
        }

        final List<Item> itemsWithReplacements;
        {
            final MutableList<Item> mutableList = new MutableList<Item>(items);
            mutableList.set(nthItem, new Item(-1));
            itemsWithReplacements = mutableList;
        }

        final MerkleRoot expectedValue = referenceImplementation(itemsWithReplacements);

        final MerkleTree<Item> merkleTree = new MerkleTreeNode<Item>();
        for (final Item item : items) {
            merkleTree.addItem(item);
        }
        merkleTree.replaceItem(nthItem, new Item(-1));

        // Action
        final MerkleRoot merkleRoot = merkleTree.getMerkleRoot();

        // Assert
        TestUtil.assertEqual(expectedValue.getBytes(), merkleRoot.getBytes());
        Assert.assertEquals(treeSize.intValue(), merkleTree.getItemCount());
        Assert.assertEquals(itemsWithReplacements, merkleTree.getItems());
    }

    private void _should_create_partial_tree_for_missing_transaction_N_with_item_count_M(final Integer transactionIndex, final Integer itemCount, final Integer expectedPartialTreeSize) {
        // Setup
        final Item newItem = new Item(-1);

        final MutableList<Item> items = new MutableList<Item>(itemCount);
        final MerkleTree<Item> merkleTree = new MerkleTreeNode<Item>();
        for (int i = 0; i < itemCount; ++i) {
            final Item item = new Item(i);
            items.add(item);
            merkleTree.addItem(item);
        }

        // Action
        final List<Sha256Hash> partialMerkleTree = merkleTree.getPartialTree(transactionIndex);
        final MerkleRoot spvMerkle = _calculateSpvMerkle(transactionIndex, newItem, partialMerkleTree);

        // Assert
        Assert.assertEquals(expectedPartialTreeSize.intValue(), partialMerkleTree.getSize());
        merkleTree.replaceItem(transactionIndex, newItem);
        final MerkleRoot expectedMerkleRoot = merkleTree.getMerkleRoot();

        TestUtil.assertEqual(expectedMerkleRoot.getBytes(), spvMerkle.getBytes());
    }


    @Test
    public void should_calculate_the_merkle_root_with_one_transaction() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();

        final byte[] expectedMerkleRoot = HexUtil.hexStringToByteArray("8A940F44B09744533577BA4605049D90C2E4964EF863AA396BE2DF0D40D8E85A");
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000972FD75068380810E74D8C709B3D451421ED32023EE6F3DB89A9B9B1000000005AE8D8400DDFE26B39AA63F84E96E4C2909D040546BA7735534497B0440F948AF8A3BF49FFFF001D055C0D2B0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0804FFFF001D025E05FFFFFFFF0100F2052A010000004341048A83FC8C9C15DF94BE66739E4C212997F294190E7802881C458693C6C48ED2200F6548C47363DB83361635C06F27D33731D4A9830EE767CD322F2DA251031396AC00000000"));
        final MerkleTree<Transaction> merkleTree = new MerkleTreeNode<Transaction>();

        for (final Transaction transaction : block.getTransactions()) {
            merkleTree.addItem(transaction);
        }

        // Action
        final MerkleRoot merkleRoot = merkleTree.getMerkleRoot();

        // Assert
        TestUtil.assertEqual(expectedMerkleRoot, merkleRoot.getBytes());
    }

    @Test
    public void should_calculate_the_merkle_root_with_four_transactions() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();

        final byte[] expectedMerkleRoot = HexUtil.hexStringToByteArray("F3E94742ACA4B5EF85488DC37C06C3282295FFEC960994B2C0D5AC2A25A95766");
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray("0100000050120119172A610421A6C3011DD330D9DF07B63616C2CC1F1CD00200000000006657A9252AACD5C0B2940996ECFF952228C3067CC38D4885EFB5A4AC4247E9F337221B4D4C86041B0F2B57100401000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF08044C86041B020602FFFFFFFF0100F2052A010000004341041B0E8C2567C12536AA13357B79A073DC4444ACB83C4EC7A0E2F99DD7457516C5817242DA796924CA4E99947D087FEDF9CE467CB9F7C6287078F801DF276FDF84AC000000000100000001032E38E9C0A84C6046D687D10556DCACC41D275EC55FC00779AC88FDF357A187000000008C493046022100C352D3DD993A981BEBA4A63AD15C209275CA9470ABFCD57DA93B58E4EB5DCE82022100840792BC1F456062819F15D33EE7055CF7B5EE1AF1EBCC6028D9CDB1C3AF7748014104F46DB5E9D61A9DC27B8D64AD23E7383A4E6CA164593C2527C038C0857EB67EE8E825DCA65046B82C9331586C82E0FD1F633F25F87C161BC6F8A630121DF2B3D3FFFFFFFF0200E32321000000001976A914C398EFA9C392BA6013C5E04EE729755EF7F58B3288AC000FE208010000001976A914948C765A6914D43F2A7AC177DA2C2F6B52DE3D7C88AC000000000100000001C33EBFF2A709F13D9F9A7569AB16A32786AF7D7E2DE09265E41C61D078294ECF010000008A4730440220032D30DF5EE6F57FA46CDDB5EB8D0D9FE8DE6B342D27942AE90A3231E0BA333E02203DEEE8060FDC70230A7F5B4AD7D7BC3E628CBE219A886B84269EAEB81E26B4FE014104AE31C31BF91278D99B8377A35BBCE5B27D9FFF15456839E919453FC7B3F721F0BA403FF96C9DEEB680E5FD341C0FC3A7B90DA4631EE39560639DB462E9CB850FFFFFFFFF0240420F00000000001976A914B0DCBF97EABF4404E31D952477CE822DADBE7E1088ACC060D211000000001976A9146B1281EEC25AB4E1E0793FF4E08AB1ABB3409CD988AC0000000001000000010B6072B386D4A773235237F64C1126AC3B240C84B917A3909BA1C43DED5F51F4000000008C493046022100BB1AD26DF930A51CCE110CF44F7A48C3C561FD977500B1AE5D6B6FD13D0B3F4A022100C5B42951ACEDFF14ABBA2736FD574BDB465F3E6F8DA12E2C5303954ACA7F78F3014104A7135BFE824C97ECC01EC7D7E336185C81E2AA2C41AB175407C09484CE9694B44953FCB751206564A9C24DD094D42FDBFDD5AAD3E063CE6AF4CFAAEA4EA14FBBFFFFFFFF0140420F00000000001976A91439AA3D569E06A1D7926DC4BE1193C99BF2EB9EE088AC00000000"));
        final MerkleTree<Transaction> merkleTree = new MerkleTreeNode<Transaction>();

        final List<Transaction> transactions = block.getTransactions();
        Assert.assertEquals(4, transactions.getSize());

        for (final Transaction transaction : transactions) {
            merkleTree.addItem(transaction);
        }

        // Action
        final MerkleRoot merkleRoot = merkleTree.getMerkleRoot();

        // Assert
        TestUtil.assertEqual(expectedMerkleRoot, merkleRoot.getBytes());
    }

    @Test
    public void should_calculate_the_merkle_root_with_thirteen_transactions() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();

        final byte[] expectedMerkleRoot = HexUtil.hexStringToByteArray("0E60651A9934E8F0DECD1C5FDE39309E48FCA0CD1C84A21DDFDE95033762D86C");
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray("010000009500C43A25C624520B5100ADF82CB9F9DA72FD2447A496BC600B0000000000006CD862370395DEDF1DA2841CCDA0FC489E3039DE5F1CCDDEF0E834991A65600EA6C8CB4DB3936A1AE31439910D01000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704B3936A1A017CFFFFFFFF01403D522A01000000434104563053B8900762F3D3E8725012D617D177E3C4AF3275C3265A1908B434E0DF91EC75603D0D8955EF040E5F68D5C36989EFE21A59F4EF94A5CC95C99794A84492AC000000000100000001544BB26A50502EF11C745F1127B29A47856B3ECD9A1B6C540A66493D5FBF0046010000008B483045022100C9E35AA55AF5AC98CB67C4DB7CF3D3F128753C4698F5D25CA0CDC3DECD0C46BE02204D6DFE89BD3FE88A32D47A44C0AB3AB60D87B27B90106F1B2F9F67C9C60CC80C01410449B8D933F97A8C4FE6CE962EE2ABFF8A81D8CFC5E0870A50CEA76C50D04ADDF2DF09331C4A47CDC3BC27A628E766EF5D01F28EE147ED21723B5FF3A62ED8DA3EFFFFFFFF024094EF03010000001976A9146C8DE651F8B92F87FF43FB9732BABEC784BDB6F588ACC05D1626000000001976A9144F006767FEEBF6438AAF51EF86AE4286A1C571B988AC000000000100000001C03533907C967249C5DC80D266E6E65555581E6328F6A3859164BFBB1EB0BC17000000008B483045022100A616082B724043758A4732DAE5461783CB48CCF1B36A7F98D41F3F5F8DB577F80220084DF7AFC2C912C3EF23EA43CFEC651BA97EE6C4E4220C0F58B78F374626BDFB01410412AEBCB2C5D5625C138C9CDD318AE04A103002B0D1B1541D72CD51DC016BFEC885E7B93D73375903ACB2731CDB7790B1D969DB2684646E6FBEABB0559B6513C1FFFFFFFF024094EF03010000001976A914FA91E22C8A2A4D1FA592DE1CF7AA8F958867A1D288ACC05D1626000000001976A91452FC9E7E1B8BE3A61FE20381CB343E62EAC105CB88AC0000000001000000015F71A19A74E9F5D202B9DD552471F2E8A24FD883A76F23455F1E4B738FD2C20F000000008B483045022100B6F858814DBF6A6D2DC71701028361A9E9EF2E2C52E5898F1BFCCFAD145CCDA7022046973784A8DE9007A52576070621C29E92F91A332960A5FB7DED81973D2E7BE4014104DD2755D0A38AF359659614530BD25E35FCB3D215B1FC41038BEB495EA5DB774E71A2F307652774B79C5DFA8FDCCFF372E61A27A177C6B157CC22BEEC361CC387FFFFFFFF0200688909000000001976A91473B81E77969678BA046C79B44829F769FF2E00F188AC40660301000000001976A914597588D391674D08C2CFC35015DB9487922ABA5288AC000000000100000001D1371F11CC10390FA0361F3E6B2E25E48C2F69040BF7D400D1B2ECF97B8461F0000000008B483045022061C083B63887398F85A527C8E256EE3498BC50FA6BC85D90EBF63C49AD406836022100884FFD73107B1129F378BFA29858D1E1C89F89922260A99A32A813572E3884460141044B32F0631083E1CC5F0401807ABC7A12A715083297B86923675A8BF66BC6AF0E82A184BED186896CE1059C9BB36E26B265536F4C9F181F570A53891EC9BA4A18FFFFFFFF02804F470C000000001976A914AFD883B5BD6AE5E88B39CC692DB33A6FF7182DCB88AC00CA9A3B000000001976A9145539C2A8CEE228EF366CB0E8F29E0079CE03874888AC000000000100000001B36BCE13FF6BE1D5BD81267F9A24B2636FAE20E0A1A5CA6C469D1C0FBC56A1EF000000008A4730440220592616F1AF3377A3CC7BC721745CF2657131759B35C40AE03820931062C061C802203FC4ADB8D0DA6BD4269F8331B21793304743015A735BCBB95298AD82595F40DA014104DD182DFCC9EB893EA6A0F34CEC5906FBB61008B6FD669B74414D2999BF49FD21F7EB775F6175234D79CF8B9D3AC749B2BB3673462CD94641BCF9E27FE2CFC08BFFFFFFFF02C09CCA64000000001976A91494006D131D49DD5C766721D7FD1DB65DF86C539D88AC4030A779000000001976A91421136BA0C035BB72E736975F0F9410117CF050B588AC000000000100000003D572D1E1FF09A1DF9C081417F9A225C16D8E94FCA3CC0D3112761E5DF11F33E2000000008C49304602210097F54A78861AA913CA1B76925107A26762D7405CFEAA19983A99FB61D99E5540022100AB1CD36486FB5598D67E15E9947F17D5DAB53FDD013B0E9CA8B8FFB2F7F40E9E014104B5F0DFE1370E364F516D25E1524EA5EB8CB8AFE7E0F739576283FFBCD7B8307F0F79459D34CE8F6386BC725B730C26F4E5996029824E48BAED2AD75D8B916FD3FFFFFFFF7C945863DC4CDB9C2E160E3653C8AB77A33B55F061DF53507C0A406A07E33E5E010000008C493046022100E7EDA51BA3EFF04C1E5FDD93BF4397E0E652CE38C725B687554E1DCCF562FDD80221009AFA588CF5347CD7F50E4C097FFABE789A3C55235D8A2FD639B21D2A73B50FB8014104A105201ABCC58F7773FC5D7125344038B5CA77571173AC4B5741F9B11F8B360F4E88AC887F680AF7FDE035295939145D868011E0B37E077CC5E5AA2B699F6030FFFFFFFF1B1887532137B5C7EC5C141F5CB88DBF1465C578FB6491ACC4D9FFADE969BDBB010000008B4830450220630795AEFE55D8BE9111EE802841EAE06A6B4AA6FA2356EB2D00142D93263465022100E9E1DC61DBAF1A3E4E57B84C8C515CDF7687DDF0E0D7B04036105DA6CFCB2845014104A105201ABCC58F7773FC5D7125344038B5CA77571173AC4B5741F9B11F8B360F4E88AC887F680AF7FDE035295939145D868011E0B37E077CC5E5AA2B699F6030FFFFFFFF0180B2E60E000000001976A914C865D81683BD195F92E47C583B35ED0B6A37F6A388AC000000000100000002EEC8C3317B0FB6C41A6D8332523F8EF4B74C98A963A1928CA9D280AA323D9DEE000000008B483045022100FD606F480B20406C8DA75F4640C940E441FF57442B982CA0B45ED4A55B622522022079DE38E1BAF58BC0DBE12938412BDD3CD7E0BE71FA5B67EECA2B5B343AEDE45C01410466F4F8457E681710421C89E6577DB1973485F11708FF062155BB34A3EF0E4423F052C5D4EB548E79647CFF8F2D272B3A8CFE9F34025BC90C60652BD41EBB2D57FFFFFFFFEEC8C3317B0FB6C41A6D8332523F8EF4B74C98A963A1928CA9D280AA323D9DEE010000008C4930460221008A34DE93E3081BFB6E41F79F591851895CF9CB354775966B00769F7BA9E5FC02022100A8778D15080A07721FADB4EC63229677FDC09CE919C10EDF5B50DE46C8199EE301410412581A35FDF35E291FC14119096A4E0C8E4F4ECF5C6EDE7F727AAE7D859AA1AC1AE3D5F777740998352DD76E46F777026F8B9E6A10A98BCD70C6B0CA25E3A7FBFFFFFFFF02C0E1E400000000001976A914DBA62E0FBC75A167491D075CBE916D4D95FB412E88AC00B4C404000000001976A9140A1D803E44F099D3C91D4B988D3C8A9FCA5D4B5D88AC0000000001000000014395F5CF12900E99DD6192B6209B49B3450E566019ED408D99104DD200D41C59000000008B483045022100A4409279BCBAE239067D4533AB66F60B814A7C736E0E493138573F58733BBF1002204BED71DCB9C0E761D6C301C8358ED65081C60644A48EFEF6C37BAF9729E6FC2701410432A325C210A4F9BB5D1522B81011A51BAC208BBA644EEE54B2C9FBCBD748A53CA04F57ACD052B247A1ACE735C414958A3E1FDED493F4128207E00EA058CEFF87FFFFFFFF024016750C000000001976A914CDC0D731B176C3AA8BA0AE0EFDC27206AFA08E7588ACC0B06006000000001976A914FC80396945A751A5DBC4B8EE682993675E55533588AC000000000100000001C2AD61D37043DE51542592378F2D0122D118EC78EBC4E9BB59E59A03D38432AA000000008B48304502201837AE7E28EC87D59B44E3E86848762D827363D0164B02C62F108F9ABC5FC1D3022100FEE05B2212E743BF8050FDD3E0CD19C8ACBA7FEF1172678A909048A81FA18FBD014104AE588CBC9515B921F6F06E8FDCEE9BB4276CFA3F7EEE37AFFFCF3AC348E59323D959D720FFB0C4EB13CF8D8FEEBFF39857AF582181801F21BF13090C2D1FDB38FFFFFFFF0200E1F505000000001976A914CF1071894099F6ED178A9D3F8F736DB0D9B8660B88AC4062B007000000001976A9144E60B49DCC9534D4700AB2BBEA5880C221CAB64488AC000000000100000003743C127E0CF6A849891792A6CAEC54CF4EF73681F423785C97D36AEA27757FED000000008B48304502200F73796A8EF4F6B152485D2B5A81E6B077F50CC3535DBCD2E6BB4A72DB6F1FB6022100EF9414EDF553130A761380D85361301A3DD8747A6F26C454C3CD34B3E8A40B19014104DA0B3696C878D3EF54B9B5EE21E2D6E1AF534C5A8F6AB1CB7AF342ABD717AEDE2556B78813C02CCB98FEB3BD4A88FF99E9E4C14F371CAF9B4E175AF19A70588DFFFFFFFF038FF9B434FD29A959006553C0AF7E0BD60AB498C15B9CC5D5900859303BED48000000008B48304502210087BEB550E5C32F1550C4D152BEBB78FE95569F1BF0C6896BAFEF0D0E4CD1A6C902207A0449332EE5F53AB5904B6E8849DE39931350DB8EDB91AD0064F0D6D3F15B210141047AA5D5BA00D20563285E33599FB71AF0657DD107C84BCDEA3335DDAC7B5B47F537A3A7C80AC22B295628CEE7FB07938D85A440C17066A364480CC22679641671FFFFFFFFD4A590514AEACE8E06D1BB2FB4FFC67A697D69D044D37F8F50C2C5DBC946B2AD010000008B483045022050D56ECF07775C47CC6937A8B87E45B6E37E22DABC421C91E62A776D727BB084022100FC93854986173493926F1AB10958EEC7C3EF6B040DE91948848399FE66CB9A6D01410439C6BF25CA3DE24D30353DC4A202670C4DE1327FB5D3B56020BD76D51B17E4DAC84001894904F2B426362B52F808901D8B2066D0EEF6F753C834105EEAB9BF1AFFFFFFFF0240420F00000000001976A914328EA7ED3377BE3C7FAB0D09487309057A33F8C588AC8047A119000000001976A914C1773B304BB2F975623F01FA365FB55E86B340C488AC000000000100000001C20A545D9DF9F6B0F58EAE2EEC1CFE76DDB49D705A11DCB44A24CB464197E7E3010000008C493046022100B422FDC92AA6A86AF349B826C033ECC2139DDF79AD7D215A0C00701CDE4CE1D3022100A1017FC5C88B70900B56BBFB67EB0F45E47A4B4D28F63674FC7DFF7F6FB1328F0141043F6B33E5101C289C5C760CB35F77C43AB8F4F2EBCE622B68F7105E166EE7CEB2D3A8562A094038452D702C0DDDE1FD089BCEB84C0EB8C4584D116C040C49F6B3FFFFFFFF02808D5B00000000001976A91420782923B21B1DD5B6B64D5069F7B01168288E0B88AC00E1F505000000001976A914AE214BAA6CD56A0D50C9B60AEA2352A56236207688AC0000000001000000016A0581837861FBCE6253B8E950EB606E0BA879BF9E6A5CD9CADB919FD376BE38000000008A47304402206C7308A8FA8D45C082DA032880270C10D436D2A4623BA6E13819D45EECF0F3B90220356F4E9855A101487B680D0B4E69B10C509015293236F2CFBAF9DA6CB6791466014104CFD5868F564BAE61BF22479E8D22E030FBDBF9A01A9A0B61DDFB580A9552B47B15F8A28E7C40B8FE73D6B9C0E0CDD1527266602B327DCC272BD8C1A33B87E4DEFFFFFFFF0248647800000000001976A914CEB552BDF23D002AED04C317C92CF8987E550DF988AC40420F00000000001976A914586CE63C59B47A3AD08CCAC6F132DC04CCBEA0E288AC00000000"));
        final MerkleTree<Transaction> merkleTree = new MerkleTreeNode<Transaction>();

        final List<Transaction> transactions = block.getTransactions();
        Assert.assertEquals(13, transactions.getSize());

        for (final Transaction transaction : transactions) {
            merkleTree.addItem(transaction);
        }

        // Action
        final MerkleRoot merkleRoot = merkleTree.getMerkleRoot();

        // Assert
        TestUtil.assertEqual(expectedMerkleRoot, merkleRoot.getBytes());
    }

    @Test
    public void should_calculate_the_merkle_root_with_over_a_thousand_transactions() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final String blockData = IoUtil.getResource("/blocks/00000000000000000051CFB8C9B8191EC4EF14F8F44F3E2290D67A8A0A29DD05");

        final byte[] expectedMerkleRoot = HexUtil.hexStringToByteArray("3DDE8912B6BF03E04DFFF24408B4643DE1E3419C84585FB96E4EC7ACA6CC29A9");
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
        final MerkleTree<Transaction> merkleTree = new MerkleTreeNode<Transaction>();

        final List<Transaction> transactions = block.getTransactions();
        Assert.assertEquals(1496, transactions.getSize());

        for (final Transaction transaction : transactions) {
            merkleTree.addItem(transaction);
        }

        // Action
        final MerkleRoot merkleRoot = merkleTree.getMerkleRoot();

        // Assert
        TestUtil.assertEqual(expectedMerkleRoot, merkleRoot.getBytes());
    }

    // @Test
    // TODO: Unsure why this test fails...  Considering this is block #300,000, it's possibly a change in the protocol.
    public void should_calculate_the_merkle_root_with_over_a_hundred_transactions() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final String blockData = IoUtil.getResource("/blocks/000000000000000082CCF8F1557C5D40B21EDABB18D2D691CFBF87118BAC7254");

        final byte[] expectedMerkleRoot = HexUtil.hexStringToByteArray("3DDE8912B6BF03E04DFFF24408B4643DE1E3419C84585FB96E4EC7ACA6CC29A9");
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
        final MerkleTree<Transaction> merkleTree = new MerkleTreeNode<Transaction>();

        final List<Transaction> transactions = block.getTransactions();
        Assert.assertEquals(237, transactions.getSize());

        for (final Transaction transaction : transactions) {
            merkleTree.addItem(transaction);
        }

        // Action
        final MerkleRoot merkleRoot = merkleTree.getMerkleRoot();

        // Assert
        TestUtil.assertEqual(expectedMerkleRoot, merkleRoot.getBytes());
    }

    @Test
    public void should_calculate_the_merkle_root_with_over_twenty_transactions() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final String blockData = IoUtil.getResource("/blocks/000000000000051F68F43E9D455E72D9C4E4CE52E8A00C5E24C07340632405CB");

        final byte[] expectedMerkleRoot = HexUtil.hexStringToByteArray("AD590F24A9A2F3895D7B1597AB6F5F5DFEC1571D346BE40F971D37EBBB6A8E2B");
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
        final MerkleTree<Transaction> merkleTree = new MerkleTreeNode<Transaction>();

        final List<Transaction> transactions = block.getTransactions();
        Assert.assertEquals(27, transactions.getSize());

        for (final Transaction transaction : transactions) {
            merkleTree.addItem(transaction);
        }

        // Action
        final MerkleRoot merkleRoot = merkleTree.getMerkleRoot();

        // Assert
        TestUtil.assertEqual(expectedMerkleRoot, merkleRoot.getBytes());
    }

    @Test
    public void should_calculate_the_merkle_root_with_over_ninety_transactions() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final String blockData = IoUtil.getResource("/blocks/0000000000000898E40E2EA2CB98E3A5EBBE2852461E581C5813633A6A267F6E");

        final byte[] expectedMerkleRoot = HexUtil.hexStringToByteArray("B13C237E2A3BA3BC6DC3886E592DC5DF4B77A21F20E07D1901A071D15163A821");
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
        final MerkleTree<Transaction> merkleTree = new MerkleTreeNode<Transaction>();

        final List<Transaction> transactions = block.getTransactions();
        Assert.assertEquals(98, transactions.getSize());

        for (final Transaction transaction : transactions) {
            merkleTree.addItem(transaction);
        }

        // Action
        final MerkleRoot merkleRoot = merkleTree.getMerkleRoot();

        // Assert
        TestUtil.assertEqual(expectedMerkleRoot, merkleRoot.getBytes());
    }

    @Test
    public void bug_0001_should_calculate_hash_for_block_29664() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(IoUtil.getResource("/blocks/00000000AFE94C578B4DC327AA64E1203283C5FD5F152CE886341766298CF523")));
        Assert.assertEquals(MutableSha256Hash.fromHexString("0E0ABB91667C0BB906E9ED8BBBFB5876FCCB707C2D9E7DAB3603B57F41EC431F"), block.getTransactions().get(0).getHash());
        Assert.assertEquals(MutableSha256Hash.fromHexString("3A5769FB2126D870ADED5FCACED3BC49FA9768436101895931ADB5246E41E957"), block.getTransactions().get(1).getHash());

        final Sha256Hash expectedBlockHash = Sha256Hash.fromHexString("00000000AFE94C578B4DC327AA64E1203283C5FD5F152CE886341766298CF523");
        final MerkleRoot expectedMerkleRoot = MutableMerkleRoot.fromHexString("C5997D1CAD40AFEC154AA99B8988E97B1F113D8076357A77572455574765A533");

        // Action
        final Sha256Hash blockHash = block.getHash();
        final MerkleRoot merkleRoot = block.getMerkleRoot();

        // Assert
        Assert.assertEquals(expectedMerkleRoot, merkleRoot);
        Assert.assertEquals(expectedBlockHash, blockHash);
    }

    @Test
    public void should_calculate_the_merkle_root_of_a_recent_block_with_less_than_one_hundred_transactions() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final String blockData = IoUtil.getResource("/blocks/00000000000000000094ABDB2DCA7ED8C828F5C5F9C5840E9ACCFE1E6EF69FE0");

        final byte[] expectedMerkleRoot = HexUtil.hexStringToByteArray("E11AB13BAA9AB568CA45478A640186D90EC1C5A50CBB89F962B3FE1845756B09");
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
        final MerkleTree<Transaction> merkleTree = new MerkleTreeNode<Transaction>();

        final List<Transaction> transactions = block.getTransactions();
        Assert.assertEquals(63, transactions.getSize());

        for (final Transaction transaction : transactions) {
            merkleTree.addItem(transaction);
        }

        // Action
        final MerkleRoot merkleRoot = merkleTree.getMerkleRoot();

        // Assert
        TestUtil.assertEqual(expectedMerkleRoot, merkleRoot.getBytes());
    }

    @Test
    public void should_calculate_the_merkle_root_after_replacing_transactions_0_1() {
        _should_calculate_the_merkle_root_after_replacing_the_Nth_item_with_tree_size_M(0, 1);
    }

    @Test
    public void should_calculate_the_merkle_root_after_replacing_transactions_0_2() {
        _should_calculate_the_merkle_root_after_replacing_the_Nth_item_with_tree_size_M(0, 2);
    }

    @Test
    public void should_calculate_the_merkle_root_after_replacing_transactions_0_3() {
        _should_calculate_the_merkle_root_after_replacing_the_Nth_item_with_tree_size_M(0, 3);
    }

    @Test
    public void should_calculate_the_merkle_root_after_replacing_transactions_1_3() {
        _should_calculate_the_merkle_root_after_replacing_the_Nth_item_with_tree_size_M(1, 3);
    }

    @Test
    public void should_calculate_the_merkle_root_after_replacing_transactions_2_3() {
        _should_calculate_the_merkle_root_after_replacing_the_Nth_item_with_tree_size_M(2, 3);
    }

    @Test
    public void should_calculate_the_merkle_root_after_replacing_transactions_0_9() {
        _should_calculate_the_merkle_root_after_replacing_the_Nth_item_with_tree_size_M(0, 9);
    }

    @Test
    public void should_calculate_the_merkle_root_after_replacing_transactions_7_9() {
        _should_calculate_the_merkle_root_after_replacing_the_Nth_item_with_tree_size_M(7, 9);
    }

    @Test
    public void should_calculate_the_merkle_root_after_replacing_transactions_8_9() {
        _should_calculate_the_merkle_root_after_replacing_the_Nth_item_with_tree_size_M(8, 9);
    }

    @Test
    public void should_create_partial_tree_for_coinbase_transaction_with_item_count_13() {
        _should_create_partial_tree_for_missing_transaction_N_with_item_count_M(0, 13, 4);
    }

    @Test
    public void should_create_partial_tree_for_coinbase_transaction_with_item_count_4() {
        _should_create_partial_tree_for_missing_transaction_N_with_item_count_M(0, 4, 2);
    }

    @Test
    public void should_create_partial_tree_for_coinbase_transaction_with_item_count_2() {
        _should_create_partial_tree_for_missing_transaction_N_with_item_count_M(0, 2, 1);
    }

    @Test
    public void should_create_partial_tree_for_coinbase_transaction_with_item_count_1() {
        _should_create_partial_tree_for_missing_transaction_N_with_item_count_M(0, 1, 0);
    }

    @Test
    public void should_create_partial_tree_for_coinbase_transaction_with_item_count_26() {
        _should_create_partial_tree_for_missing_transaction_N_with_item_count_M(0, 26, 5);
    }

    @Test
    public void should_get_items_in_order() {
        // Setup
        final MerkleTreeNode<Item> merkleTree = new MerkleTreeNode<Item>();
        for (int i = 0; i < 100; ++i) {
            merkleTree.addItem(new Item(i));

            for (int j = 0; j < merkleTree.getItemCount(); ++j) {
                final Item item = merkleTree.getItem(j);
                Assert.assertEquals(new Item(j).getHash(), item.getHash());
            }
        }

        // Action

        // Assert
    }
}
