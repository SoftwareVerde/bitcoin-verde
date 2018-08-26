package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DifficultyCalculatorTests extends IntegrationTest {
    @Before
    public void setup() {
        _resetDatabase();
        _resetCache();
    }

    @Test
    public void should_return_default_difficulty_for_block_0() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(databaseConnection);

        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);

        final BlockInflater blockInflater = new BlockInflater();
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));

        final BlockId blockId = blockDatabaseManager.storeBlock(block);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block);

        final BlockChainSegmentId blockChainSegmentId = blockDatabaseManager.getBlockChainSegmentId(blockId);

        // Action
        final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(blockChainSegmentId, block);

        // Assert
        Assert.assertEquals(Difficulty.BASE_DIFFICULTY, difficulty);
    }
}
