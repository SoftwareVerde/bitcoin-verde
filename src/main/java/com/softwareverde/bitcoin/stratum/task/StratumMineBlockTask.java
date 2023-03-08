package com.softwareverde.bitcoin.stratum.task;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.ImmutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.merkleroot.MutableMerkleRoot;
import com.softwareverde.bitcoin.stratum.message.server.MinerNotifyMessage;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.type.time.SystemTime;

public class StratumMineBlockTask {
    protected List<Sha256Hash> _merkleTreeBranches; // Little-endian merkle tree (intermediary) branch hashes...

    protected final ByteArray _id;
    protected final Long _idLong;
    protected final Long _blockHeight;
    protected final Block _prototypeBlock;
    protected final String _extraNonce1;
    protected final ByteArray _coinbaseTransactionHead;
    protected final ByteArray _coinbaseTransactionTail;
    protected final Long _timestampInSeconds;

    // Creates the partialMerkleTree Json as little-endian hashes...
    protected void _buildMerkleTreeBranches() {
        final ImmutableListBuilder<Sha256Hash> listBuilder = new ImmutableListBuilder<>();
        final List<Sha256Hash> partialMerkleTree = _prototypeBlock.getPartialMerkleTree(0);
        for (final Sha256Hash transactionHash : partialMerkleTree) {
            listBuilder.add(transactionHash.toReversedEndian());
        }
        _merkleTreeBranches = listBuilder.build();
    }

    public static MerkleRoot calculateMerkleRoot(final Transaction coinbaseTransaction, final List<Sha256Hash> littleEndianMerkleTreeBranches) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();

        byte[] merkleRoot = coinbaseTransaction.getHash().toReversedEndian().getBytes();

        for (final ByteArray merkleBranch : littleEndianMerkleTreeBranches) {
            final byte[] concatenatedHashes;
            {
                byteArrayBuilder.appendBytes(merkleRoot);
                byteArrayBuilder.appendBytes(merkleBranch);
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

        final MerkleRoot merkleRoot = StratumMineBlockTask.calculateMerkleRoot(coinbaseTransaction, _merkleTreeBranches);
        blockHeader.setMerkleRoot(merkleRoot);

        blockHeader.setNonce(ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(stratumNonce)));

        final Long timestamp = ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(stratumTimestamp));
        blockHeader.setTimestamp(timestamp);

        return blockHeader;
    }

    protected MinerNotifyMessage _createRequest(final Boolean abandonOldJobs) {
        final Sha256Hash previousBlockHash = _prototypeBlock.getPreviousBlockHash();

        final MinerNotifyMessage minerNotifyMessage = new MinerNotifyMessage();
        minerNotifyMessage.setJobId(_id);
        minerNotifyMessage.setPreviousBlockHash(previousBlockHash);
        minerNotifyMessage.setCoinbaseTransactionHead(_coinbaseTransactionHead);
        minerNotifyMessage.setCoinbaseTransactionTail(_coinbaseTransactionTail);
        minerNotifyMessage.setLittleEndianMerkleTreeBranches(_merkleTreeBranches);
        minerNotifyMessage.setBlockVersion(_prototypeBlock.getVersion());
        minerNotifyMessage.setBlockDifficulty(_prototypeBlock.getDifficulty());
        minerNotifyMessage.setBlockTimestamp(_timestampInSeconds);
        minerNotifyMessage.setShouldAbandonOldJobs(abandonOldJobs);

        return minerNotifyMessage;
    }

    public StratumMineBlockTask(final ByteArray id, final Long blockHeight, final Block prototypeBlock, final ByteArray coinbaseTransactionHead, final ByteArray coinbaseTransactionTail, final String extraNonce1) {
        this(id, blockHeight, prototypeBlock, coinbaseTransactionHead, coinbaseTransactionTail, extraNonce1, new SystemTime());
    }

    public StratumMineBlockTask(final ByteArray id, final Long blockHeight, final Block prototypeBlock, final ByteArray coinbaseTransactionHead, final ByteArray coinbaseTransactionTail, final String extraNonce1, final SystemTime systemTime) {
        _id = id.asConst();
        _blockHeight = blockHeight;
        _prototypeBlock = prototypeBlock.asConst();
        _coinbaseTransactionHead = coinbaseTransactionHead;
        _coinbaseTransactionTail = coinbaseTransactionTail;
        _extraNonce1 = extraNonce1;

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

            merkleRoot = StratumMineBlockTask.calculateMerkleRoot(coinbaseTransaction, _merkleTreeBranches);
        }
        blockHeader.setMerkleRoot(merkleRoot);

        blockHeader.setNonce(ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(stratumNonce)));

        final Long timestamp = ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(stratumTimestamp));
        blockHeader.setTimestamp(timestamp);

        return blockHeader;
    }

    public Block assembleBlockTemplate(final Integer nonceByteCount, final Integer extraNonce2ByteCount) {
        final String extraNonce2String = (new MutableByteArray(extraNonce2ByteCount)).toString();
        final Transaction coinbaseTransaction = _assembleCoinbaseTransaction(extraNonce2String);

        final String stratumTimestamp = HexUtil.toHexString(ByteUtil.longToBytes(_timestampInSeconds));
        final String stratumNonceString = (new MutableByteArray(nonceByteCount)).toString();
        final BlockHeader blockHeader = _assembleBlockHeader(stratumNonceString, coinbaseTransaction, stratumTimestamp);
        final List<Transaction> transactions;
        {
            final List<Transaction> prototypeBlockTransaction = _prototypeBlock.getTransactions();
            final MutableList<Transaction> mutableList = new MutableList<>(prototypeBlockTransaction);
            mutableList.set(0, coinbaseTransaction);
            transactions = mutableList;
        }
        return new ImmutableBlock(blockHeader, transactions);
    }

    public Block assembleBlock(final String stratumNonce, final String stratumExtraNonce2, final String stratumTimestamp) {
        final Transaction coinbaseTransaction = _assembleCoinbaseTransaction(stratumExtraNonce2);

        final BlockHeader blockHeader = _assembleBlockHeader(stratumNonce, coinbaseTransaction, stratumTimestamp);
        final List<Transaction> transactions;
        {
            final List<Transaction> prototypeBlockTransaction = _prototypeBlock.getTransactions();
            final MutableList<Transaction> mutableList = new MutableList<>(prototypeBlockTransaction);
            mutableList.set(0, coinbaseTransaction);
            transactions = mutableList;
        }
        return new ImmutableBlock(blockHeader, transactions);
    }

    public MinerNotifyMessage createRequest(final Boolean abandonOldJobs) {
        return _createRequest(abandonOldJobs);
    }

    public BlockHeader getPrototypeBlock() {
        return _prototypeBlock;
    }

    public Long getBlockHeight() {
        return _blockHeight;
    }

}
