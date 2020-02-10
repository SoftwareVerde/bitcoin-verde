package com.softwareverde.bitcoin.server.module.node.database.address.fullnode;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.database.address.TransactionOutputId;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

public interface AddressDatabaseManager extends com.softwareverde.bitcoin.server.module.node.database.address.AddressDatabaseManager {

    AddressId storeScriptAddress(LockingScript lockingScript) throws DatabaseException;

    List<AddressId> storeScriptAddresses(List<LockingScript> lockingScripts) throws DatabaseException;

    AddressId getAddressId(TransactionOutputId transactionOutputId) throws DatabaseException;

    AddressId getAddressId(String addressString) throws DatabaseException;

    AddressId getAddressId(Address address) throws DatabaseException;

    List<Object> getSpendableTransactionOutputs(BlockchainSegmentId blockchainSegmentId, AddressId addressId) throws DatabaseException; // TODO

    List<TransactionId> getTransactionIds(BlockchainSegmentId blockchainSegmentId, AddressId addressId, Boolean includeUnconfirmedTransactions) throws DatabaseException;

    List<TransactionId> getTransactionIdsSendingTo(BlockchainSegmentId blockchainSegmentId, AddressId addressId, Boolean includeUnconfirmedTransactions) throws DatabaseException;

    List<TransactionId> getTransactionIdsSpendingFrom(BlockchainSegmentId blockchainSegmentId, AddressId addressId, Boolean includeUnconfirmedTransactions) throws DatabaseException;

    Long getAddressBalance(BlockchainSegmentId blockchainSegmentId, AddressId addressId) throws DatabaseException;

    List<TransactionId> getSlpTransactionIds(SlpTokenId slpTokenId) throws DatabaseException;
}
