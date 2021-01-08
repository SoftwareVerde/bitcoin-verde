package com.softwareverde.bitcoin.rpc;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;

import java.util.HashMap;

public class BlockTemplate implements Jsonable {
    protected static final ByteArray DEFAULT_NONCE_RANGE = ByteArray.fromHexString("00000000FFFFFFFF");

    protected Long _blockHeight;
    protected Long _blockVersion;
    protected Sha256Hash _previousBlockHash;
    protected Difficulty _difficulty;

    // Transaction Data
    protected final MutableList<Transaction> _transactions = new MutableList<>(0);
    protected final HashMap<Sha256Hash, Long> _transactionFees = new HashMap<>(0);
    protected final HashMap<Sha256Hash, Integer> _transactionSignatureOperationCounts = new HashMap<>(0);

    protected Long _coinbaseAmount;
    protected ByteArray _nonceRange = DEFAULT_NONCE_RANGE;
    protected Long _currentTime;
    protected Long _minimumBlockTime;
    protected Long _maxSignatureOperationCount;
    protected Long _maxBlockByteCount;
    protected ByteArray _target;

    // Request Properties
    protected String _coinbaseAuxFlags;
    protected String _longPollId;
    protected final MutableList<String> _capabilities = new MutableList<>(0);
    protected final MutableList<String> _mutableFields = new MutableList<>(0);

    public Long getBlockHeight() {
        return _blockHeight;
    }

    public Difficulty getDifficulty() {
        return _difficulty;
    }

    public List<Transaction> getTransactions() {
        return _transactions;
    }

    public Long getCoinbaseAmount() {
        return _coinbaseAmount;
    }

    public Sha256Hash getPreviousBlockHash() {
        return _previousBlockHash;
    }

    public Integer getTransactionCount() {
        return _transactions.getCount();
    }

    public MutableBlock toBlock() {
        final AddressInflater addressInflater = new AddressInflater();
        final TransactionInflater transactionInflater = new TransactionInflater();

        final MutableBlock block = new MutableBlock();
        block.setVersion(_blockVersion);
        block.setDifficulty(_difficulty);
        block.setPreviousBlockHash(_previousBlockHash);
        block.setTimestamp(_minimumBlockTime);
        block.setNonce(0L);

        final Transaction coinbaseTransaction;
        { // Generate the Coinbase
            final String coinbaseMessage = "0000000000000000000000000000000000000000000000000000000000000000"; // NOTE: CoinbaseMessage must be at least 11 bytes or the transaction will not satisfy the minimum transaction size (100 bytes).
            final PrivateKey privateKey = PrivateKey.fromHexString("0000000000000000000000000000000000000000000000000000000000000001");
            final Address address = addressInflater.fromPrivateKey(privateKey);
            coinbaseTransaction = transactionInflater.createCoinbaseTransaction(_blockHeight, coinbaseMessage, address, _coinbaseAmount);
        }
        block.addTransaction(coinbaseTransaction);

        for (final Transaction transaction : _transactions) {
            block.addTransaction(transaction);
        }

        return block;
    }

    @Override
    public Json toJson() {
        final Json json = new Json();

        final Json capabilitiesJson = new Json(true);
        for (final String capability : _capabilities) {
            capabilitiesJson.add(capability);
        }
        json.put("capabilities", capabilitiesJson);

        json.put("version", _blockVersion);

        final String previousBlockHashString = _previousBlockHash.toString();
        json.put("previousblockhash", previousBlockHashString.toLowerCase());

        final TransactionDeflater transactionDeflater = new TransactionDeflater();
        final Json transactionsJson = new Json(true);
        for (final Transaction transaction : _transactions) {
            final Json transactionJson = new Json(false);
            final ByteArray transactionBytes = transactionDeflater.toBytes(transaction);
            final String transactionBytesString = transactionBytes.toString();
            transactionJson.put("data", transactionBytesString.toLowerCase());

            final Sha256Hash transactionHash = transaction.getHash();
            final String transactionHashString = transactionHash.toString();
            transactionJson.put("txid", transactionHashString.toLowerCase());
            transactionJson.put("hash", transactionHashString.toLowerCase());

            final Long transactionFees = _transactionFees.get(transactionHash);
            transactionJson.put("fee", transactionFees);

            final Integer operationCount = _transactionSignatureOperationCounts.get(transactionHash);
            transactionJson.put("sigops", operationCount);

            transactionsJson.add(transactionJson);
        }
        json.put("transactions", transactionsJson);

        final Json coinbaseAuxJson = new Json(false);
        coinbaseAuxJson.put("flags", _coinbaseAuxFlags);
        json.put("coinbaseaux", coinbaseAuxJson);

        json.put("coinbasevalue", _coinbaseAmount);

        json.put("longpollid", _longPollId);

        final String targetString = _target.toString();
        json.put("target", targetString.toLowerCase());

        json.put("mintime", _minimumBlockTime);

        final Json mutableJson = new Json(true);
        for (final String mutableField : _mutableFields) {
            mutableJson.add(mutableField);
        }
        json.put("mutable", mutableJson);

        final String nonceRangeString = _nonceRange.toString();
        json.put("noncerange", nonceRangeString.toLowerCase());

        json.put("sigoplimit", _maxSignatureOperationCount);

        json.put("sizelimit", _maxBlockByteCount);

        json.put("curtime", _currentTime);

        final ByteArray encodedDifficulty = _difficulty.encode();
        final String difficultyString = encodedDifficulty.toString();
        json.put("bits", difficultyString.toLowerCase());

        json.put("height", _blockHeight);

        return json;
    }
}
