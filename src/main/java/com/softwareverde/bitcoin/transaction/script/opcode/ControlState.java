package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.transaction.script.opcode.controlstate.CodeBlock;
import com.softwareverde.bitcoin.transaction.script.opcode.controlstate.CodeBlockType;

import java.util.LinkedList;

public class ControlState {
    protected final LinkedList<CodeBlock> _codeBlocks = new LinkedList<CodeBlock>();

    public Boolean isInCodeBlock() {
        return (! _codeBlocks.isEmpty());
    }

    public CodeBlock getCodeBlock() {
        if (_codeBlocks.isEmpty()) { return null; }
        return _codeBlocks.getLast();
    }

    public Boolean shouldExecute() {
        if (_codeBlocks.isEmpty()) { return true; }

        final CodeBlock codeBlock = _codeBlocks.getLast();
        return codeBlock.condition;
    }

    public void enteredIfBlock(final Boolean conditionValue) {
        final Boolean newCodeBlockConditionValue;
        {
            if (_codeBlocks.isEmpty()) {
                newCodeBlockConditionValue = conditionValue;
            }
            else {
                final CodeBlock codeBlock = _codeBlocks.getLast();
                newCodeBlockConditionValue = (codeBlock.condition ? conditionValue : false);
            }
        }

        _codeBlocks.addLast(new CodeBlock(CodeBlockType.IF, newCodeBlockConditionValue));
    }

    public void enteredElseBlock() {
        final CodeBlock ifCodeBlock = _codeBlocks.removeLast();
        _codeBlocks.addLast(new CodeBlock(CodeBlockType.ELSE, (! ifCodeBlock.condition)));
    }

    public void exitedCodeBlock() {
        _codeBlocks.removeLast();
    }
}
