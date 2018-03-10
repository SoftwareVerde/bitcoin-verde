package com.softwareverde.bitcoin.transaction.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.transaction.signer.SignatureContextGenerator;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.bitcoin.type.address.Address;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.type.key.PrivateKey;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TransactionValidatorTests extends IntegrationTest {
    protected Long _storeBlock(final String blockBytes) throws Exception {
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockInflater blockInflater = new BlockInflater();
        final Block block = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(blockBytes));
        blockDatabaseManager.storeBlock(block);
        return blockDatabaseManager.getBlockIdFromHash(block.getHash());
    }

    @Before
    public void setup() {
        _resetDatabase();
    }

    @Test
    public void should_validate_valid_transaction() throws Exception {
        // Setup
        final TransactionInflater transactionInflater = new TransactionInflater();
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final TransactionValidator transactionValidator = new TransactionValidator(databaseConnection);

        { // Store the transaction output being spent by the transaction...
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);
            final Long blockId = _storeBlock(BlockData.MainChain.BLOCK_1);
            final Transaction previousTransaction = transactionInflater.fromBytes(BitcoinUtil.hexStringToByteArray("0100000001E7FCF39EE6B86F1595C55B16B60BF4F297988CB9519F5D42597E7FB721E591C6010000008B483045022100AC572B43E78089851202CFD9386750B08AFC175318C537F04EB364BF5A0070D402203F0E829D4BAEA982FEAF987CB9F14C85097D2FBE89FBA3F283F6925B3214A97E0141048922FA4DC891F9BB39F315635C03E60E019FF9EC1559C8B581324B4C3B7589A57550F9B0B80BC72D0F959FDDF6CA65F07223C37A8499076BD7027AE5C325FAC5FFFFFFFF0140420F00000000001976A914C4EB47ECFDCF609A1848EE79ACC2FA49D3CAAD7088AC00000000"));
            transactionDatabaseManager.storeTransaction(blockId, previousTransaction);
        }

        final byte[] transactionBytes = BitcoinUtil.hexStringToByteArray("01000000010B6072B386D4A773235237F64C1126AC3B240C84B917A3909BA1C43DED5F51F4000000008C493046022100BB1AD26DF930A51CCE110CF44F7A48C3C561FD977500B1AE5D6B6FD13D0B3F4A022100C5B42951ACEDFF14ABBA2736FD574BDB465F3E6F8DA12E2C5303954ACA7F78F3014104A7135BFE824C97ECC01EC7D7E336185C81E2AA2C41AB175407C09484CE9694B44953FCB751206564A9C24DD094D42FDBFDD5AAD3E063CE6AF4CFAAEA4EA14FBBFFFFFFFF0140420F00000000001976A91439AA3D569E06A1D7926DC4BE1193C99BF2EB9EE088AC00000000");
        final Transaction transaction = transactionInflater.fromBytes(transactionBytes);

        // Action
        final Boolean inputsAreUnlocked = transactionValidator.validateTransactionInputsAreUnlocked(transaction);

        // Assert
        Assert.assertTrue(inputsAreUnlocked);
    }

    @Test
    public void should_create_signed_transaction_and_unlock_it() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final TransactionValidator transactionValidator = new TransactionValidator(databaseConnection);
        final PrivateKey privateKey = PrivateKey.createNewKey();

        final TransactionOutput transactionOutputToSpend; {
            final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();
            transactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);
            transactionOutput.setIndex(0);
            transactionOutput.setLockingScript((ScriptBuilder.payToAddress(Address.fromPrivateKey(privateKey))));
            transactionOutputToSpend = transactionOutput;
        }

        final Transaction transactionToSpend; {
            final TransactionInput transactionInput; {
                final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
                mutableTransactionInput.setPreviousTransactionOutputHash(new MutableHash());
                mutableTransactionInput.setPreviousTransactionOutputIndex(0);
                mutableTransactionInput.setSequenceNumber(TransactionInput.MAX_SEQUENCE_NUMBER);
                mutableTransactionInput.setUnlockingScript((new ScriptBuilder()).pushString("Mined via Bitcoin-Verde.").build());
                transactionInput = mutableTransactionInput;
            }

            final MutableTransaction mutableTransaction = new MutableTransaction();
            mutableTransaction.setVersion(1);
            mutableTransaction.setLockTime(new ImmutableLockTime(LockTime.MIN_TIMESTAMP));
            mutableTransaction.setHasWitnessData(false);
            mutableTransaction.addTransactionOutput(transactionOutputToSpend);
            mutableTransaction.addTransactionInput(transactionInput);
            transactionToSpend = mutableTransaction;
        }

        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);
        final Long blockId = _storeBlock(BlockData.MainChain.BLOCK_1);
        transactionDatabaseManager.storeTransaction(blockId, transactionToSpend);

        final Transaction transaction; {
            final TransactionOutput newTransactionOutput; {
                final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();
                transactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);
                transactionOutput.setIndex(0);
                transactionOutput.setLockingScript(ScriptBuilder.payToAddress("1HrXm9WZF7LBm3HCwCBgVS3siDbk5DYCuW"));
                newTransactionOutput = transactionOutput;
            }

            final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput(); {
                mutableTransactionInput.setPreviousTransactionOutputHash(transactionToSpend.getHash());
                mutableTransactionInput.setPreviousTransactionOutputIndex(0);
                mutableTransactionInput.setSequenceNumber(TransactionInput.MAX_SEQUENCE_NUMBER);
                mutableTransactionInput.setUnlockingScript(Script.EMPTY_SCRIPT);
            }

            final MutableTransaction mutableTransaction = new MutableTransaction(); {
                mutableTransaction.setVersion(1);
                mutableTransaction.setLockTime(new ImmutableLockTime(LockTime.MIN_TIMESTAMP));
                mutableTransaction.setHasWitnessData(false);

                mutableTransaction.addTransactionInput(mutableTransactionInput);
                mutableTransaction.addTransactionOutput(newTransactionOutput);
            }

            final SignatureContextGenerator signatureContextGenerator = new SignatureContextGenerator(databaseConnection);
            final SignatureContext signatureContext = signatureContextGenerator.createContextForEntireTransaction(mutableTransaction);

            final TransactionSigner transactionSigner = new TransactionSigner();
            transaction = transactionSigner.signTransaction(signatureContext, privateKey);
        }

        // Action
        final Boolean inputsAreUnlocked = transactionValidator.validateTransactionInputsAreUnlocked(transaction);

        // Assert
        Assert.assertTrue(inputsAreUnlocked);
    }

    
}
