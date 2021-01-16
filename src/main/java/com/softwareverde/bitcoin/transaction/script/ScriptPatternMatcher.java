package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptInflater;
import com.softwareverde.bitcoin.transaction.script.memo.MemoScriptType;
import com.softwareverde.bitcoin.transaction.script.opcode.ControlOperation;
import com.softwareverde.bitcoin.transaction.script.opcode.Opcode;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScript;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptInflater;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptType;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;

public class ScriptPatternMatcher {
    protected static final List<Opcode> PAY_TO_PUBLIC_KEY_PATTERN;
    static {
        final ImmutableListBuilder<Opcode> listBuilder = new ImmutableListBuilder<Opcode>(3);

        listBuilder.add(Opcode.PUSH_DATA);
        listBuilder.add(Opcode.CHECK_SIGNATURE);

        PAY_TO_PUBLIC_KEY_PATTERN = listBuilder.build();
    }

    protected static final List<Opcode> PAY_TO_PUBLIC_KEY_HASH_PATTERN;
    static {
        final ImmutableListBuilder<Opcode> listBuilder = new ImmutableListBuilder<Opcode>(3);

        listBuilder.add(Opcode.COPY_1ST);
        listBuilder.add(Opcode.SHA_256_THEN_RIPEMD_160);
        listBuilder.add(Opcode.PUSH_DATA);
        listBuilder.add(Opcode.IS_EQUAL_THEN_VERIFY);
        listBuilder.add(Opcode.CHECK_SIGNATURE);

        PAY_TO_PUBLIC_KEY_HASH_PATTERN = listBuilder.build();
    }

    protected static final List<Opcode> PAY_TO_SCRIPT_HASH_PATTERN;
    static {
        final ImmutableListBuilder<Opcode> listBuilder = new ImmutableListBuilder<Opcode>(3);

        listBuilder.add(Opcode.SHA_256_THEN_RIPEMD_160);
        listBuilder.add(Opcode.PUSH_DATA);
        listBuilder.add(Opcode.IS_EQUAL);

        PAY_TO_SCRIPT_HASH_PATTERN = listBuilder.build();
    }

    protected Boolean _matchesPattern(final List<Opcode> pattern, final List<Operation> scriptOperations) {
        final int opcodeCount = pattern.getCount();
        final int operationCount = scriptOperations.getCount();

        if (opcodeCount != operationCount) { return false; }

        for (int i = 0; i < opcodeCount; ++i) {
            final Opcode opcode = pattern.get(i);
            final Operation operation = scriptOperations.get(i);

            final boolean isMatch = (opcode.matchesByte(operation.getOpcodeByte()));
            if (! isMatch) { return false; }
        }

        return true;
    }

    protected Boolean _matchesPayToPublicKeyFormat(final Script lockingScript) {
        final List<Operation> scriptOperations = lockingScript.getOperations();
        final boolean matchesPattern = _matchesPattern(PAY_TO_PUBLIC_KEY_PATTERN, scriptOperations);
        if (! matchesPattern) { return false; }

        final Operation pushOperation = scriptOperations.get(0);
        if (pushOperation instanceof PushOperation) {
            final int pushedByteCount = ((PushOperation) pushOperation).getValue().getByteCount();

            final boolean isPublicKeyLength = (pushedByteCount == 65);
            final boolean isCompressedPublicKeyLength = (pushedByteCount == 33);
            if ( (! isPublicKeyLength) && (! isCompressedPublicKeyLength) ) { return false; }
        }
        else { return false; }

        return true;
    }

    protected Boolean _matchesPayToPublicKeyHashFormat(final Script lockingScript) {
        final List<Operation> scriptOperations = lockingScript.getOperations();
        final boolean matchesPattern = _matchesPattern(PAY_TO_PUBLIC_KEY_HASH_PATTERN, scriptOperations);
        if (! matchesPattern) { return false; }

        final Operation pushOperation = scriptOperations.get(2);
        if (pushOperation instanceof PushOperation) {
            final int pushedByteCount = ((PushOperation) pushOperation).getValue().getByteCount();
            if (pushedByteCount != 20) { return false; }
        }
        else { return false; }

        return true;
    }

    protected Boolean _matchesPayToScriptHashFormat(final Script lockingScript) {
        final List<Operation> scriptOperations = lockingScript.getOperations();
        final boolean matchesPattern = _matchesPattern(PAY_TO_SCRIPT_HASH_PATTERN, scriptOperations);
        if (! matchesPattern) { return false; }

        final Operation pushOperation = scriptOperations.get(1);
        if (pushOperation instanceof PushOperation) {
            final int pushedByteCount = ((PushOperation) pushOperation).getValue().getByteCount();
            if (pushedByteCount != 20) { return false; }
        }
        else { return false; }

        return true;
    }

    protected PublicKey _extractPublicKeyFromPayToPublicKey(final Script lockingScript) {
        final List<Operation> scriptOperations = lockingScript.getOperations();
        if (scriptOperations == null) { return null; }
        if (scriptOperations.getCount() != PAY_TO_PUBLIC_KEY_PATTERN.getCount()) { return null; }

        final Operation operation = scriptOperations.get(0);
        if (! (operation instanceof PushOperation)) {
            return null;
        }

        final PushOperation pushOperation = (PushOperation) operation;
        final ByteArray bytes = MutableByteArray.wrap(pushOperation.getValue().getBytes());
        return PublicKey.fromBytes(bytes);
    }


    protected Address _extractAddressFromPayToPublicKey(final Script lockingScript) {
        final PublicKey publicKey = _extractPublicKeyFromPayToPublicKey(lockingScript);
        if (publicKey == null) { return null; }

        final AddressInflater addressInflater = new AddressInflater();
        return addressInflater.fromPublicKey(publicKey);
    }

    protected Address _extractAddressFromPayToPublicKeyHash(final Script lockingScript, final Boolean isCompressed) {
        final List<Operation> scriptOperations = lockingScript.getOperations();
        if (scriptOperations == null) { return null; }
        if (scriptOperations.getCount() != PAY_TO_PUBLIC_KEY_HASH_PATTERN.getCount()) { return null; }

        final Operation operation = scriptOperations.get(2);
        if (! (operation instanceof PushOperation)) {
            return null;
        }

        final PushOperation pushOperation = (PushOperation) operation;
        final ByteArray bytes = MutableByteArray.wrap(pushOperation.getValue().getBytes());

        final AddressInflater addressInflater = new AddressInflater();
        return addressInflater.fromBytes(bytes, isCompressed);
    }

    protected Address _extractAddressFromPayToScriptHash(final Script lockingScript, final Boolean isCompressed) {
        final List<Operation> scriptOperations = lockingScript.getOperations();
        if (scriptOperations == null) { return null; }
        if (scriptOperations.getCount() != PAY_TO_SCRIPT_HASH_PATTERN.getCount()) { return null; }

        final Operation operation = scriptOperations.get(1);
        if (! (operation instanceof PushOperation)) {
            return null;
        }

        final PushOperation pushOperation = (PushOperation) operation;
        final ByteArray bytes = MutableByteArray.wrap(pushOperation.getValue().getBytes());

        final AddressInflater addressInflater = new AddressInflater();
        return addressInflater.fromBytes(bytes, isCompressed);
    }

    /**
     * Returns true if the provided script matches the Pay-To-Public-Key (P2PK) script format.
     *  The P2PK format is: <20-byte public-key-hash> OP_CHECKSIG
     */
    public Boolean matchesPayToPublicKeyFormat(final LockingScript lockingScript) {
        return _matchesPayToPublicKeyFormat(lockingScript);
    }

    /**
     * Returns true if the provided script matches the Pay-To-Public-Key-Hash (P2PKH) script format.
     *  The P2PKH format is: OP_DUP OP_HASH160 <20-byte public-key-hash> OP_EQUALVERIFY OP_CHECKSIG
     */
    public Boolean matchesPayToPublicKeyHashFormat(final LockingScript lockingScript) {
        return _matchesPayToPublicKeyHashFormat(lockingScript);
    }

    public Address extractAddress(final ScriptType scriptType, final LockingScript lockingScript) {
        final Address address;
        {
            switch (scriptType) {
                case PAY_TO_PUBLIC_KEY: {
                    address = _extractAddressFromPayToPublicKey(lockingScript);
                } break;

                case PAY_TO_PUBLIC_KEY_HASH: {
                    address = _extractAddressFromPayToPublicKeyHash(lockingScript, false);
                } break;

                case PAY_TO_SCRIPT_HASH: {
                    address = _extractAddressFromPayToScriptHash(lockingScript, false);
                } break;

                default: {
                    address = null;
                } break;
            }
        }
        return address;
    }

    public Address extractAddressFromPayToPublicKey(final LockingScript lockingScript) {
        return _extractAddressFromPayToPublicKey(lockingScript);
    }

    public Address extractAddressFromPayToPublicKeyHash(final LockingScript lockingScript) {
        return _extractAddressFromPayToPublicKeyHash(lockingScript, false);
    }

    public Address extractAddressFromPayToPublicKeyHash(final LockingScript lockingScript, final Boolean isCompressed) {
        return _extractAddressFromPayToPublicKeyHash(lockingScript, isCompressed);
    }

    public Address extractAddressFromPayToScriptHash(final LockingScript lockingScript) {
        return _extractAddressFromPayToScriptHash(lockingScript, false);
    }

    /**
     * Returns true if the provided script matches the Pay-To-Script-Hash (P2SH) script format.
     *  The P2SH format is: HASH160 <20-byte redeem-script-hash> EQUAL
     */
    public Boolean matchesPayToScriptHashFormat(final LockingScript lockingScript) {
        return _matchesPayToScriptHashFormat(lockingScript);
    }

    public ScriptType getScriptType(final LockingScript lockingScript) {
        final Boolean isPayToPublicKeyHash = _matchesPayToPublicKeyHashFormat(lockingScript);
        if (isPayToPublicKeyHash) {
            return ScriptType.PAY_TO_PUBLIC_KEY_HASH;
        }

        final Boolean isPayToScriptHash = _matchesPayToScriptHashFormat(lockingScript);
        if (isPayToScriptHash) {
            return ScriptType.PAY_TO_SCRIPT_HASH;
        }

        final Boolean isPayToPublicKey = _matchesPayToPublicKeyFormat(lockingScript);
        if (isPayToPublicKey) {
            return ScriptType.PAY_TO_PUBLIC_KEY;
        }

        final SlpScriptType slpScriptType = SlpScriptInflater.getScriptType(lockingScript);
        if (slpScriptType != null) {
            final SlpScriptInflater slpScriptInflater = new SlpScriptInflater();

            switch (slpScriptType) {
                case GENESIS: {
                    final SlpScript slpScript = slpScriptInflater.genesisScriptFromScript(lockingScript);
                    if (slpScript != null) {
                        return ScriptType.SLP_GENESIS_SCRIPT;
                    }
                }
                case SEND: {
                    final SlpScript slpScript = slpScriptInflater.sendScriptFromScript(lockingScript);
                    if (slpScript != null) {
                        return ScriptType.SLP_SEND_SCRIPT;
                    }
                }
                case MINT: {
                    final SlpScript slpScript = slpScriptInflater.mintScriptFromScript(lockingScript);
                    if (slpScript != null) {
                        return ScriptType.SLP_MINT_SCRIPT;
                    }
                }
                case COMMIT: {
                    final SlpScript slpScript = slpScriptInflater.commitScriptFromScript(lockingScript);
                    if (slpScript != null) {
                        return ScriptType.SLP_COMMIT_SCRIPT;
                    }
                }
            }
        }

        final MemoScriptType memoScriptType = MemoScriptInflater.getScriptType(lockingScript);
        if (memoScriptType != null) {
            return ScriptType.MEMO_SCRIPT;
        }

        return ScriptType.CUSTOM_SCRIPT;
    }

    // https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2019-05-15-segwit-recovery.md
    public Boolean matchesSegregatedWitnessProgram(final UnlockingScript unlockingScript) {
        final List<Operation> operations = unlockingScript.getOperations();
        if (operations.getCount() != 1) { return false; }

        final Operation operation = operations.get(0);
        if (operation.getType() != Operation.Type.OP_PUSH) { return false; }

        final PushOperation pushOperation = (PushOperation) operation;
        final Value value = pushOperation.getValue();

        // Total script length must be between 4 and 42, inclusive, in order to be a segwit program...
        final int valueByteCount = value.getByteCount();
        if ( (valueByteCount < 4 || valueByteCount > 42) ) { return false; }

        // First byte must push a static value to the stack in order to be a segwit program...
        final byte firstByte = value.getByte(0);
        if ( (firstByte != 0x00) && (! (firstByte >= 0x51 && firstByte <= 0x60)) ) { return false; }

        // The second byte must be a integer value, that is equal to the length of the remaining p2sh script...
        // Considering the total length of the script must be between 4 and 42 bytes, the only possible opcodes that
        // that could be provided are push-data operations; these push operations also must consume the rest of the
        // script, leaving no room for other opcode types. (2 <= secondByteIntegerValue <= 40)
        final byte secondByte = value.getByte(1);
        final int secondByteIntegerValue = ByteUtil.byteToInteger(secondByte);
        return ((value.getByteCount() - 2) == secondByteIntegerValue);
    }

    public Boolean isProvablyUnspendable(final LockingScript lockingScript) {
        // Using reflection on a Constable class can invalidate its immutability guarantees,
        //  however, both branches use only immutable operations and yield significant performance
        //  benefits dependant upon the implementation (~50x more performant).
        // Rudimentary tests yielded:
        //  With reflection, a 50/50 split of an uncached ImmutableLockingScript and MutableLockingScript rendered 56,452 operations per ms.
        //  Without this optimization, an uncached ImmutableLockingScript via ::getOperations rendered 1,071 per ms.
        //  Without this optimization, a MutableLockingScript via ::getBytes rendered 1,033 per ms.
        // Since this function is used repeatedly during UTXO caching to determine eligibility, this optimization
        //  warrants the code complexity.
        if (lockingScript instanceof ImmutableScript) {
            final ByteArray byteArray = lockingScript.getBytes();
            if (byteArray.isEmpty()) { return false; }

            final byte firstByte = byteArray.getByte(0);
            return Opcode.RETURN.matchesByte(firstByte);
        }
        else {
            final List<Operation> operations = lockingScript.getOperations();
            if (operations.isEmpty()) { return false; }

            final Operation operation = operations.get(0);
            if (operation.getType() != Operation.Type.OP_CONTROL) { return false; }

            final ControlOperation controlOperation = (ControlOperation) operation;
            return Opcode.RETURN.matchesByte(controlOperation.getOpcodeByte());
        }
    }
}
