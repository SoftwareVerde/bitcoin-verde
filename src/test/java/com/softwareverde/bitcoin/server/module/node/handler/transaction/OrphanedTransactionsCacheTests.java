package com.softwareverde.bitcoin.server.module.node.handler.transaction;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.security.secp256k1.key.PrivateKey;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

public class OrphanedTransactionsCacheTests extends IntegrationTest {
    @Before
    public void setup() {
        _resetDatabase();
    }

    protected MutableTransactionOutput _createTransactionOutput(final Address payToAddress) {
        final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();
        transactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);
        transactionOutput.setIndex(0);
        transactionOutput.setLockingScript(ScriptBuilder.payToAddress(payToAddress));
        return transactionOutput;
    }

    protected Transaction _createTransaction(final PrivateKey privateKey, final TransactionOutputIdentifier outputBeingSpent, final TransactionOutput transactionOutput) {
        final AddressInflater addressInflater = new AddressInflater();
        final MutableTransaction transaction = new MutableTransaction();
        transaction.setVersion(Transaction.VERSION);
        transaction.setLockTime(LockTime.MIN_TIMESTAMP);
        {
            final MutableTransactionInput transactionInput = new MutableTransactionInput();
            transactionInput.setPreviousOutputTransactionHash(outputBeingSpent.getTransactionHash());
            transactionInput.setPreviousOutputIndex(outputBeingSpent.getOutputIndex());
            transactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
            transactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
            transaction.addTransactionInput(transactionInput);
        }
        transaction.addTransactionOutput(_createTransactionOutput(addressInflater.uncompressedFromPrivateKey(privateKey)));

        final SignatureContext signatureContext = new SignatureContext(transaction, new HashType(Mode.SIGNATURE_HASH_ALL, true, false), Long.MAX_VALUE);
        signatureContext.setShouldSignInputScript(0, true, transactionOutput);
        final TransactionSigner transactionSigner = new TransactionSigner();
        return transactionSigner.signTransaction(signatureContext, privateKey);
    }

    @Test
    public void should_queue_transaction_whose_output_cannot_be_found() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final BlockInflater blockInflater = new BlockInflater();
            final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
            final Transaction genesisBlockCoinbase = genesisBlock.getCoinbaseTransaction();

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockDatabaseManager.insertBlock(genesisBlock);
            }

            final PrivateKey privateKey = PrivateKey.createNewKey();
            final Transaction parentTransaction = _createTransaction(privateKey, new TransactionOutputIdentifier(genesisBlockCoinbase.getHash(), 0), genesisBlockCoinbase.getTransactionOutputs().get(0)); // NOTE: This transaction does not properly unlock its output...
            final Transaction childTransaction = _createTransaction(privateKey, new TransactionOutputIdentifier(parentTransaction.getHash(), 0), parentTransaction.getTransactionOutputs().get(0));

            final OrphanedTransactionsCache orphanedTransactionsCache = new OrphanedTransactionsCache();

            orphanedTransactionsCache.add(childTransaction, databaseManager);

            Assert.assertNull(transactionDatabaseManager.getTransactionId(childTransaction.getHash()));

            // Action
            transactionDatabaseManager.storeTransaction(parentTransaction);
            final Set<Transaction> possiblyValidTransactions = orphanedTransactionsCache.onTransactionAdded(parentTransaction);

            // Assert
            Assert.assertNotNull(possiblyValidTransactions);
            Assert.assertEquals(1, possiblyValidTransactions.size());
            Assert.assertEquals(childTransaction.getHash(), possiblyValidTransactions.iterator().next().getHash());
        }
    }
}
