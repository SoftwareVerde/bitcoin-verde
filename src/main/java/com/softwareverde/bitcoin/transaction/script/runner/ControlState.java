package com.softwareverde.bitcoin.transaction.script.runner;

import com.softwareverde.bitcoin.transaction.script.opcode.controlstate.CodeBlock;
import com.softwareverde.bitcoin.transaction.script.opcode.controlstate.CodeBlockType;
import com.softwareverde.util.Util;

import java.util.LinkedList;

public class ControlState {
    // protected final LinkedList<CodeBlock> _codeBlocks = new LinkedList<CodeBlock>();
    protected CodeBlock _codeBlock;

    public Boolean isInCodeBlock() {
        return (_codeBlock != null);
    }

    public CodeBlock getCodeBlock() {
        return _codeBlock;
    }

    public Boolean shouldExecute() {
        if (_codeBlock == null) { return true; }
        return (Util.coalesce(_codeBlock.condition, false));
    }

    public void enteredIfBlock(final Boolean conditionValue) {
        if (_codeBlock == null) {
            if (conditionValue == null) {
                throw new NullPointerException(); // conditionValue may only be null if it is within a un-executed nested block...
            }
            _codeBlock = new CodeBlock(CodeBlockType.IF, conditionValue);
            return;
        }

        final Boolean newConditionValue = (Util.coalesce(_codeBlock.condition, false) ? conditionValue : null);
        final CodeBlock newCodeBlock = new CodeBlock(CodeBlockType.IF, newConditionValue);
        newCodeBlock.parent = _codeBlock;
        _codeBlock = newCodeBlock;

    }

    public void enteredElseBlock() {
        if (_codeBlock == null) { throw new IllegalStateException("Entered Else-Block when not currently in a CodeBlock..."); }

        final Boolean newConditionValue = (_codeBlock.condition != null ? (! _codeBlock.condition) : null);
        final CodeBlock newCodeBlock = new CodeBlock(CodeBlockType.ELSE, newConditionValue);
        newCodeBlock.parent = _codeBlock.parent;
        _codeBlock = newCodeBlock;
    }

    public void exitedCodeBlock() {
        if (_codeBlock == null) { throw new IllegalStateException("Exited Block when not currently in a CodeBlock..."); }
        _codeBlock = _codeBlock.parent;
    }
}
