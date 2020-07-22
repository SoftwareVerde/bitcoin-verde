package com.softwareverde.bitcoin.server.stratum.task;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.ImmutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.merkleroot.MutableMerkleRoot;
import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.json.Json;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.type.time.SystemTime;

public class StratumMineBlockTask {
    protected List<String> _merkleTreeBranches; // Little-endian merkle tree (intermediary) branch hashes...

    protected final ByteArray _id;
    protected final Long _idLong;
    protected final Block _prototypeBlock;
    protected final String _extraNonce1;
    protected final String _coinbaseTransactionHead;
    protected final String _coinbaseTransactionTail;
    protected final Long _timestampInSeconds;

    // Creates the partialMerkleTree Json as little-endian hashes...
    protected void _buildMerkleTreeBranches() {
        final ImmutableListBuilder<String> listBuilder = new ImmutableListBuilder<String>();
        final List<Sha256Hash> partialMerkleTree = _prototypeBlock.getPartialMerkleTree(0);
        for (final Sha256Hash hash : partialMerkleTree) {
            final String hashString = hash.toString();
            listBuilder.add(BitcoinUtil.reverseEndianString(hashString));
        }
        _merkleTreeBranches = listBuilder.build();
    }

    public static MerkleRoot calculateMerkleRoot(final Transaction coinbaseTransaction, final List<String> merkleTreeBranches) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byte[] merkleRoot = coinbaseTransaction.getHash().toReversedEndian().getBytes();

        for (final String merkleBranch : merkleTreeBranches) {
            final byte[] concatenatedHashes;
            {
                byteArrayBuilder.appendBytes(merkleRoot);
                byteArrayBuilder.appendBytes(HexUtil.hexStringToByteArray(merkleBranch));
                concatenatedHashes = byteArrayBuilder.build();
                byteArrayBuilder.clear();
            }

            merkleRoot = HashUtil.doubleSha256(concatenatedHashes);
        }

        return MutableMerkleRoot.wrap(ByteUtil.reverseEndian(merkleRoot));
    }

    protected Transaction _assembleCoinbaseTransaction(final String stratumExtraNonce2) {
        final TransactionInflater transactionInflater = new TransactionInflater();
        return transactionInflater.fromBytes(HexUtil.hexStringToByteArray(
            _coinbaseTransactionHead + _extraNonce1 + stratumExtraNonce2 + _coinbaseTransactionTail
        ));
    }

    protected BlockHeader _assembleBlockHeader(final String stratumNonce, final String stratumExtraNonce2, final String stratumTimestamp) {
        final Transaction coinbaseTransaction = _assembleCoinbaseTransaction(stratumExtraNonce2);
        return _assembleBlockHeader(stratumNonce, coinbaseTransaction, stratumTimestamp);
    }

    protected BlockHeader _assembleBlockHeader(final String stratumNonce, final Transaction coinbaseTransaction, final String stratumTimestamp) {
        final MutableBlockHeader blockHeader = new MutableBlockHeader(_prototypeBlock);

        final MerkleRoot merkleRoot = calculateMerkleRoot(coinbaseTransaction, _merkleTreeBranches);
        blockHeader.setMerkleRoot(merkleRoot);

        blockHeader.setNonce(ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(stratumNonce)));

        final Long timestamp = ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(stratumTimestamp));
        blockHeader.setTimestamp(timestamp);

        return blockHeader;
    }

    protected RequestMessage _createRequest(final Boolean abandonOldJobs) {
        final RequestMessage mineBlockMessage = new RequestMessage(RequestMessage.ServerCommand.NOTIFY.getValue());

        final Json parametersJson = new Json(true);
        parametersJson.add(_id);
        parametersJson.add(StratumUtil.swabHexString(BitcoinUtil.reverseEndianString(HexUtil.toHexString(_prototypeBlock.getPreviousBlockHash().getBytes()))));
        parametersJson.add(_coinbaseTransactionHead);
        parametersJson.add(_coinbaseTransactionTail);

        final Json partialMerkleTreeJson = new Json(true);

        for (final String hashString : _merkleTreeBranches) {
            partialMerkleTreeJson.add(hashString);
        }

        parametersJson.add(partialMerkleTreeJson);

        parametersJson.add(HexUtil.toHexString(ByteUtil.integerToBytes(_prototypeBlock.getVersion())));
        parametersJson.add(_prototypeBlock.getDifficulty().encode());
        parametersJson.add(HexUtil.toHexString(ByteUtil.integerToBytes(_timestampInSeconds)));
        parametersJson.add(abandonOldJobs);

        mineBlockMessage.setParameters(parametersJson);

        return mineBlockMessage;
    }

    public StratumMineBlockTask(final ByteArray id, final Block prototypeBlock, final String coinbaseTransactionHead, final String coinbaseTransactionTail, final String extraNonce1) {
        _id = id.asConst();
        _prototypeBlock = prototypeBlock.asConst();
        _coinbaseTransactionHead = coinbaseTransactionHead;
        _coinbaseTransactionTail = coinbaseTransactionTail;
        _extraNonce1 = extraNonce1;

        final SystemTime systemTime = new SystemTime();
        _timestampInSeconds = systemTime.getCurrentTimeInSeconds();

        _idLong = ByteUtil.bytesToLong(_id.getBytes());

        _buildMerkleTreeBranches();
    }

    public Long getId() {
        return _idLong;
    }

    public String getExtraNonce() {
        return _extraNonce1;
    }

    public Difficulty getDifficulty() {
        return _prototypeBlock.getDifficulty();
    }

    public Long getTimestamp() {
        return _timestampInSeconds;
    }

    public BlockHeader assembleBlockHeader(final String stratumNonce, final String stratumExtraNonce2, final String stratumTimestamp) {
        final MutableBlockHeader blockHeader = new MutableBlockHeader(_prototypeBlock);

        final TransactionInflater transactionInflater = new TransactionInflater();

        final MerkleRoot merkleRoot;
        {
            final Transaction coinbaseTransaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray(
                _coinbaseTransactionHead + _extraNonce1 + stratumExtraNonce2 + _coinbaseTransactionTail
            ));

            merkleRoot = calculateMerkleRoot(coinbaseTransaction, _merkleTreeBranches);
        }
        blockHeader.setMerkleRoot(merkleRoot);

        blockHeader.setNonce(ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(stratumNonce)));

        final Long timestamp = ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(stratumTimestamp));
        blockHeader.setTimestamp(timestamp);

        return blockHeader;
    }

    public Block assembleBlock(final String stratumNonce, final String stratumExtraNonce2, final String stratumTimestamp) {
        final Transaction coinbaseTransaction = _assembleCoinbaseTransaction(stratumExtraNonce2);

        final BlockHeader blockHeader = _assembleBlockHeader(stratumNonce, coinbaseTransaction, stratumTimestamp);
        final List<Transaction> transactions;
        {
            final List<Transaction> prototypeBlockTransaction = _prototypeBlock.getTransactions();
            final MutableList<Transaction> mutableList = new MutableList<Transaction>(prototypeBlockTransaction);
            mutableList.set(0, coinbaseTransaction);
            transactions = mutableList;
        }
        return new ImmutableBlock(blockHeader, transactions);
    }

    public RequestMessage createRequest(final Boolean abandonOldJobs) {
        return _createRequest(abandonOldJobs);
    }

}
