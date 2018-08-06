package com.softwareverde.bitcoin.transaction.script.opcode.controlstate;

public class CodeBlock {
    public final Boolean condition;
    public final CodeBlockType type;
    public CodeBlock parent = null;

    public CodeBlock(final CodeBlockType type, final Boolean condition) {
        this.type = type;
        this.condition = condition;
    }
}
