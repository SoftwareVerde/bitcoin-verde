package com.softwareverde.bitcoin.server.message;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeaderInflater;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.bloomfilter.clear.ClearTransactionBloomFilterMessage;
import com.softwareverde.bitcoin.server.message.type.bloomfilter.clear.ClearTransactionBloomFilterMessageInflater;
import com.softwareverde.bitcoin.server.message.type.bloomfilter.set.SetTransactionBloomFilterMessage;
import com.softwareverde.bitcoin.server.message.type.bloomfilter.set.SetTransactionBloomFilterMessageInflater;
import com.softwareverde.bitcoin.server.message.type.bloomfilter.update.UpdateTransactionBloomFilterMessage;
import com.softwareverde.bitcoin.server.message.type.bloomfilter.update.UpdateTransactionBloomFilterMessageInflater;
import com.softwareverde.bitcoin.server.message.type.compact.EnableCompactBlocksMessage;
import com.softwareverde.bitcoin.server.message.type.compact.EnableCompactBlocksMessageInflater;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofMessage;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofMessageInflater;
import com.softwareverde.bitcoin.server.message.type.error.ErrorMessage;
import com.softwareverde.bitcoin.server.message.type.error.ErrorMessageInflater;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddressMessage;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddressMessageInflater;
import com.softwareverde.bitcoin.server.message.type.node.address.request.RequestPeersMessage;
import com.softwareverde.bitcoin.server.message.type.node.address.request.RequestPeersMessageInflater;
import com.softwareverde.bitcoin.server.message.type.node.feature.NewBlocksViaHeadersMessage;
import com.softwareverde.bitcoin.server.message.type.node.feature.NewBlocksViaHeadersMessageInflater;
import com.softwareverde.bitcoin.server.message.type.node.feefilter.FeeFilterMessage;
import com.softwareverde.bitcoin.server.message.type.node.feefilter.FeeFilterMessageInflater;
import com.softwareverde.bitcoin.server.message.type.node.ping.BitcoinPingMessage;
import com.softwareverde.bitcoin.server.message.type.node.ping.BitcoinPingMessageInflater;
import com.softwareverde.bitcoin.server.message.type.node.pong.BitcoinPongMessage;
import com.softwareverde.bitcoin.server.message.type.node.pong.BitcoinPongMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.address.QueryAddressBlocksMessage;
import com.softwareverde.bitcoin.server.message.type.query.address.QueryAddressBlocksMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.block.QueryBlocksMessage;
import com.softwareverde.bitcoin.server.message.type.query.block.QueryBlocksMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.mempool.QueryUnconfirmedTransactionsMessage;
import com.softwareverde.bitcoin.server.message.type.query.mempool.QueryUnconfirmedTransactionsMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.response.InventoryMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.InventoryMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.response.block.BlockMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.block.BlockMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.response.block.header.BlockHeadersMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.block.header.BlockHeadersMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.response.block.merkle.MerkleBlockMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.block.merkle.MerkleBlockMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.response.error.NotFoundResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.error.NotFoundResponseMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.response.transaction.TransactionMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.transaction.TransactionMessageInflater;
import com.softwareverde.bitcoin.server.message.type.query.slp.QuerySlpStatusMessage;
import com.softwareverde.bitcoin.server.message.type.query.slp.QuerySlpStatusMessageInflater;
import com.softwareverde.bitcoin.server.message.type.request.RequestDataMessage;
import com.softwareverde.bitcoin.server.message.type.request.RequestDataMessageInflater;
import com.softwareverde.bitcoin.server.message.type.request.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.server.message.type.request.header.RequestBlockHeadersMessageInflater;
import com.softwareverde.bitcoin.server.message.type.slp.EnableSlpTransactionsMessageInflater;
import com.softwareverde.bitcoin.server.message.type.thin.block.ExtraThinBlockMessage;
import com.softwareverde.bitcoin.server.message.type.thin.block.ExtraThinBlockMessageInflater;
import com.softwareverde.bitcoin.server.message.type.thin.request.block.RequestExtraThinBlockMessage;
import com.softwareverde.bitcoin.server.message.type.thin.request.block.RequestExtraThinBlockMessageInflater;
import com.softwareverde.bitcoin.server.message.type.thin.request.transaction.RequestExtraThinTransactionsMessage;
import com.softwareverde.bitcoin.server.message.type.thin.request.transaction.RequestExtraThinTransactionsMessageInflater;
import com.softwareverde.bitcoin.server.message.type.thin.transaction.ThinTransactionsMessage;
import com.softwareverde.bitcoin.server.message.type.thin.transaction.ThinTransactionsMessageInflater;
import com.softwareverde.bitcoin.server.message.type.version.acknowledge.BitcoinAcknowledgeVersionMessage;
import com.softwareverde.bitcoin.server.message.type.version.acknowledge.BitcoinAcknowledgeVersionMessageInflater;
import com.softwareverde.bitcoin.server.message.type.version.synchronize.BitcoinSynchronizeVersionMessage;
import com.softwareverde.bitcoin.server.message.type.version.synchronize.BitcoinSynchronizeVersionMessageInflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.message.ProtocolMessageFactory;
import com.softwareverde.util.HexUtil;

import java.util.HashMap;
import java.util.Map;

public class BitcoinProtocolMessageFactory implements ProtocolMessageFactory {
    protected final BitcoinProtocolMessageHeaderInflater _protocolMessageHeaderParser;
    protected final Map<MessageType, BitcoinProtocolMessageInflater> _commandInflaterMap = new HashMap<MessageType, BitcoinProtocolMessageInflater>();

    protected final MasterInflater _masterInflater;

    protected void _defineInflaters() {
        _commandInflaterMap.put(MessageType.SYNCHRONIZE_VERSION, new BitcoinSynchronizeVersionMessageInflater(_masterInflater));
        _commandInflaterMap.put(MessageType.ACKNOWLEDGE_VERSION, new BitcoinAcknowledgeVersionMessageInflater());
        _commandInflaterMap.put(MessageType.PING, new BitcoinPingMessageInflater());
        _commandInflaterMap.put(MessageType.PONG, new BitcoinPongMessageInflater());
        _commandInflaterMap.put(MessageType.NODE_ADDRESSES, new NodeIpAddressMessageInflater(_masterInflater));
        _commandInflaterMap.put(MessageType.REQUEST_BLOCK_HEADERS, new RequestBlockHeadersMessageInflater());
        _commandInflaterMap.put(MessageType.BLOCK_HEADERS, new BlockHeadersMessageInflater(_masterInflater));
        _commandInflaterMap.put(MessageType.QUERY_BLOCKS, new QueryBlocksMessageInflater());
        _commandInflaterMap.put(MessageType.QUERY_UNCONFIRMED_TRANSACTIONS, new QueryUnconfirmedTransactionsMessageInflater());
        _commandInflaterMap.put(MessageType.ERROR, new ErrorMessageInflater());
        _commandInflaterMap.put(MessageType.NOT_FOUND, new NotFoundResponseMessageInflater());
        _commandInflaterMap.put(MessageType.INVENTORY, new InventoryMessageInflater());
        _commandInflaterMap.put(MessageType.BLOCK, new BlockMessageInflater(_masterInflater));
        _commandInflaterMap.put(MessageType.TRANSACTION, new TransactionMessageInflater(_masterInflater));
        _commandInflaterMap.put(MessageType.MERKLE_BLOCK, new MerkleBlockMessageInflater(_masterInflater, _masterInflater));
        _commandInflaterMap.put(MessageType.ENABLE_NEW_BLOCKS_VIA_HEADERS, new NewBlocksViaHeadersMessageInflater());
        _commandInflaterMap.put(MessageType.REQUEST_DATA, new RequestDataMessageInflater(_masterInflater));
        _commandInflaterMap.put(MessageType.ENABLE_COMPACT_BLOCKS, new EnableCompactBlocksMessageInflater());
        _commandInflaterMap.put(MessageType.REQUEST_EXTRA_THIN_BLOCK, new RequestExtraThinBlockMessageInflater(_masterInflater, _masterInflater));
        _commandInflaterMap.put(MessageType.EXTRA_THIN_BLOCK, new ExtraThinBlockMessageInflater(_masterInflater, _masterInflater));
        _commandInflaterMap.put(MessageType.REQUEST_EXTRA_THIN_TRANSACTIONS, new RequestExtraThinTransactionsMessageInflater());
        _commandInflaterMap.put(MessageType.THIN_TRANSACTIONS, new ThinTransactionsMessageInflater(_masterInflater));
        _commandInflaterMap.put(MessageType.FEE_FILTER, new FeeFilterMessageInflater());
        _commandInflaterMap.put(MessageType.REQUEST_PEERS, new RequestPeersMessageInflater());
        _commandInflaterMap.put(MessageType.SET_TRANSACTION_BLOOM_FILTER, new SetTransactionBloomFilterMessageInflater(_masterInflater));
        _commandInflaterMap.put(MessageType.UPDATE_TRANSACTION_BLOOM_FILTER, new UpdateTransactionBloomFilterMessageInflater());
        _commandInflaterMap.put(MessageType.CLEAR_TRANSACTION_BLOOM_FILTER, new ClearTransactionBloomFilterMessageInflater());
        _commandInflaterMap.put(MessageType.DOUBLE_SPEND_PROOF, new DoubleSpendProofMessageInflater());
        // Bitcoin Verde Messages
        _commandInflaterMap.put(MessageType.QUERY_ADDRESS_BLOCKS, new QueryAddressBlocksMessageInflater(_masterInflater));
        _commandInflaterMap.put(MessageType.ENABLE_SLP_TRANSACTIONS, new EnableSlpTransactionsMessageInflater());
        _commandInflaterMap.put(MessageType.QUERY_SLP_STATUS, new QuerySlpStatusMessageInflater());
    }

    @Override
    public BitcoinProtocolMessage fromBytes(final byte[] bytes) {
        final BitcoinProtocolMessageHeader protocolMessageHeader = _protocolMessageHeaderParser.fromBytes(bytes);
        if (protocolMessageHeader == null) { return null; }

        final BitcoinProtocolMessageInflater protocolMessageInflater = _commandInflaterMap.get(protocolMessageHeader.command);
        if (protocolMessageInflater == null) {
            Logger.info("Unsupported message command. 0x"+ HexUtil.toHexString(ByteUtil.copyBytes(bytes, 0, BitcoinProtocolMessageHeaderInflater.HEADER_BYTE_COUNT)));
            return null;
        }

        return protocolMessageInflater.fromBytes(bytes);
    }

    public BitcoinProtocolMessageFactory() {
        this(new CoreInflater());
    }

    public BitcoinProtocolMessageFactory(final MasterInflater masterInflater) {
        _masterInflater = masterInflater;
        _protocolMessageHeaderParser = _masterInflater.getBitcoinProtocolMessageHeaderInflater();
        _defineInflaters();
    }

    public BitcoinProtocolMessageHeaderInflater getProtocolMessageHeaderParser() {
        return _protocolMessageHeaderParser;
    }

    public BitcoinSynchronizeVersionMessage newSynchronizeVersionMessage() {
        return new BitcoinSynchronizeVersionMessage();
    }

    public BitcoinAcknowledgeVersionMessage newAcknowledgeVersionMessage() {
        return new BitcoinAcknowledgeVersionMessage();
    }

    public BitcoinPingMessage newPingMessage() {
        return new BitcoinPingMessage();
    }

    public BitcoinPongMessage newPongMessage() {
        return new BitcoinPongMessage();
    }

    public BitcoinNodeIpAddressMessage newNodeIpAddressMessage() {
        return new BitcoinNodeIpAddressMessage();
    }

    public RequestBlockHeadersMessage newRequestBlockHeadersMessage() {
        return new RequestBlockHeadersMessage();
    }

    public BlockHeadersMessage newBlockHeadersMessage() {
        return new BlockHeadersMessage(_masterInflater);
    }

    public QueryBlocksMessage newQueryBlocksMessage() {
        return new QueryBlocksMessage();
    }

    public QueryUnconfirmedTransactionsMessage newQueryUnconfirmedTransactionsMessage() {
        return new QueryUnconfirmedTransactionsMessage();
    }

    public ErrorMessage newErrorMessage() {
        return new ErrorMessage();
    }

    public NotFoundResponseMessage newNotFoundResponseMessage() {
        return new NotFoundResponseMessage();
    }

    public InventoryMessage newInventoryMessage() {
        return new InventoryMessage();
    }

    public BlockMessage newBlockMessage() {
        return new BlockMessage(_masterInflater);
    }

    public TransactionMessage newTransactionMessage() {
        return new TransactionMessage(_masterInflater);
    }

    public DoubleSpendProofMessage newDoubleSpendProofMessage() {
        return new DoubleSpendProofMessage();
    }

    public MerkleBlockMessage newMerkleBlockMessage() {
        return new MerkleBlockMessage(_masterInflater, _masterInflater);
    }

    public NewBlocksViaHeadersMessage newNewBlocksViaHeadersMessage() {
        return new NewBlocksViaHeadersMessage();
    }

    public RequestDataMessage newRequestDataMessage() {
        return new RequestDataMessage();
    }

    public EnableCompactBlocksMessage newEnableCompactBlocksMessage() {
        return new EnableCompactBlocksMessage();
    }

    public RequestExtraThinBlockMessage newRequestExtraThinBlockMessage() {
        return new RequestExtraThinBlockMessage(_masterInflater);
    }

    public ExtraThinBlockMessage newExtraThinBlockMessage() {
        return new ExtraThinBlockMessage(_masterInflater, _masterInflater);
    }

    public RequestExtraThinTransactionsMessage newRequestExtraThinTransactionsMessage() {
        return new RequestExtraThinTransactionsMessage();
    }

    public ThinTransactionsMessage newThinTransactionsMessage() {
        return new ThinTransactionsMessage(_masterInflater);
    }

    public FeeFilterMessage newFeeFilterMessage() {
        return new FeeFilterMessage();
    }

    public RequestPeersMessage newRequestPeersMessage() {
        return new RequestPeersMessage();
    }

    public SetTransactionBloomFilterMessage newSetTransactionBloomFilterMessage() {
        return new SetTransactionBloomFilterMessage(_masterInflater);
    }

    public UpdateTransactionBloomFilterMessage newUpdateTransactionBloomFilterMessage() {
        return new UpdateTransactionBloomFilterMessage();
    }

    public ClearTransactionBloomFilterMessage newClearTransactionBloomFilterMessage() {
        return new ClearTransactionBloomFilterMessage();
    }

    public QueryAddressBlocksMessage newQueryAddressBlocksMessage() {
        return new QueryAddressBlocksMessage();
    }

    public QuerySlpStatusMessage newQuerySlpStatusMessage() {
        return new QuerySlpStatusMessage();
    }
}
