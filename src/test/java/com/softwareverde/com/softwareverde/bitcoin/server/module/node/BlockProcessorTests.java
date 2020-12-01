package com.softwareverde.com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.context.core.BlockProcessorContext;
import com.softwareverde.bitcoin.context.core.BlockchainBuilderContext;
import com.softwareverde.bitcoin.context.core.PendingBlockLoaderContext;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainBuilder;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainBuilderTests;
import com.softwareverde.bitcoin.server.module.node.sync.blockloader.PendingBlockLoader;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.FakeBlockStore;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.IoUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.MutableNetworkTime;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.ConcurrentHashMap;

public class BlockProcessorTests extends IntegrationTest {
    @Override
    public void before() throws Exception {
        super.before();
    }

    @Override
    public void after() throws Exception {
        super.after();
    }

    protected void _setBlockHeight(final DatabaseConnection databaseConnection, final Sha256Hash blockHash, final Long blockHeight) throws Exception {
        databaseConnection.executeSql(
            new Query("UPDATE blocks SET block_height = ? WHERE hash = ?")
            .setParameter(blockHeight)
            .setParameter(blockHash)
        );
    }

    protected static class FakeBlockInflaters implements BlockInflaters {
        protected final BlockDeflater _blockDeflater = new BlockDeflater();
        protected final ConcurrentHashMap<Sha256Hash, Sha256Hash> _hashMap = new ConcurrentHashMap<Sha256Hash, Sha256Hash>();

        public void defineBlockHash(final Sha256Hash invalidHash, final Sha256Hash validHash) {
            _hashMap.put(invalidHash, validHash);
        }

        @Override
        public BlockInflater getBlockInflater() {
            return new BlockInflater() {
                @Override
                protected MutableBlock _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
                    final Block originalBlock = super._fromByteArrayReader(byteArrayReader);
                    return new MutableBlock(originalBlock) {
                        @Override
                        public Boolean isValid() {
                            return true;
                        }

                        @Override
                        public Sha256Hash getHash() {
                            final Sha256Hash originalBlockHash = originalBlock.getHash();
                            if (_hashMap.containsKey(originalBlockHash)) {
                                return _hashMap.get(originalBlockHash);
                            }

                            return originalBlockHash;
                        }
                    };
                }
            };
        }

        @Override
        public BlockDeflater getBlockDeflater() {
            return _blockDeflater;
        }
    }

    protected static class FakeMutableBlock extends MutableBlock {
        protected final Sha256Hash _masqueradeHash;
        public FakeMutableBlock(final Block block, final Sha256Hash masqueradeHash) {
            super(block);
            _masqueradeHash = masqueradeHash;
        }

        @Override
        public Sha256Hash getHash() {
            return _masqueradeHash;
        }

        public Sha256Hash getInvalidHash() {
            return super.getHash();
        }
    }

    @Test
    public void should_handle_replicated_delayed_deep_reorg_with_synced_headers_with_semi_real_blocks() throws Exception {
        // This test should attempt to setup the scenario encountered on 2020-11-29 where the node fell behind
        //  syncing, during which time a reorg occurred having the node's headBlock on a different blockchain then the
        //  head blockHeader with multiple blocks (and headers) available after the reorg (2+).
        //  BlockHeight: 663701

        final BlockInflater blockInflater = _masterInflater.getBlockInflater();
        final BlockHeaderInflater blockHeaderInflater = _masterInflater.getBlockHeaderInflater();
        final TransactionInflaters transactionInflaters = _masterInflater;
        final FakeBlockInflaters blockInflaters = new FakeBlockInflaters();
        final FullNodeDatabaseManagerFactory databaseManagerFactory = _fullNodeDatabaseManagerFactory;

        final Block genesisBlock = blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.GENESIS_BLOCK));
        final Block asertAnchorBlock = blockInflater.fromBytes(ByteArray.fromHexString(IoUtil.getResource("/blocks/00000000000000000083ED4B7A780D59E3983513215518AD75654BB02DEEE62F")));
        final Block block663700 = blockInflater.fromBytes(ByteArray.fromHexString(IoUtil.getResource("/blocks/0000000000000000035DE0B0AEF620DF19649D10838D53858A1079B2D871AB84")));
        final Block block663701_A = blockInflater.fromBytes(ByteArray.fromHexString(IoUtil.getResource("/blocks/000000000000000001941919BE2A285D053269FD5EEEF46311DBA1130D8486BF")));
        final Block block663701_B = blockInflater.fromBytes(ByteArray.fromHexString(IoUtil.getResource("/blocks/000000000000000003D8E1EFE1E4C339F1A926CAA9F5D20F5C3DA83A0F3BF4B6")));
        final BlockHeader blockHeader663702 = blockHeaderInflater.fromBytes(ByteArray.fromHexString("00004020B6F43B0F3AA83D5C0FD2F5A9CA26A9F139C3E4E1EFE1D8030000000000000000BBC16102412BE4269BEB37054288048290A0510A7ED4286DF83485A52953C550F17EC35F713E041871B228D2"));
        final BlockHeader blockHeader663703 = blockHeaderInflater.fromBytes(ByteArray.fromHexString("0000C0206132919EDC1EEBCD961AD62284B69461A185CD787D3D4E02000000000000000038953887CAD77F89B924BA5FCB15238C6FDA602295538647B4766AB0A873B9F75F7FC35F093D04184D1016AA"));

        Assert.assertNotNull(genesisBlock);
        Assert.assertNotNull(asertAnchorBlock);
        Assert.assertNotNull(block663700);
        Assert.assertNotNull(block663701_A);
        Assert.assertNotNull(block663701_B);
        Assert.assertNotNull(blockHeader663702);
        Assert.assertNotNull(blockHeader663703);

        final FakeMutableBlock anchorShimBlock;
        {
            anchorShimBlock = new FakeMutableBlock(genesisBlock, asertAnchorBlock.getPreviousBlockHash());
            anchorShimBlock.setPreviousBlockHash(BlockHeader.GENESIS_BLOCK_HASH);
            anchorShimBlock.clearTransactions();
            anchorShimBlock.addTransaction(genesisBlock.getCoinbaseTransaction());

            blockInflaters.defineBlockHash(anchorShimBlock.getInvalidHash(), anchorShimBlock.getHash());
        }

        final FakeMutableBlock block663700ShimBlock;
        {
            block663700ShimBlock = new FakeMutableBlock(block663700, block663700.getPreviousBlockHash());
            block663700ShimBlock.setPreviousBlockHash(asertAnchorBlock.getHash());
            block663700ShimBlock.clearTransactions();
            block663700ShimBlock.addTransaction(genesisBlock.getCoinbaseTransaction());

            blockInflaters.defineBlockHash(block663700ShimBlock.getInvalidHash(), block663700ShimBlock.getHash());
        }

        final FakeMutableBlock shimBlock;
        {
            shimBlock = new FakeMutableBlock(block663700, block663700.getPreviousBlockHash());
            shimBlock.setPreviousBlockHash(BlockHeader.GENESIS_BLOCK_HASH);
            shimBlock.clearTransactions();
            shimBlock.addTransaction(block663700.getCoinbaseTransaction());

            blockInflaters.defineBlockHash(shimBlock.getInvalidHash(), shimBlock.getHash());
        }

        final FakeBlockStore blockStore = new FakeBlockStore();
        final BlockchainBuilderTests.FakeBitcoinNodeManager bitcoinNodeManager = new BlockchainBuilderTests.FakeBitcoinNodeManager();

        final BlockProcessorContext blockProcessorContext = new BlockProcessorContext(blockInflaters, transactionInflaters, blockStore, databaseManagerFactory, new MutableNetworkTime(), _synchronizationStatus, _transactionValidatorFactory);
        final PendingBlockLoaderContext pendingBlockLoaderContext = new PendingBlockLoaderContext(blockInflaters, databaseManagerFactory, _threadPool);
        final BlockchainBuilderContext blockchainBuilderContext = new BlockchainBuilderContext(blockInflaters, databaseManagerFactory, bitcoinNodeManager, _threadPool);

        final BlockProcessor blockProcessor = new BlockProcessor(blockProcessorContext);
        final PendingBlockLoader pendingBlockLoader = new PendingBlockLoader(pendingBlockLoaderContext, 1);

        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockHeaderDatabaseManager.storeBlockHeader(genesisBlock);
                blockHeaderDatabaseManager.storeBlockHeader(anchorShimBlock);
                blockHeaderDatabaseManager.storeBlockHeader(asertAnchorBlock);
                blockHeaderDatabaseManager.storeBlockHeader(block663700ShimBlock);
                blockHeaderDatabaseManager.storeBlockHeader(block663700);
                blockHeaderDatabaseManager.storeBlockHeader(block663701_A);
                blockHeaderDatabaseManager.storeBlockHeader(block663701_B);
                blockHeaderDatabaseManager.storeBlockHeader(blockHeader663702);
                blockHeaderDatabaseManager.storeBlockHeader(blockHeader663703);
            }

            synchronized (BlockHeaderDatabaseManager.MUTEX) { // Skip validation for the setup blocks...
                final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
                for (final Block block : new Block[]{ genesisBlock, anchorShimBlock, asertAnchorBlock, shimBlock, block663700 }) {
                    blockDatabaseManager.storeBlock(block);
                }
            }

            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

            for (final Block block : new Block[]{ block663701_A }) {
                pendingBlockDatabaseManager.storeBlock(block);
            }

            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            _setBlockHeight(databaseConnection, anchorShimBlock.getHash(), 661646L);
            _setBlockHeight(databaseConnection, asertAnchorBlock.getHash(), 661647L);
            _setBlockHeight(databaseConnection, block663700ShimBlock.getHash(), 663699L);
            _setBlockHeight(databaseConnection, block663700.getHash(), 663700L);
            _setBlockHeight(databaseConnection, block663701_A.getHash(), 663701L);
            _setBlockHeight(databaseConnection, block663701_B.getHash(), 663701L);
            _setBlockHeight(databaseConnection, blockHeader663702.getHash(), 663702L);
            _setBlockHeight(databaseConnection, blockHeader663703.getHash(), 663703L);


            final BlockchainSegmentId blockchainSegmentId = BlockchainSegmentId.wrap(1L);
            final BlockchainSegmentId blockchainSegmentIdA = BlockchainSegmentId.wrap(2L);
            final BlockchainSegmentId blockchainSegmentIdB = BlockchainSegmentId.wrap(3L);
            Assert.assertEquals(blockchainSegmentId, blockHeaderDatabaseManager.getBlockchainSegmentId(blockHeaderDatabaseManager.getBlockHeaderId(genesisBlock.getHash())));
            Assert.assertEquals(blockchainSegmentId, blockHeaderDatabaseManager.getBlockchainSegmentId(blockHeaderDatabaseManager.getBlockHeaderId(anchorShimBlock.getHash())));
            Assert.assertEquals(blockchainSegmentId, blockHeaderDatabaseManager.getBlockchainSegmentId(blockHeaderDatabaseManager.getBlockHeaderId(asertAnchorBlock.getHash())));
            Assert.assertEquals(blockchainSegmentId, blockHeaderDatabaseManager.getBlockchainSegmentId(blockHeaderDatabaseManager.getBlockHeaderId(block663700ShimBlock.getHash())));
            Assert.assertEquals(blockchainSegmentId, blockHeaderDatabaseManager.getBlockchainSegmentId(blockHeaderDatabaseManager.getBlockHeaderId(block663700.getHash())));
            Assert.assertEquals(blockchainSegmentIdA, blockHeaderDatabaseManager.getBlockchainSegmentId(blockHeaderDatabaseManager.getBlockHeaderId(block663701_A.getHash())));
            Assert.assertEquals(blockchainSegmentIdB, blockHeaderDatabaseManager.getBlockchainSegmentId(blockHeaderDatabaseManager.getBlockHeaderId(block663701_B.getHash())));
            Assert.assertEquals(blockchainSegmentIdB, blockHeaderDatabaseManager.getBlockchainSegmentId(blockHeaderDatabaseManager.getBlockHeaderId(blockHeader663702.getHash())));
            Assert.assertEquals(blockchainSegmentIdB, blockHeaderDatabaseManager.getBlockchainSegmentId(blockHeaderDatabaseManager.getBlockHeaderId(blockHeader663703.getHash())));

            { // Populate the required UTXOs for validation...
                final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
                final MutableList<TransactionOutputIdentifier> requiredUtxos = new MutableList<>();

                { // Add all generated outputs for 700.
                    final List<Transaction> transactions = block663700.getTransactions();
                    for (final Transaction transaction : transactions) {
                        requiredUtxos.addAll(TransactionOutputIdentifier.fromTransactionOutputs(transaction));
                    }
                }

                { // Add all outputs required for 701A.
                    final List<Transaction> transactions = block663701_A.getTransactions();
                    for (final Transaction transaction : transactions) {
                        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                            requiredUtxos.add(TransactionOutputIdentifier.fromTransactionInput(transactionInput));
                            Logger.warn("701A spends: " + TransactionOutputIdentifier.fromTransactionInput(transactionInput));
                        }
                    }
                }

                { // Add all outputs required for 701B.
                    final List<Transaction> transactions = block663701_B.getTransactions();
                    for (final Transaction transaction : transactions) {
                        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                            requiredUtxos.add(TransactionOutputIdentifier.fromTransactionInput(transactionInput));
                        }
                    }
                }

                unspentTransactionOutputDatabaseManager.insertUnspentTransactionOutputs(requiredUtxos, 663700L);
                unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(databaseManagerFactory);
            }
        }

        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();

            { // Process 701A normally.
                final BlockchainBuilder blockchainBuilder = new BlockchainBuilder(blockchainBuilderContext, blockProcessor, pendingBlockLoader, BlockchainBuilderTests.FAKE_DOWNLOAD_STATUS_MONITOR, BlockchainBuilderTests.FAKE_BLOCK_DOWNLOAD_REQUESTER);
                final BlockchainBuilder.StatusMonitor statusMonitor = blockchainBuilder.getStatusMonitor();
                blockchainBuilder.start();
                final int maxSleepCount = 10;
                int sleepCount = 0;
                do {
                    Thread.sleep(250L);
                    sleepCount += 1;

                    if (sleepCount >= maxSleepCount) { throw new RuntimeException("Test execution timeout exceeded."); }
                } while (statusMonitor.getStatus() != SleepyService.Status.SLEEPING);
                blockchainBuilder.stop();
            }

            // Commit the UTXO set after 701A.
            unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(databaseManagerFactory);

            // Action

            // Make 701B available for processing.
            for (final Block block : new Block[]{ block663701_B }) {
                pendingBlockDatabaseManager.storeBlock(block);
            }

            { // Process 701B normally.
                final BlockchainBuilder blockchainBuilder = new BlockchainBuilder(blockchainBuilderContext, blockProcessor, pendingBlockLoader, BlockchainBuilderTests.FAKE_DOWNLOAD_STATUS_MONITOR, BlockchainBuilderTests.FAKE_BLOCK_DOWNLOAD_REQUESTER);
                final BlockchainBuilder.StatusMonitor statusMonitor = blockchainBuilder.getStatusMonitor();
                blockchainBuilder.start();
                final int maxSleepCount = 10;
                int sleepCount = 0;
                do {
                    Thread.sleep(250L);
                    sleepCount += 1;

                    if (sleepCount >= maxSleepCount) { throw new RuntimeException("Test execution timeout exceeded."); }
                } while (statusMonitor.getStatus() != SleepyService.Status.SLEEPING);
                blockchainBuilder.stop();
            }

            // Assert
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            Assert.assertTrue(blockDatabaseManager.hasTransactions(block663701_B.getHash()));
        }
    }
}
