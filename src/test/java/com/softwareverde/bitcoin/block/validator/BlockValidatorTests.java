package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.ImmutableDifficulty;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeBlockValidatorContext;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.coinbase.MutableCoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ImmutableScript;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.transaction.signer.HashMapTransactionOutputRepository;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidationResult;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorCore;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.IoUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BlockValidatorTests extends UnitTest {
    public static MutableTransactionOutput createTransactionOutput(final Address payToAddress, final Long amount) {
        final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();
        transactionOutput.setAmount(amount);
        transactionOutput.setIndex(0);
        transactionOutput.setLockingScript((ScriptBuilder.payToAddress(payToAddress)));
        return transactionOutput;
    }

    public static MutableTransactionInput createTransactionInputThatSpendsTransaction(final Transaction transactionToSpend) {
        final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
        mutableTransactionInput.setPreviousOutputTransactionHash(transactionToSpend.getHash());
        mutableTransactionInput.setPreviousOutputIndex(0);
        mutableTransactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
        mutableTransactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
        return mutableTransactionInput;
    }

    public static MutableTransaction createTransactionContaining(final TransactionInput transactionInput, final TransactionOutput transactionOutput) {
        final MutableTransaction mutableTransaction = new MutableTransaction();
        mutableTransaction.setVersion(1L);
        mutableTransaction.setLockTime(new ImmutableLockTime(LockTime.MIN_TIMESTAMP));

        mutableTransaction.addTransactionInput(transactionInput);
        mutableTransaction.addTransactionOutput(transactionOutput);

        return mutableTransaction;
    }

    protected static BlockHeader inflateBlockHeader(final BlockHeaderInflater blockInflater, final String blockData) {
        return blockInflater.fromBytes(ByteArray.fromHexString(blockData));
    }

    protected static Block inflateBlock(final BlockInflater blockInflater, final String blockData) {
        return blockInflater.fromBytes(ByteArray.fromHexString(blockData));
    }

    protected static void assertCoinbaseIsInvalid(final CoinbaseTransaction invalidCoinbase) throws Exception {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();

        final Block genesisBlock = inflateBlock(blockInflater, BlockData.MainChain.GENESIS_BLOCK);

        final Block originalBlock01 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain2.BLOCK_1));
        final MutableBlock modifiedBlock01 = new MutableBlock(originalBlock01) {
            @Override
            public Sha256Hash getHash() {
                return originalBlock01.getHash();
            }

            @Override
            public Boolean isValid() {
                return originalBlock01.isValid();
            }
        };

        modifiedBlock01.replaceTransaction(0, invalidCoinbase);

        final FakeBlockValidatorContext blockValidatorContext = new FakeBlockValidatorContext(NetworkTime.MAX_VALUE, upgradeSchedule);
        final BlockValidator blockValidator = new BlockValidator(blockValidatorContext);
        blockValidatorContext.addBlock(genesisBlock, 0L);
        blockValidatorContext.addBlock(modifiedBlock01, 1L);

        // Action
        final BlockValidationResult blockIsValid = blockValidator.validateBlock(modifiedBlock01, 1L);

        // Assert
        Assert.assertFalse(blockIsValid.isValid);
    }

    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_validate_block_that_contains_transaction_that_spends_outputs_in_same_block() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();

        final NetworkTime networkTime = NetworkTime.MAX_VALUE;
        final FakeBlockValidatorContext blockValidatorContext = new FakeBlockValidatorContext(networkTime, upgradeSchedule);

        final BlockValidator blockValidator = new BlockValidator(blockValidatorContext);

        { // Store the blocks and transactions included within the block-under-test so that it should appear valid...
            final Block genesisBlock = inflateBlock(blockInflater, BlockData.MainChain.GENESIS_BLOCK);
            blockValidatorContext.addBlock(genesisBlock, 0L);

            // Block Hash: 00000000689051C09FF2CD091CC4C22C10B965EB8DB3AD5F032621CC36626175
            final Block prerequisiteBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000F5790162A682DDD5086265D254F7F59023D35D07DF7C95DC9779942D00000000193028D8B78007269D52B2A1068E32EDD21D0772C2C157954F7174761B78A51A30CE6E49FFFF001D3A2E34480201000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0804FFFF001D027C05FFFFFFFF0100F2052A01000000434104B43BB206B71F34E2FAB9359B156FF683BED889021A06C315722A7C936B9743AD88A8882DC13EECAFCDAD4F082D2D0CC54AA177204F79DC7305F1F4857B7B8802AC00000000010000000177B5E6E78F8552129D07A73801B1A5F6830EC040D218755B46340B4CF6D21FD7000000004A49304602210083EC8BD391269F00F3D714A54F4DBD6B8004B3E9C91F3078FF4FCA42DA456F4D0221008DFE1450870A717F59A494B77B36B7884381233555F8439DAC4EA969977DD3F401FFFFFFFF0200E1F505000000004341044A656F065871A353F216CA26CEF8DDE2F03E8C16202D2E8AD769F02032CB86A5EB5E56842E92E19141D60A01928F8DD2C875A390F67C1F6C94CFC617C0EA45AFAC00180D8F00000000434104F36C67039006EC4ED2C885D7AB0763FEB5DEB9633CF63841474712E4CF0459356750185FC9D962D0F4A1E08E1A84F0C9A9F826AD067675403C19D752530492DCAC00000000"));
            blockValidatorContext.addBlock(prerequisiteBlock, 545L);
        }

        // Block Hash: 000000005A4DED781E667E06CEEFAFB71410B511FE0D5ADC3E5A27ECBEC34AE6
        final Block block = inflateBlock(blockInflater, "0100000075616236CC2126035FADB38DEB65B9102CC2C41C09CDF29FC051906800000000FE7D5E12EF0FF901F6050211249919B1C0653771832B3A80C66CEA42847F0AE1D4D26E49FFFF001D00F0A4410401000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0804FFFF001D029105FFFFFFFF0100F2052A010000004341046D8709A041D34357697DFCB30A9D05900A6294078012BF3BB09C6F9B525F1D16D5503D7905DB1ADA9501446EA00728668FC5719AA80BE2FDFC8A858A4DBDD4FBAC00000000010000000255605DC6F5C3DC148B6DA58442B0B2CD422BE385EAB2EBEA4119EE9C268D28350000000049483045022100AA46504BAA86DF8A33B1192B1B9367B4D729DC41E389F2C04F3E5C7F0559AAE702205E82253A54BF5C4F65B7428551554B2045167D6D206DFE6A2E198127D3F7DF1501FFFFFFFF55605DC6F5C3DC148B6DA58442B0B2CD422BE385EAB2EBEA4119EE9C268D2835010000004847304402202329484C35FA9D6BB32A55A70C0982F606CE0E3634B69006138683BCD12CBB6602200C28FEB1E2555C3210F1DDDB299738B4FF8BBE9667B68CB8764B5AC17B7ADF0001FFFFFFFF0200E1F505000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC00180D8F000000004341044A656F065871A353F216CA26CEF8DDE2F03E8C16202D2E8AD769F02032CB86A5EB5E56842E92E19141D60A01928F8DD2C875A390F67C1F6C94CFC617C0EA45AFAC0000000001000000025F9A06D3ACDCEB56BE1BFEAA3E8A25E62D182FA24FEFE899D1C17F1DAD4C2028000000004847304402205D6058484157235B06028C30736C15613A28BDB768EE628094CA8B0030D4D6EB0220328789C9A2EC27DDAEC0AD5EF58EFDED42E6EA17C2E1CE838F3D6913F5E95DB601FFFFFFFF5F9A06D3ACDCEB56BE1BFEAA3E8A25E62D182FA24FEFE899D1C17F1DAD4C2028010000004A493046022100C45AF050D3CEA806CEDD0AB22520C53EBE63B987B8954146CDCA42487B84BDD6022100B9B027716A6B59E640DA50A864D6DD8A0EF24C76CE62391FA3EABAF4D2886D2D01FFFFFFFF0200E1F505000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC00180D8F000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC000000000100000002E2274E5FEA1BF29D963914BD301AA63B64DAAF8A3E88F119B5046CA5738A0F6B0000000048473044022016E7A727A061EA2254A6C358376AAA617AC537EB836C77D646EBDA4C748AAC8B0220192CE28BF9F2C06A6467E6531E27648D2B3E2E2BAE85159C9242939840295BA501FFFFFFFFE2274E5FEA1BF29D963914BD301AA63B64DAAF8A3E88F119B5046CA5738A0F6B010000004A493046022100B7A1A755588D4190118936E15CD217D133B0E4A53C3C15924010D5648D8925C9022100AAEF031874DB2114F2D869AC2DE4AE53908FBFEA5B2B1862E181626BB9005C9F01FFFFFFFF0200E1F505000000004341044A656F065871A353F216CA26CEF8DDE2F03E8C16202D2E8AD769F02032CB86A5EB5E56842E92E19141D60A01928F8DD2C875A390F67C1F6C94CFC617C0EA45AFAC00180D8F000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC00000000");
        blockValidatorContext.addBlock(block, 546L);

        // Action
        final Boolean blockIsValid = blockValidator.validateBlock(block, 546L).isValid;

        // Assert
        Assert.assertTrue(blockIsValid);
    }

    @Test
    public void should_not_validate_block_that_spends_its_own_coinbase() {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();

        final Block genesisBlock = inflateBlock(blockInflater, BlockData.MainChain.GENESIS_BLOCK);

        final Block originalBlock01 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain2.BLOCK_1));
        final MutableBlock modifiedBlock01 = new MutableBlock(originalBlock01) {
            @Override
            public Sha256Hash getHash() {
                return originalBlock01.getHash();
            }

            @Override
            public Boolean isValid() {
                return originalBlock01.isValid();
            }
        };

        { // Assert the Block is valid without the Transaction spending its coinbase...
            final FakeBlockValidatorContext blockValidatorContext = new FakeBlockValidatorContext(NetworkTime.MAX_VALUE, upgradeSchedule);
            final BlockValidator blockValidator = new BlockValidator(blockValidatorContext);
            blockValidatorContext.addBlock(genesisBlock, 0L);
            blockValidatorContext.addBlock(modifiedBlock01, 1L);

            final BlockValidationResult blockIsValid = blockValidator.validateBlock(modifiedBlock01, 1L);
            Assert.assertTrue(blockIsValid.isValid);
        }

        final Transaction signedTransaction;
        {
            final AddressInflater addressInflater = new AddressInflater();
            final Transaction transactionToSpend = modifiedBlock01.getCoinbaseTransaction();
            final HashMapTransactionOutputRepository transactionOutputRepository = new HashMapTransactionOutputRepository();
            {
                final Sha256Hash transactionToSpendHash = transactionToSpend.getHash();
                int outputIndex = 0;
                for (final TransactionOutput transactionOutput : transactionToSpend.getTransactionOutputs()) {
                    final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent = new TransactionOutputIdentifier(transactionToSpendHash, outputIndex);
                    transactionOutputRepository.put(transactionOutputIdentifierBeingSpent, transactionOutput);
                    outputIndex += 1;
                }
            }

            final PrivateKey privateKey = PrivateKey.fromHexString("697D9CCCD7A09A31ED41C1D1BFF35E2481098FB03B4E73FAB7D4C15CF01FADCC");
            final MutableTransaction unsignedTransaction = BlockValidatorTests.createTransactionContaining(
                BlockValidatorTests.createTransactionInputThatSpendsTransaction(transactionToSpend),
                BlockValidatorTests.createTransactionOutput(addressInflater.fromBase58Check("1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW"), (1L * Transaction.SATOSHIS_PER_BITCOIN))
            );

            { // Sign the transaction...
                Transaction partiallySignedTransaction = unsignedTransaction;
                final TransactionSigner transactionSigner = new TransactionSigner();
                final SignatureContext signatureContext = new SignatureContext(partiallySignedTransaction, new HashType(Mode.SIGNATURE_HASH_ALL, true, false), upgradeSchedule); // BCH is not enabled at this block height...

                int inputIndex = 0;
                final List<TransactionInput> transactionInputs = unsignedTransaction.getTransactionInputs();
                for (final TransactionInput transactionInput : transactionInputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    final TransactionOutput transactionOutputBeingSpent = transactionOutputRepository.get(transactionOutputIdentifierBeingSpent);

                    signatureContext.setInputIndexBeingSigned(inputIndex);
                    signatureContext.setShouldSignInputScript(inputIndex, true, transactionOutputBeingSpent);
                    partiallySignedTransaction = transactionSigner.signTransaction(signatureContext, privateKey, false);
                }

                signedTransaction = partiallySignedTransaction;
            }
        }

        modifiedBlock01.addTransaction(signedTransaction);

        final FakeBlockValidatorContext blockValidatorContext = new FakeBlockValidatorContext(NetworkTime.MAX_VALUE, upgradeSchedule);
        final BlockValidator blockValidator = new BlockValidator(blockValidatorContext);
        blockValidatorContext.addBlock(genesisBlock, 0L);
        blockValidatorContext.addBlock(modifiedBlock01, 1L);

        { // Ensure the fake transaction that would normally be valid on its own...
            { // Add the 101st block so so that the signed transaction has a medianBlockTime when bypassing the coinbase maturity validation...
                final Block block101 = blockInflater.fromBytes(ByteArray.fromHexString("010000009A22DB7FD25E719ABF9E8CCF869FBBC1E22FA71822A37EFAE054C17B00000000F7A5D0816883EC2F4D237082B47B4D3A6A26549D65AC50D8527B67AB4CB7E6CFADAA6949FFFF001D15FA87F60101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D014EFFFFFFFF0100F2052A01000000434104181D31D7160779E75231F7647F91E53D633839EB9CE3FE096EC522719CC1B9DA0237CB9941A059579BEE26692A90344417069391A6AA1E4680CAA4580A7AB9F3AC00000000"));
                blockValidatorContext.addBlock(block101, 101L);
            }

            final TransactionValidator transactionValidator = new TransactionValidatorCore(blockValidatorContext);
            final TransactionValidationResult transactionValidationResult = transactionValidator.validateTransaction(102L, signedTransaction);
            Assert.assertTrue(transactionValidationResult.isValid);
        }

        // Action
        final BlockValidationResult blockIsValid = blockValidator.validateBlock(modifiedBlock01, 1L);

        // Assert
        Assert.assertFalse(blockIsValid.isValid);
    }

    @Test
    public void difficulty_should_be_recalculated_every_2016th_block() throws Exception {
        // Setup
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        final BlockInflater blockInflater = new BlockInflater();

        final FakeBlockValidatorContext blockValidatorContext = new FakeBlockValidatorContext(NetworkTime.MAX_VALUE, upgradeSchedule);
        final BlockValidator blockValidator = new BlockValidator(blockValidatorContext);

        final Block genesisBlock = inflateBlock(blockInflater, BlockData.MainChain.GENESIS_BLOCK);
        blockValidatorContext.addBlock(genesisBlock, 0L);

        { // Store the block that is 2016 blocks before the firstBlockWithDifficultyAdjustment
            // Block Hash: 000000000FA8BFA0F0DD32F956B874B2C7F1772C5FBEDCB1B35E03335C7FB0A8
            // Block Height: 30240
            // Timestamp: B1512B4B
            // NOTE: This block is the block referenced when calculating the elapsed time since the previous update...
            final Block block30240 = inflateBlock(blockInflater, IoUtil.getResource("/blocks/000000000FA8BFA0F0DD32F956B874B2C7F1772C5FBEDCB1B35E03335C7FB0A8"));


            blockValidatorContext.addBlock(block30240, 30240L);

            final Difficulty blockDifficulty = block30240.getDifficulty();
            Assert.assertEquals(Difficulty.BASE_DIFFICULTY, blockDifficulty);
        }

        // Timestamp: 23EC3A4B
        // Block Height: 4031
        // Block Hash: 00000000984F962134A7291E3693075AE03E521F0EE33378EC30A334D860034B
        { // Store the previous block so the firstBlockWithDifficultyAdjustment has the correct hash...
            final String blockData = IoUtil.getResource("/blocks/00000000984F962134A7291E3693075AE03E521F0EE33378EC30A334D860034B");
            final Block previousBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));

            blockValidatorContext.addBlock(previousBlock, 4031L);

            final Difficulty blockDifficulty = previousBlock.getDifficulty();
            Assert.assertEquals(Difficulty.BASE_DIFFICULTY, blockDifficulty);
        }

        blockValidatorContext.addBlockHeader(inflateBlockHeader(blockHeaderInflater, "0100000049C1DAAB3B6536FF1B2633C3A316A6E06EC287676CDEEC4CA7BAAE6B00000000AC10B36B8F354B3353207DE15940A5EDBC05BB8364AF75B4B5409E7823F2B48923EC3A4BFFFF001DBD5FA412"), 32255L, MedianBlockTime.fromSeconds(1262150129L), null);

        final Difficulty expectedDifficulty = new ImmutableDifficulty(ByteArray.fromHexString("00D86A"), Difficulty.BASE_DIFFICULTY_EXPONENT);
        final float expectedDifficultyRatio = 1.18F;
        // Original Block with the first difficulty adjustment: 000000004F2886A170ADB7204CB0C7A824217DD24D11A74423D564C4E0904967 (Block Height: 32256)
        final String blockData = IoUtil.getResource("/blocks/000000004F2886A170ADB7204CB0C7A824217DD24D11A74423D564C4E0904967");

        final Block firstBlockWithDifficultyIncrease;
        {
            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(blockData));
            firstBlockWithDifficultyIncrease = block;
            blockValidatorContext.addBlock(block, 32256L);
        }

        final Difficulty blockDifficulty = firstBlockWithDifficultyIncrease.getDifficulty();
        Assert.assertEquals(expectedDifficulty, blockDifficulty);
        Assert.assertEquals(expectedDifficultyRatio, blockDifficulty.getDifficultyRatio().floatValue(), 0.005);

        final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(blockValidatorContext);
        final Difficulty calculatedNextDifficulty = difficultyCalculator.calculateRequiredDifficulty(32256L);
        Assert.assertEquals(expectedDifficulty, calculatedNextDifficulty);

        // Action
        final Boolean blockIsValid = blockValidator.validateBlock(firstBlockWithDifficultyIncrease, 32256L).isValid;

        // Assert
        Assert.assertTrue(blockIsValid);
    }

    @Test
    public void should_not_validate_block_that_contains_a_duplicate_transaction() throws Exception {
        // Setup
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
        final BlockInflater blockInflater = new BlockInflater();
        final AddressInflater addressInflater = new AddressInflater();

        final FakeBlockValidatorContext blockValidatorContext = new FakeBlockValidatorContext(NetworkTime.MAX_VALUE, upgradeSchedule);
        final BlockValidator blockValidator = new BlockValidator(blockValidatorContext);

        final HashMapTransactionOutputRepository transactionOutputRepository = new HashMapTransactionOutputRepository();

        {
            final Block block = inflateBlock(blockInflater, BlockData.MainChain.GENESIS_BLOCK);
            blockValidatorContext.addBlock(block, 0L);
        }

        final Block modifiedBlock01;
        final Transaction transactionToSpend;
        final TransactionOutputIdentifier outputIdentifierToSpend;
        final TransactionOutput outputToSpend;
        { // Inflate a modified version of block 1 with a UTXO we can spend...
            final TransactionInflater transactionInflater = new TransactionInflater();
            // This transaction is actually the coinbase from ForkChain2.Block1. It is unlocked via 697D9CCCD7A09A31ED41C1D1BFF35E2481098FB03B4E73FAB7D4C15CF01FADCC.
            //  The transaction spent in the block-under-test should not be a coinbase since it would not validate due to the maturity rule...
            transactionToSpend = transactionInflater.fromBytes(ByteArray.fromHexString("01000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF230101172F706F6F6C2E626974636F696E76657264652E6F72672F08631A5438BF010000FFFFFFFF0100F2052A010000001976A9147E2277B34EE9E690F696DE9286D2CD5CD84D0FF788AC00000000"));
            outputIdentifierToSpend = new TransactionOutputIdentifier(transactionToSpend.getHash(), 0);
            outputToSpend = transactionToSpend.getTransactionOutputs().get(0);

            final Block originalBlock01 = inflateBlock(blockInflater, BlockData.MainChain.BLOCK_1);

            final Sha256Hash blockHash = originalBlock01.getHash();
            final MutableBlock mutableBlock = new MutableBlock(originalBlock01) {
                @Override
                public Sha256Hash getHash() {
                    return blockHash;
                }

                @Override
                public Boolean isValid() {
                    return true;
                }
            };

            mutableBlock.addTransaction(transactionToSpend);
            blockValidatorContext.addBlock(mutableBlock, 1L, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP), null);
            modifiedBlock01 = mutableBlock;
        }

        transactionOutputRepository.put(outputIdentifierToSpend, outputToSpend);

        final PrivateKey privateKey = PrivateKey.fromHexString("697D9CCCD7A09A31ED41C1D1BFF35E2481098FB03B4E73FAB7D4C15CF01FADCC");
        final MutableBlock mutableBlock;
        {
            mutableBlock = new MutableBlock(inflateBlock(blockInflater, BlockData.MainChain.BLOCK_2)) {
                @Override
                public Sha256Hash getHash() {
                    return Sha256Hash.fromHexString("0000000082B5015589A3FDF2D4BAFF403E6F0BE035A5D9742C1CAE6295464449"); // Block 3's hash (arbitrary)...
                }

                @Override
                public Boolean isValid() {
                    return true;
                }
            };

            mutableBlock.setPreviousBlockHash(modifiedBlock01.getHash());
        }

        final Transaction signedTransaction;
        {
            final MutableTransaction unsignedTransaction = BlockValidatorTests.createTransactionContaining(
                BlockValidatorTests.createTransactionInputThatSpendsTransaction(transactionToSpend),
                BlockValidatorTests.createTransactionOutput(addressInflater.fromBase58Check("1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW"), (1L * Transaction.SATOSHIS_PER_BITCOIN))
            );

            { // Sign the transaction...
                Transaction partiallySignedTransaction = unsignedTransaction;
                final TransactionSigner transactionSigner = new TransactionSigner();
                final SignatureContext signatureContext = new SignatureContext(partiallySignedTransaction, new HashType(Mode.SIGNATURE_HASH_ALL, true, false), upgradeSchedule); // BCH is not enabled at this block height...

                int inputIndex = 0;
                final List<TransactionInput> transactionInputs = unsignedTransaction.getTransactionInputs();
                for (final TransactionInput transactionInput : transactionInputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifierBeingSpent = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    final TransactionOutput transactionOutputBeingSpent = transactionOutputRepository.get(transactionOutputIdentifierBeingSpent);

                    signatureContext.setInputIndexBeingSigned(inputIndex);
                    signatureContext.setShouldSignInputScript(inputIndex, true, transactionOutputBeingSpent);
                    partiallySignedTransaction = transactionSigner.signTransaction(signatureContext, privateKey);
                }

                signedTransaction = partiallySignedTransaction;
            }
        }

        { // Ensure the fake transaction that will be duplicated would normally be valid on its own...
            final TransactionValidator transactionValidator = new TransactionValidatorCore(blockValidatorContext);
            final TransactionValidationResult transactionValidationResult = transactionValidator.validateTransaction(2L, signedTransaction);
            Assert.assertTrue(transactionValidationResult.isValid);
        }

        mutableBlock.addTransaction(signedTransaction);
        mutableBlock.addTransaction(signedTransaction); // Add the valid transaction twice...

        blockValidatorContext.addBlock(mutableBlock, 2L);

        // Action
        final Boolean blockIsValid = blockValidator.validateBlock(mutableBlock, 2L).isValid;

        // Assert
        Assert.assertFalse(blockIsValid);
    }

    public void should_be_allowed_to_spend_transactions_with_duplicate_identifiers_in_the_same_block() throws Exception {
        /* Excerpt from the current uncertainty surrounding whether or not this is valid:

            ... According to BIP 30, duplicate transactions are only allowed if they've been previously spent
            (except the two grandfathered instances).  ("Blocks are not allowed to contain a transaction whose
            identifier matches that of an earlier, not-fully-spent transaction in the same chain.")

            ...the person controlling the keys for the grandfathered transactions can produce a complicated scenario
            that bends these rules (assuming they're even allowed to spend them...

            Assume the grandfathered duplicate-txid coinbases are "A1" and "A2".  Also let "B1" be a new tx that
            spends one of the A1/A2 outputs (and where "B2" is essentially a duplicate of "B1", spending the prevout
            to the same address as B1 (so B1.txid == B2.txid)).  BIP30 prevents B2 from being accepted unless B1 has
            already been spent.  So once B1 is spent (by "C1"), B2 becomes valid since B1 is now "spent". Normally,
            this isn't a practical use-case since the mempool rejects duplicate txids, but if these transactions
            were broadcast as a block (with ordering being [<Coinbase>, B1, C1, B2]), would it be considered a valid
            block? If so, what happens if/when CTOR is implemented?

            Until clear consensus is decided to handle this situation, Bitcoin Verde considers duplicate transactions in the
            same block an invalid block.

            This scenario is essentially covered via BlockValidatorTests::should_not_validate_block_that_contains_a_duplicate_transaction().
        */
    }

    @Test
    public void should_not_validate_block_with_invalid_coinbase_due_to_extra_inputs() throws Exception {
        final BlockInflater blockInflater = new BlockInflater();
        final Block originalBlock01 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain2.BLOCK_1));

        final CoinbaseTransaction invalidCoinbase;
        { // Coinbase Transaction may only have one TransactionInput...
            final MutableCoinbaseTransaction mutableCoinbaseTransaction = new MutableCoinbaseTransaction(originalBlock01.getCoinbaseTransaction());
            mutableCoinbaseTransaction.addTransactionInput(mutableCoinbaseTransaction.getTransactionInputs().get(0));
            invalidCoinbase = mutableCoinbaseTransaction;
        }

        BlockValidatorTests.assertCoinbaseIsInvalid(invalidCoinbase);
    }

    @Test
    public void should_not_validate_block_with_invalid_coinbase_due_to_invalid_prevout_identifier() throws Exception {
        final BlockInflater blockInflater = new BlockInflater();
        final Block originalBlock01 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain2.BLOCK_1));

        final CoinbaseTransaction invalidCoinbase;
        { // Coinbase Transaction prevout must be 0xFFFFFFFF
            final MutableCoinbaseTransaction mutableCoinbaseTransaction = new MutableCoinbaseTransaction(originalBlock01.getCoinbaseTransaction());
            final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput(mutableCoinbaseTransaction.getTransactionInputs().get(0));
            mutableTransactionInput.setPreviousOutputIndex(0);
            mutableCoinbaseTransaction.setTransactionInput(0, mutableTransactionInput);
            invalidCoinbase = mutableCoinbaseTransaction;
        }

        BlockValidatorTests.assertCoinbaseIsInvalid(invalidCoinbase);
    }

    @Test
    public void should_not_validate_block_with_invalid_coinbase_due_to_invalid_prevout_identifier_2() throws Exception {
        final BlockInflater blockInflater = new BlockInflater();
        final Block originalBlock01 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain2.BLOCK_1));

        final CoinbaseTransaction invalidCoinbase;
        { // Coinbase Transaction prevout hash must be Sha256Hash.EMPTY_HASH
            final MutableCoinbaseTransaction mutableCoinbaseTransaction = new MutableCoinbaseTransaction(originalBlock01.getCoinbaseTransaction());
            final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput(mutableCoinbaseTransaction.getTransactionInputs().get(0));
            mutableTransactionInput.setPreviousOutputTransactionHash(Sha256Hash.fromHexString("0E3E2357E806B6CDB1F70B54C3A3A17B6714EE1F0E68BEBB44A74B1EFD512098"));
            mutableCoinbaseTransaction.setTransactionInput(0, mutableTransactionInput);
            invalidCoinbase = mutableCoinbaseTransaction;
        }

        BlockValidatorTests.assertCoinbaseIsInvalid(invalidCoinbase);
    }

    @Test
    public void should_not_validate_block_with_invalid_coinbase_due_large_input_script() throws Exception {
        final BlockInflater blockInflater = new BlockInflater();
        final Block originalBlock01 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain2.BLOCK_1));

        final CoinbaseTransaction invalidCoinbase;
        { // Coinbase Transaction unlocking script must be no more than 100 bytes...
            final MutableCoinbaseTransaction mutableCoinbaseTransaction = new MutableCoinbaseTransaction(originalBlock01.getCoinbaseTransaction());
            final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput(mutableCoinbaseTransaction.getTransactionInputs().get(0));
            mutableTransactionInput.setUnlockingScript(UnlockingScript.castFrom(new ImmutableScript(ByteArray.wrap(new byte[101]))));
            mutableCoinbaseTransaction.setTransactionInput(0, mutableTransactionInput);
            invalidCoinbase = mutableCoinbaseTransaction;
        }

        BlockValidatorTests.assertCoinbaseIsInvalid(invalidCoinbase);
    }
}
