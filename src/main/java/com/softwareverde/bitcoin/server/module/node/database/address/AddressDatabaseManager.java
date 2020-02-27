package com.softwareverde.bitcoin.server.module.node.database.address;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

public interface AddressDatabaseManager {
    List<AddressId> getAddressIds(TransactionId transactionId) throws DatabaseException;
    AddressId getAddressId(String addressString) throws DatabaseException;
    AddressId getAddressId(Address address) throws DatabaseException;
    List<TransactionId> getTransactionIds(BlockchainSegmentId blockchainSegmentId, AddressId addressId, Boolean includeUnconfirmedTransactions) throws DatabaseException;
    List<TransactionId> getTransactionIdsSendingTo(BlockchainSegmentId blockchainSegmentId, AddressId addressId, Boolean includeUnconfirmedTransactions) throws DatabaseException;
    List<TransactionId> getTransactionIdsSpendingFrom(BlockchainSegmentId blockchainSegmentId, AddressId addressId, Boolean includeUnconfirmedTransactions) throws DatabaseException;
    Long getAddressBalance(BlockchainSegmentId blockchainSegmentId, AddressId addressId) throws DatabaseException;
    List<TransactionId> getSlpTransactionIds(SlpTokenId slpTokenId) throws DatabaseException;
}
