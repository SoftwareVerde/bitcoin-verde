package com.softwareverde.bitcoin.server.stratum;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.ImmutableBlock;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.ImmutableDifficulty;
import com.softwareverde.bitcoin.server.stratum.message.RequestMessage;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.type.bytearray.FragmentedBytes;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.type.merkleroot.MutableMerkleRoot;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.util.HexUtil;

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

    protected final MutableBlock _prototypeBlock = new MutableBlock();
    protected List<String> _merkleTreeBranches; // Little-endian merkle tree (intermediary) branch hashes...
    protected String _extraNonce1;
    protected String _coinbaseTransactionHead;
    protected String _coinbaseTransactionTail;

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

    protected static String _reverseEndian(final String input) {
        if (input.length() % 2 != 0) {
            Logger.log("reverseEndian: Invalid Hex String: "+ input);
            return null;
        }

        final StringBuilder stringBuilder = new StringBuilder();
        for (int i=0; i<input.length()/2; ++i) {
            final int index = (input.length() - (i * 2) - 2);
            final String byteString = _createByteString(input.charAt(index), input.charAt(index + 1));
            stringBuilder.append(byteString);
        }
        return stringBuilder.toString();
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

    protected RequestMessage _createRequest(final Long timestamp) {
        final RequestMessage mineBlockMessage = new RequestMessage(RequestMessage.ServerCommand.NOTIFY.getValue());

        final Json parametersJson = new Json(true);
        parametersJson.add(HexUtil.toHexString(_id.getBytes()));
        parametersJson.add(_swabBytes(_reverseEndian(HexUtil.toHexString(_prototypeBlock.getPreviousBlockHash().getBytes()))));
        parametersJson.add(_coinbaseTransactionHead);
        parametersJson.add(_coinbaseTransactionTail);

        final Json partialMerkleTreeJson = new Json(true);
        { // Create the partialMerkleTree Json as little-endian hashes...
            final ImmutableListBuilder<String> listBuilder = new ImmutableListBuilder<String>();
            final List<Sha256Hash> partialMerkleTree = _prototypeBlock.getPartialMerkleTree(0);
            for (final Sha256Hash hash : partialMerkleTree) {
                final String hashString = hash.toString();
                partialMerkleTreeJson.add(_reverseEndian(hashString));
                listBuilder.add(_reverseEndian(hashString));
            }
            _merkleTreeBranches = listBuilder.build();
        }
        parametersJson.add(partialMerkleTreeJson);

        parametersJson.add(HexUtil.toHexString(ByteUtil.integerToBytes(_prototypeBlock.getVersion())));
        parametersJson.add(HexUtil.toHexString(_prototypeBlock.getDifficulty().encode()));
        parametersJson.add(HexUtil.toHexString(ByteUtil.integerToBytes(timestamp)));
        parametersJson.add(true);

        mineBlockMessage.setParameters(parametersJson);

        return mineBlockMessage;
    }

    public StratumMineBlockTask() {
        _id = MutableByteArray.wrap(ByteUtil.integerToBytes(getNextId()));
        _prototypeBlock.addTransaction(new MutableTransaction());
    }

    public void setBlockVersion(final String stratumBlockVersion) {
        final Integer blockVersion = ByteUtil.bytesToInteger(HexUtil.hexStringToByteArray(stratumBlockVersion));
        _prototypeBlock.setVersion(blockVersion);
    }

    public void setBlockVersion(final Integer blockVersion) {
        _prototypeBlock.setVersion(blockVersion);
    }

    public void setPreviousBlockHash(final String stratumPreviousBlockHash) {
        final Sha256Hash previousBlockHash = MutableSha256Hash.fromHexString(_reverseEndian(_swabBytes(stratumPreviousBlockHash)));
        _prototypeBlock.setPreviousBlockHash(previousBlockHash);
    }

    public void setPreviousBlockHash(final Sha256Hash previousBlockHash) {
        _prototypeBlock.setPreviousBlockHash(previousBlockHash);
    }

    public void setExtraNonce(final String stratumExtraNonce) {
        _extraNonce1 = stratumExtraNonce;
    }

    public void setExtraNonce(final ByteArray extraNonce) {
        _extraNonce1 = HexUtil.toHexString(extraNonce.getBytes());
    }

    public void setDifficulty(final String stratumDifficulty) {
        final Difficulty difficulty = ImmutableDifficulty.decode(HexUtil.hexStringToByteArray(stratumDifficulty));
        _prototypeBlock.setDifficulty(difficulty);
    }

    public void setDifficulty(final Difficulty difficulty) {
        _prototypeBlock.setDifficulty(difficulty);
    }

    // ViaBTC provides the merkleTreeBranches as little-endian byte strings.
    public void setMerkleTreeBranches(final List<String> merkleTreeBranches) {
        _merkleTreeBranches = merkleTreeBranches.asConst();
    }

    public void addTransaction(final Transaction transaction) {
        _prototypeBlock.addTransaction(transaction.asConst());
    }

    public void clearTransactions() {
        final Transaction coinbaseTransaction = _prototypeBlock.getCoinbaseTransaction();
        _prototypeBlock.clearTransactions();
        _prototypeBlock.addTransaction(coinbaseTransaction);
    }

    public void setCoinbaseTransaction(final String stratumCoinbaseTransactionHead, final String stratumCoinbaseTransactionTail) {
        _coinbaseTransactionHead = stratumCoinbaseTransactionHead;
        _coinbaseTransactionTail = stratumCoinbaseTransactionTail;
    }

    public void setCoinbaseTransaction(final Transaction coinbaseTransaction, final Integer totalExtraNonceByteCount) {
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

    public BlockHeader assembleBlockHeader(final String stratumNonce, final String stratumExtraNonce2, final String stratumTimestamp) {
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

            merkleRoot = _calculateMerkleRoot(coinbaseTransaction, _merkleTreeBranches);
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

    public RequestMessage createRequest() {
        final Long timestamp = (System.currentTimeMillis() / 1000L);
        return _createRequest(timestamp);
    }

    public RequestMessage createRequest(final Long timestamp) {
        return _createRequest(timestamp);
    }
}
