package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.address.CompressedAddress;
import com.softwareverde.bitcoin.secp256k1.key.PublicKey;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.opcode.Opcode;
import com.softwareverde.bitcoin.transaction.script.opcode.Operation;
import com.softwareverde.bitcoin.transaction.script.opcode.PushOperation;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;

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
        final int opcodeCount = pattern.getSize();
        final int operationCount = scriptOperations.getSize();

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

            final Boolean isPublicKeyLength = (pushedByteCount == 65);
            final Boolean isCompressedPublicKeyLength = (pushedByteCount == 33);
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
        if (scriptOperations.getSize() != PAY_TO_PUBLIC_KEY_PATTERN.getSize()) { return null; }

        final Operation operation = scriptOperations.get(0);
        if (! (operation instanceof PushOperation)) {
            return null;
        }

        final PushOperation pushOperation = (PushOperation) operation;
        final ByteArray bytes = MutableByteArray.wrap(pushOperation.getValue().getBytes());
        final PublicKey publicKey = PublicKey.fromBytes(bytes);
        return publicKey;
    }


    protected Address _extractAddressFromPayToPublicKey(final Script lockingScript) {
        final PublicKey publicKey = _extractPublicKeyFromPayToPublicKey(lockingScript);
        if (publicKey == null) { return null; }

        final AddressInflater addressInflater = new AddressInflater();
        if (publicKey.isCompressed()) {
            return addressInflater.compressedFromPublicKey(publicKey);
        }
        else {
            return addressInflater.fromPublicKey(publicKey);
        }
    }

    protected Address _extractDecompressedAddressFromPayToPublicKey(final Script lockingScript) {
        final PublicKey publicKey = _extractPublicKeyFromPayToPublicKey(lockingScript);
        if (publicKey == null) { return null; }

        final AddressInflater addressInflater = new AddressInflater();
        return addressInflater.fromPublicKey(publicKey.decompress());
    }

    protected CompressedAddress _extractCompressedAddressFromPayToPublicKey(final Script lockingScript) {
        final PublicKey publicKey = _extractPublicKeyFromPayToPublicKey(lockingScript);
        if (publicKey == null) { return null; }

        final AddressInflater addressInflater = new AddressInflater();
        return addressInflater.compressedFromPublicKey(publicKey);
    }

    protected Address _extractAddressFromPayToPublicKeyHash(final Script lockingScript) {
        final List<Operation> scriptOperations = lockingScript.getOperations();
        if (scriptOperations == null) { return null; }
        if (scriptOperations.getSize() != PAY_TO_PUBLIC_KEY_HASH_PATTERN.getSize()) { return null; }

        final Operation operation = scriptOperations.get(2);
        if (! (operation instanceof PushOperation)) {
            return null;
        }

        final PushOperation pushOperation = (PushOperation) operation;
        final ByteArray bytes = MutableByteArray.wrap(pushOperation.getValue().getBytes());

        final AddressInflater addressInflater = new AddressInflater();
        return addressInflater.fromBytes(bytes);
    }

    protected Address _extractAddressFromPayToScriptHash(final Script lockingScript) {
        final List<Operation> scriptOperations = lockingScript.getOperations();
        if (scriptOperations == null) { return null; }
        if (scriptOperations.getSize() != PAY_TO_SCRIPT_HASH_PATTERN.getSize()) { return null; }

        final Operation operation = scriptOperations.get(1);
        if (! (operation instanceof PushOperation)) {
            return null;
        }

        final PushOperation pushOperation = (PushOperation) operation;
        final ByteArray bytes = MutableByteArray.wrap(pushOperation.getValue().getBytes());

        final AddressInflater addressInflater = new AddressInflater();
        return addressInflater.fromBytes(bytes);
    }

    /**
     * Returns true if the provided script matches the Pay-To-Public-Key (P2PK) script format.
     *  The P2PK format is: <20-byte public-key-hash> OP_CHECKSIG
     */
    public Boolean matchesPayToPublicKeyFormat(final Script lockingScript) {
        return _matchesPayToPublicKeyFormat(lockingScript);
    }

    /**
     * Returns true if the provided script matches the Pay-To-Public-Key-Hash (P2PKH) script format.
     *  The P2PKH format is: OP_DUP OP_HASH160 <20-byte public-key-hash> OP_EQUALVERIFY OP_CHECKSIG
     */
    public Boolean matchesPayToPublicKeyHashFormat(final Script lockingScript) {
        return _matchesPayToPublicKeyHashFormat(lockingScript);
    }

    public Address extractAddress(final ScriptType scriptType, final Script lockingScript) {
        final Address address;
        {
            switch (scriptType) {
                case PAY_TO_PUBLIC_KEY: {
                    address = _extractAddressFromPayToPublicKey(lockingScript);
                } break;

                case PAY_TO_PUBLIC_KEY_HASH: {
                    address = _extractAddressFromPayToPublicKeyHash(lockingScript);
                } break;

                case PAY_TO_SCRIPT_HASH: {
                    address = _extractAddressFromPayToScriptHash(lockingScript);
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

    public Address extractDecompressedAddressFromPayToPublicKey(final LockingScript lockingScript) {
        return _extractDecompressedAddressFromPayToPublicKey(lockingScript);
    }

    public CompressedAddress extractCompressedAddressFromPayToPublicKey(final LockingScript lockingScript) {
        return _extractCompressedAddressFromPayToPublicKey(lockingScript);
    }

    public Address extractAddressFromPayToPublicKeyHash(final LockingScript lockingScript) {
        return _extractAddressFromPayToPublicKeyHash(lockingScript);
    }


    public Address extractAddressFromPayToScriptHash(final LockingScript lockingScript) {
        return _extractAddressFromPayToScriptHash(lockingScript);
    }

    /**
     * Returns true if the provided script matches the Pay-To-Script-Hash (P2SH) script format.
     *  The P2SH format is: HASH160 <20-byte redeem-script-hash> EQUAL
     */
    public Boolean matchesPayToScriptHashFormat(final LockingScript lockingScript) {
        return _matchesPayToScriptHashFormat(lockingScript);
    }

    public ScriptType getScriptType(final LockingScript script) {
        final Boolean isPayToPublicKeyHash = _matchesPayToPublicKeyHashFormat(script);
        final Boolean isPayToScriptHash = ( isPayToPublicKeyHash ? false : _matchesPayToScriptHashFormat(script) );
        final Boolean isPayToPublicKey = ( (isPayToPublicKeyHash || isPayToScriptHash) ? false : _matchesPayToPublicKeyFormat(script) );

        if (isPayToPublicKeyHash) {
            return ScriptType.PAY_TO_PUBLIC_KEY_HASH;
        }
        else if (isPayToScriptHash) {
            return ScriptType.PAY_TO_SCRIPT_HASH;
        }
        else if (isPayToPublicKey) {
            return ScriptType.PAY_TO_PUBLIC_KEY;
        }

        return ScriptType.CUSTOM_SCRIPT;
    }

    // https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2019-05-15-segwit-recovery.md
    public Boolean matchesSegregatedWitnessProgram(final UnlockingScript unlockingScript) {
        final List<Operation> operations = unlockingScript.getOperations();
        if (operations.getSize() != 1) { return false; }

        final Operation operation = operations.get(0);
        if (operation.getType() != Operation.Type.OP_PUSH) { return false; }

        final PushOperation pushOperation = (PushOperation) operation;
        final Value value = pushOperation.getValue();

        final Integer valueByteCount = value.getByteCount();
        if ( (valueByteCount < 4 || valueByteCount > 42) ) { return false; }

        final byte firstByte = value.getByte(0);
        if ( (firstByte != 0x00) && (! (firstByte >= 0x51 && firstByte <= 0x60)) ) { return false; }

        final byte secondByte = value.getByte(1);
        final int secondByteIntegerValue = ByteUtil.byteToInteger(secondByte);
        return ((value.getByteCount() - 2) == secondByteIntegerValue);
    }
}
