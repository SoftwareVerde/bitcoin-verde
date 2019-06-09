package com.softwareverde.bitcoin.server.module.node.database.address;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

public interface AddressDatabaseManager {
    /**
     * ScriptWrapper is a wrapper around LockingScript so that hashCode and equals uses simple checks instead of
     *  the more complicated Script implementations.
     */
    class ScriptWrapper {
        public final LockingScript lockingScript;

        public ScriptWrapper(final LockingScript lockingScript) {
            this.lockingScript = lockingScript;
        }

        @Override
        public int hashCode() {
            return this.lockingScript.simpleHashCode();
        }

        @Override
        public boolean equals(final Object object) {
            if (object instanceof ScriptWrapper) {
                final ScriptWrapper scriptWrapper = ((ScriptWrapper) object);
                return this.lockingScript.simpleEquals(scriptWrapper.lockingScript);
            }

            return this.lockingScript.simpleEquals(object); }
    }

    AddressId storeScriptAddress(LockingScript lockingScript) throws DatabaseException;
    List<AddressId> storeScriptAddresses(List<LockingScript> lockingScripts) throws DatabaseException;
    AddressId getAddressId(TransactionOutputId transactionOutputId) throws DatabaseException;
    AddressId getAddressId(String addressString) throws DatabaseException;
    AddressId getAddressId(Address address) throws DatabaseException;
    List<SpendableTransactionOutput> getSpendableTransactionOutputs(BlockchainSegmentId blockchainSegmentId, AddressId addressId) throws DatabaseException;
    List<TransactionId> getTransactionIds(BlockchainSegmentId blockchainSegmentId, AddressId addressId, Boolean includeUnconfirmedTransactions) throws DatabaseException;
    List<TransactionId> getTransactionIdsSendingTo(BlockchainSegmentId blockchainSegmentId, AddressId addressId, Boolean includeUnconfirmedTransactions) throws DatabaseException;
    List<TransactionId> getTransactionIdsSpendingFrom(BlockchainSegmentId blockchainSegmentId, AddressId addressId, Boolean includeUnconfirmedTransactions) throws DatabaseException;
    Long getAddressBalance(BlockchainSegmentId blockchainSegmentId, AddressId addressId) throws DatabaseException;
}
