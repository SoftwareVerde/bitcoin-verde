package com.softwareverde.bitcoin.server.stratum;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.CanonicalMutableBlock;
import com.softwareverde.bitcoin.block.ImmutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.bytearray.FragmentedBytes;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.merkleroot.MutableMerkleRoot;
import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class StratumMineBlockTask {
    final static Object _mutex = new Object();
    private static Long _nextId = 1L;
    protected static Long getNextId() {
        synchronized (_mutex) {
            final Long id = _nextId;
            _nextId += 1;
            return id;
        }
    }

    final ByteArray _id;

    protected final CanonicalMutableBlock _prototypeBlock = new CanonicalMutableBlock();
    protected List<String> _merkleTreeBranches; // Little-endian merkle tree (intermediary) branch hashes...
    protected String _extraNonce1;
    protected String _coinbaseTransactionHead;
    protected String _coinbaseTransactionTail;

    protected final ReentrantReadWriteLock.ReadLock _prototypeBlockReadLock;
    protected final ReentrantReadWriteLock.WriteLock _prototypeBlockWriteLock;

    protected static MerkleRoot _calculateMerkleRoot(final Transaction coinbaseTransaction, final List<String> merkleTreeBranches) {
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

            merkleRoot = BitcoinUtil.sha256(BitcoinUtil.sha256(concatenatedHashes));
        }

        return MutableMerkleRoot.wrap(ByteUtil.reverseEndian(merkleRoot));
    }

    protected static String _createByteString(final char a, final char b) {
        return String.valueOf(a) + b;
    }

    protected static String _swabBytes(final String input) {
        // 00 01 02 03 | 04 05 06 07 -> 03 02 01 00 | 07 06 05 04
        final StringBuilder stringBuilder = new StringBuilder();
        for (int i=0; i<input.length()/8; ++i) {
            final int index = (i*8);
            final String byteString0 = _createByteString(input.charAt(index + 0), input.charAt(index + 1));
            final String byteString1 = _createByteString(input.charAt(index + 2), input.charAt(index + 3));
            final String byteString2 = _createByteString(input.charAt(index + 4), input.charAt(index + 5));
            final String byteString3 = _createByteString(input.charAt(index + 6), input.charAt(index + 7));

            stringBuilder.append(byteString3);
            stringBuilder.append(byteString2);
            stringBuilder.append(byteString1);
            stringBuilder.append(byteString0);
        }
        return stringBuilder.toString();
    }

    protected Transaction _assembleCoinbaseTransaction(final String stratumExtraNonce2) {
        final TransactionInflater transactionInflater = new TransactionInflater();
        return transactionInflater.fromBytes(HexUtil.hexStringToByteArray(
            _coinbaseTransactionHead +
            _extraNonce1 +
            stratumExtraNonce2 +
            _coinbaseTransactionTail
        ));
    }

    protected BlockHeader _assembleBlockHeader(final String stratumNonce, final String stratumExtraNonce2, final String stratumTimestamp) {
        final Transaction coinbaseTransaction = _assembleCoinbaseTransaction(stratumExtraNonce2);
        return _assembleBlockHeader(stratumNonce, coinbaseTransaction, stratumTimestamp);
    }

    protected BlockHeader _assembleBlockHeader(final String stratumNonce, final Transaction coinbaseTransaction, final String stratumTimestamp) {
        final MutableBlockHeader blockHeader = new MutableBlockHeader(_prototypeBlock);

        final MerkleRoot merkleRoot = _calculateMerkleRoot(coinbaseTransaction, _merkleTreeBranches);
        blockHeader.setMerkleRoot(merkleRoot);

        blockHeader.setNonce(ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(stratumNonce)));

        final Long timestamp = ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(stratumTimestamp));
        blockHeader.setTimestamp(timestamp);

        return blockHeader;
    }

    // Creates the partialMerkleTree Json as little-endian hashes...
    protected void _rebuildMerkleTreeBranches() {
        final ImmutableListBuilder<String> listBuilder = new ImmutableListBuilder<String>();
        final List<Sha256Hash> partialMerkleTree = _prototypeBlock.getPartialMerkleTree(0);
        for (final Sha256Hash hash : partialMerkleTree) {
            final String hashString = hash.toString();
            listBuilder.add(BitcoinUtil.reverseEndianString(hashString));
        }
        _merkleTreeBranches = listBuilder.build();
    }

    protected RequestMessage _createRequest(final Long timestamp) {
        final RequestMessage mineBlockMessage = new RequestMessage(RequestMessage.ServerCommand.NOTIFY.getValue());

        final Json parametersJson = new Json(true);
        parametersJson.add(HexUtil.toHexString(_id.getBytes()));
        parametersJson.add(_swabBytes(BitcoinUtil.reverseEndianString(HexUtil.toHexString(_prototypeBlock.getPreviousBlockHash().getBytes()))));
        parametersJson.add(_coinbaseTransactionHead);
        parametersJson.add(_coinbaseTransactionTail);

        final Json partialMerkleTreeJson = new Json(true);

        _rebuildMerkleTreeBranches();
        for (final String hashString : _merkleTreeBranches) {
            partialMerkleTreeJson.add(hashString);
        }

        parametersJson.add(partialMerkleTreeJson);

        parametersJson.add(HexUtil.toHexString(ByteUtil.integerToBytes(_prototypeBlock.getVersion())));
        parametersJson.add(_prototypeBlock.getDifficulty().encode());
        parametersJson.add(HexUtil.toHexString(ByteUtil.integerToBytes(timestamp)));
        parametersJson.add(true);

        mineBlockMessage.setParameters(parametersJson);

        return mineBlockMessage;
    }

    public StratumMineBlockTask() {
        _id = MutableByteArray.wrap(ByteUtil.integerToBytes(getNextId()));
        _prototypeBlock.addTransaction(new MutableTransaction());

        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _prototypeBlockReadLock = readWriteLock.readLock();
        _prototypeBlockWriteLock = readWriteLock.writeLock();
    }

    public void setBlockVersion(final String stratumBlockVersion) {
        try {
            _prototypeBlockWriteLock.lock();

            final Long blockVersion = ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(stratumBlockVersion));
            _prototypeBlock.setVersion(blockVersion);
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    public void setBlockVersion(final Long blockVersion) {
        try {
            _prototypeBlockWriteLock.lock();

            _prototypeBlock.setVersion(blockVersion);
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    public void setPreviousBlockHash(final String stratumPreviousBlockHash) {
        try {
            _prototypeBlockWriteLock.lock();

            final Sha256Hash previousBlockHash = Sha256Hash.fromHexString(BitcoinUtil.reverseEndianString(_swabBytes(stratumPreviousBlockHash)));
            _prototypeBlock.setPreviousBlockHash(previousBlockHash);

        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    public void setPreviousBlockHash(final Sha256Hash previousBlockHash) {
        try {
            _prototypeBlockWriteLock.lock();

            _prototypeBlock.setPreviousBlockHash(previousBlockHash);
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    public void setExtraNonce(final String stratumExtraNonce) {
        _extraNonce1 = stratumExtraNonce;
    }

    public void setExtraNonce(final ByteArray extraNonce) {
        _extraNonce1 = HexUtil.toHexString(extraNonce.getBytes());
    }

    public void setDifficulty(final String stratumDifficulty) {
        try {
            _prototypeBlockWriteLock.lock();

            final Difficulty difficulty = Difficulty.decode(HexUtil.hexStringToByteArray(stratumDifficulty));
            _prototypeBlock.setDifficulty(difficulty);
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    public void setDifficulty(final Difficulty difficulty) {
        try {
            _prototypeBlockWriteLock.lock();

            _prototypeBlock.setDifficulty(difficulty);
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    // ViaBTC provides the merkleTreeBranches as little-endian byte strings.
    public void setMerkleTreeBranches(final List<String> merkleTreeBranches) {
        try {
            _prototypeBlockWriteLock.lock();

            _merkleTreeBranches = merkleTreeBranches.asConst();
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    public void addTransaction(final Transaction transaction) {
        try {
            _prototypeBlockWriteLock.lock();

            final Transaction constTransaction = transaction.asConst();
            _prototypeBlock.addTransaction(constTransaction);
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    public void clearTransactions() {
        try {
            _prototypeBlockWriteLock.lock();

            final Transaction coinbaseTransaction = _prototypeBlock.getCoinbaseTransaction();
            _prototypeBlock.clearTransactions();
            _prototypeBlock.addTransaction(coinbaseTransaction);
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    public void setCoinbaseTransaction(final String stratumCoinbaseTransactionHead, final String stratumCoinbaseTransactionTail) {
        try {
            _prototypeBlockWriteLock.lock();

            _coinbaseTransactionHead = stratumCoinbaseTransactionHead;
            _coinbaseTransactionTail = stratumCoinbaseTransactionTail;
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    public void setCoinbaseTransaction(final Transaction coinbaseTransaction, final Integer totalExtraNonceByteCount) {
        try {
            _prototypeBlockWriteLock.lock();

            final TransactionDeflater transactionDeflater = new TransactionDeflater();
            final FragmentedBytes coinbaseTransactionParts;
            coinbaseTransactionParts = transactionDeflater.fragmentTransaction(coinbaseTransaction);

            // NOTE: _coinbaseTransactionHead contains the unlocking script. This script contains two items:
            //  1. The Coinbase Message (ex: "/Mined via Bitcoin-Verde v0.0.1/")
            //  2. The extraNonce (which itself is composed of two components: extraNonce1 and extraNonce2...)
            // extraNonce1 is usually defined by the Mining Pool, not the Miner. The Miner is sent (by the Pool) the number
            // of bytes it should use when generating the extraNonce2 during the Pool's response to the Miner's SUBSCRIBE message.
            // Despite extraNonce just being random data, it still needs to be pushed like regular data within the unlocking script.
            //  Thus, the unlocking script is generated by pushing N bytes (0x00), where N is the byteCount of the extraNonce
            //  (extraNonceByteCount = extraNonce1ByteCount + extraNonce2ByteCount). This results in appropriate operation code
            //  being prepended to the script.  These 0x00 bytes are omitted when stored within _coinbaseTransactionHead,
            //  otherwise, the Miner would appending the extraNonce after the 0x00 bytes instead of replacing them...
            //
            //  Therefore, assuming N is 8, the 2nd part of the unlocking script would originally look something like:
            //
            //      OPCODE  | EXTRA NONCE 1         | EXTRA NONCE 2
            //      -----------------------------------------------------
            //      0x08    | 0x00 0x00 0x00 0x00   | 0x00 0x00 0x00 0x00
            //
            //  Then, stored within _coinbaseTransactionHead (to be sent to the Miner) simply as:
            //      0x08    |                       |
            //

            final Integer headByteCountExcludingExtraNonces = (coinbaseTransactionParts.headBytes.length - totalExtraNonceByteCount);
            _coinbaseTransactionHead = HexUtil.toHexString(ByteUtil.copyBytes(coinbaseTransactionParts.headBytes, 0, headByteCountExcludingExtraNonces));
            _coinbaseTransactionTail = HexUtil.toHexString(coinbaseTransactionParts.tailBytes);

            _prototypeBlock.replaceTransaction(0, coinbaseTransaction);
        }
        finally {
            _prototypeBlockWriteLock.unlock();
        }
    }

    public BlockHeader assembleBlockHeader(final String stratumNonce, final String stratumExtraNonce2, final String stratumTimestamp) {
        try {
            _prototypeBlockReadLock.lock();

            final MutableBlockHeader blockHeader = new MutableBlockHeader(_prototypeBlock);

            final TransactionInflater transactionInflater = new TransactionInflater();

            final MerkleRoot merkleRoot;
            {
                final Transaction coinbaseTransaction = transactionInflater.fromBytes(HexUtil.hexStringToByteArray(
                    _coinbaseTransactionHead +
                    _extraNonce1 +
                    stratumExtraNonce2 +
                    _coinbaseTransactionTail
                ));

                _rebuildMerkleTreeBranches();
                merkleRoot = _calculateMerkleRoot(coinbaseTransaction, _merkleTreeBranches);
            }
            blockHeader.setMerkleRoot(merkleRoot);

            blockHeader.setNonce(ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(stratumNonce)));

            final Long timestamp = ByteUtil.bytesToLong(HexUtil.hexStringToByteArray(stratumTimestamp));
            blockHeader.setTimestamp(timestamp);

//            int i = 0;
//            for (final Transaction transaction : _prototypeBlock.getTransactions()) {
//                Logger.log(i + ": " + transaction.getHash());
//                i += 1;
//            }

            return blockHeader;
        }
        finally {
            _prototypeBlockReadLock.unlock();
        }
    }

    public Block assembleBlock(final String stratumNonce, final String stratumExtraNonce2, final String stratumTimestamp) {
        try {
            _prototypeBlockReadLock.lock();

            final Transaction coinbaseTransaction = _assembleCoinbaseTransaction(stratumExtraNonce2);

            _rebuildMerkleTreeBranches();
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
        finally {
            _prototypeBlockReadLock.unlock();
        }
    }

    public RequestMessage createRequest() {
        try {
            _prototypeBlockReadLock.lock();

            final Long timestamp = (System.currentTimeMillis() / 1000L);
            return _createRequest(timestamp);
        }
        finally {
            _prototypeBlockReadLock.unlock();
        }
    }

    public RequestMessage createRequest(final Long timestamp) {
        try {
            _prototypeBlockReadLock.lock();

            return _createRequest(timestamp);
        }
        finally {
            _prototypeBlockReadLock.unlock();
        }
    }

    public String getExtraNonce() {
        return _extraNonce1;
    }
}
