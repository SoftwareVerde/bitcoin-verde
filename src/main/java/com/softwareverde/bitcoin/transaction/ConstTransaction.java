package com.softwareverde.bitcoin.transaction;

import com.softwareverde.constable.Const;

/**
 * Acts as a parent interface for ImmutableTransaction, ensuring that {@link Transaction#asConst()}
 * returns an interface.  This allows classes implementing Transaction to provide their own
 * immutable transaction base class, instead of being forced to use ImmutableTransaction.
 */
public interface ConstTransaction extends Const, Transaction {
}
