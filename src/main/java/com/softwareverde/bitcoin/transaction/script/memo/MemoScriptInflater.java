package com.softwareverde.bitcoin.transaction.script.memo;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoAddPollOptionAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoAddressAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoCreatePollAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoFollowTopicAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoFollowUserAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoLikeMemoAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoMuteUserAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoPollVoteAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoPostMemoAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoPostTopicAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoReplyToMemoAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoSendMoneyAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoSetNameAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoSetProfilePictureAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoSetProfileTextAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoStringAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoTransactionHashAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoUnfollowTopicAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoUnfollowUserAction;
import com.softwareverde.bitcoin.transaction.script.memo.action.MemoUnmuteUserAction;
import com.softwareverde.bitcoin.transaction.script.opcode.Opcode;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

public class MemoScriptInflater {
    public static MemoScriptType getScriptType(final LockingScript lockingScript) {
        if (lockingScript == null) { return null; }

        final List<Operation> operations = lockingScript.getOperations();
        if (operations.getCount() < 2) { return null; }

        { // Ensure the first opcode is OP_RETURN...
            final Operation operation = operations.get(0);
            final boolean firstOperationIsReturn = (operation.getOpcodeByte() == Opcode.RETURN.getValue());
            if (! firstOperationIsReturn) { return null; }
        }

        { // Ensure the first opcode after the OP_RETURN is a push and that its value matches a valid Memo Operation...
            final Operation operation = operations.get(1);
            if (operation.getType() != Operation.Type.OP_PUSH) { return null; }
            final PushOperation pushOperation = (PushOperation) operation;

            final Value value = pushOperation.getValue();
            return MemoScriptType.fromBytes(value);
        }
    }

    protected final AddressInflater _addressInflater = new AddressInflater();

    public MemoScriptInflater() { }

    protected Integer _getInteger(final Integer index, final List<Operation> operations) {
        if (operations.getCount() <= index) { return null; }

        final Operation operation = operations.get(index);
        if (! (operation instanceof PushOperation)) { return null; }

        final PushOperation pushOperation = (PushOperation) operation;

        final Value value = pushOperation.getValue();
        return value.asInteger();
    }

    protected String _getString(final Integer index, final List<Operation> operations) {
        if (operations.getCount() <= index) { return null; }

        final Operation operation = operations.get(index);
        if (! (operation instanceof PushOperation)) { return null; }

        final PushOperation pushOperation = (PushOperation) operation;

        final Value value = pushOperation.getValue();
        return value.asString();
    }

    protected Sha256Hash _getHash(final Integer index, final List<Operation> operations) {
        if (operations.getCount() <= index) { return null; }

        final Operation operation = operations.get(index);
        if (! (operation instanceof PushOperation)) { return null; }

        final PushOperation pushOperation = (PushOperation) operation;

        final Value value = pushOperation.getValue();
        if (! Util.areEqual(Sha256Hash.BYTE_COUNT, value.getByteCount())) { return null; }

        return Sha256Hash.wrap(value.getBytes());
    }

    protected Address _getAddress(final Integer index, final List<Operation> operations) {
        if (operations.getCount() <= index) { return null; }

        final Operation operation = operations.get(index);
        if (! (operation instanceof PushOperation)) { return null; }

        final PushOperation pushOperation = (PushOperation) operation;

        final Value value = pushOperation.getValue();
        if (! Util.areEqual(Address.BYTE_COUNT, value.getByteCount())) { return null; }

        return _addressInflater.fromBytes(value);
    }

    protected MemoAction _fromLockingScript(final LockingScript lockingScript) {
        final MemoScriptType memoScriptType = MemoScriptInflater.getScriptType(lockingScript);
        if (memoScriptType == null) { return null; }

        final List<Operation> operations = lockingScript.getOperations();

        switch (memoScriptType) {
            case SET_NAME: {
                if (operations.getCount() != 3) { return null; }
                final String value = _getString(2, operations);
                if (value == null) { return null; }

                return new MemoSetNameAction(value);
            }

            case POST_MEMO: {
                if (operations.getCount() != 3) { return null; }
                final String value = _getString(2, operations);
                if (value == null) { return null; }

                return new MemoPostMemoAction(value);
            }

            case REPLY_MEMO: {
                if (operations.getCount() != 4) { return null; }

                final Sha256Hash transactionHash = _getHash(2, operations);
                if (transactionHash == null) { return null; }

                final String value = _getString(3, operations);
                if (value == null) { return null; }

                return new MemoReplyToMemoAction(transactionHash, value);
            }

            case LIKE_MEMO: {
                if (operations.getCount() != 3) { return null; }

                final Sha256Hash transactionHash = _getHash(2, operations);
                if (transactionHash == null) { return null; }

                return new MemoLikeMemoAction(transactionHash);
            }

            case SET_PROFILE_TEXT: {
                if (operations.getCount() != 3) { return null; }
                final String value = _getString(2, operations);
                if (value == null) { return null; }

                return new MemoSetProfileTextAction(value);
            }

            case FOLLOW_USER: {
                if (operations.getCount() != 3) { return null; }
                final Address address = _getAddress(2, operations);
                if (address == null) { return null; }

                return new MemoFollowUserAction(address);
            }

            case UNFOLLOW_USER: {
                if (operations.getCount() != 3) { return null; }
                final Address address = _getAddress(2, operations);
                if (address == null) { return null; }

                return new MemoUnfollowUserAction(address);
            }

            case SET_PROFILE_PICTURE: {
                if (operations.getCount() != 3) { return null; }
                final String value = _getString(2, operations);
                if (value == null) { return null; }

                return new MemoSetProfilePictureAction(value);
            }

            case POST_TOPIC: {
                if (operations.getCount() != 4) { return null; }

                final String topic = _getString(2, operations);
                if (topic == null) { return null; }

                final String message = _getString(3, operations);
                if (message == null) { return null; }

                return new MemoPostTopicAction(topic, message);
            }

            case FOLLOW_TOPIC: {
                if (operations.getCount() != 3) { return null; }
                final String value = _getString(2, operations);
                if (value == null) { return null; }

                return new MemoFollowTopicAction(value);
            }

            case UNFOLLOW_TOPIC: {
                if (operations.getCount() != 3) { return null; }
                final String value = _getString(2, operations);
                if (value == null) { return null; }

                return new MemoUnfollowTopicAction(value);
            }

            case CREATE_POLL: {
                if (operations.getCount() != 5) { return null; }

                final Integer pollType = _getInteger(2, operations);
                if (pollType == null) { return null; }

                final Integer optionCount = _getInteger(3, operations);
                if (optionCount == null) { return null; }

                final String question = _getString(4, operations);
                if (question == null) { return null; }

                return new MemoCreatePollAction(pollType, optionCount, question);
            }

            case ADD_POLL_OPTION: {
                if (operations.getCount() != 4) { return null; }

                final Sha256Hash transactionHash = _getHash(2, operations);
                if (transactionHash == null) { return null; }

                final String value = _getString(3, operations);
                if (value == null) { return null; }

                return new MemoAddPollOptionAction(transactionHash, value);
            }

            case VOTE_POLL: {
                if (operations.getCount() != 4) { return null; }

                final Sha256Hash transactionHash = _getHash(2, operations);
                if (transactionHash == null) { return null; }

                final String value = _getString(3, operations);
                if (value == null) { return null; }

                return new MemoPollVoteAction(transactionHash, value);
            }

            case MUTE_USER: {
                if (operations.getCount() != 3) { return null; }
                final Address address = _getAddress(2, operations);
                if (address == null) { return null; }

                return new MemoMuteUserAction(address);
            }

            case UNMUTE_USER: {
                if (operations.getCount() != 3) { return null; }
                final Address address = _getAddress(2, operations);
                if (address == null) { return null; }

                return new MemoUnmuteUserAction(address);
            }

            case SEND_MONEY: {
                if (operations.getCount() != 4) { return null; }

                final Address address = _getAddress(2, operations);
                if (address == null) { return null; }

                final String value = _getString(3, operations);
                if (value == null) { return null; }

                return new MemoSendMoneyAction(address, value);
            }

            default: { return null; }
        }
    }

    public ByteArray getActionIdentifier(final LockingScript lockingScript) {
        final MemoAction memoAction = _fromLockingScript(lockingScript);
        if (memoAction == null) { return null; }

        final MemoScriptType memoScriptType = memoAction.getMemoScriptType();
        switch (memoScriptType) {
            case REPLY_MEMO:
            case LIKE_MEMO:
            case ADD_POLL_OPTION:
            case VOTE_POLL: {
                if (! (memoAction instanceof MemoTransactionHashAction)) {
                    Logger.debug(memoScriptType + " depends on TransactionHash identifier but is not MemoTransactionHashAction type.");
                    return null;
                }

                final MemoTransactionHashAction memoTransactionHashAction = (MemoTransactionHashAction) memoAction;
                return memoTransactionHashAction.getTransactionHash();
            }

            case POST_TOPIC:
            case FOLLOW_TOPIC:
            case UNFOLLOW_TOPIC: {
                if (! (memoAction instanceof MemoStringAction)) {
                    Logger.debug(memoScriptType + " depends on TransactionHash identifier but is not MemoStringAction type.");
                    return null;
                }

                final MemoStringAction memoStringAction = (MemoStringAction) memoAction;
                final String topicName = memoStringAction.getContent();
                return ByteArray.wrap(StringUtil.stringToBytes(topicName));
            }

            case SEND_MONEY:
            case MUTE_USER:
            case UNMUTE_USER:
            case FOLLOW_USER:
            case UNFOLLOW_USER: {
                if (! (memoAction instanceof MemoAddressAction)) {
                    Logger.debug(memoScriptType + " depends on TransactionHash identifier but is not MemoAddressAction type.");
                    return null;
                }

                final MemoAddressAction memoAddressAction = (MemoAddressAction) memoAction;
                return memoAddressAction.getAddress();
            }
        }

        return null;
    }

    public MemoAction fromLockingScript(final LockingScript lockingScript) {
        return _fromLockingScript(lockingScript);
    }
}
