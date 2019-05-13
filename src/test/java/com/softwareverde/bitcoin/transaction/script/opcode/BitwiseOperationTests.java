package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.bip.HF20181115SV;
import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class BitwiseOperationTests {
    protected void _executeShift(final Opcode opcode, final String startingValueHexString, final Long bitShiftCount, final String expectedValueHexString) {
        // Setup
        final BitwiseOperation bitwiseOperation = new BitwiseOperation(opcode);
        final Stack stack = new Stack();

        stack.push(Value.fromBytes(HexUtil.hexStringToByteArray(startingValueHexString)));
        stack.push(Value.fromInteger(bitShiftCount));

        final MutableContext context = new MutableContext();
        context.setBlockHeight(556767L);
        final ControlState controlState = new ControlState();

        // Action
        final Boolean wasSuccess = bitwiseOperation.applyTo(stack, controlState, context);

        // Assert
        Assert.assertTrue(wasSuccess);
        final Value value = stack.pop();
        Assert.assertEquals(MutableByteArray.wrap(HexUtil.hexStringToByteArray(expectedValueHexString)), value);
    }

    @Test
    public void should_shift_bits_left() {
        if (! HF20181115SV.isEnabled(Long.MAX_VALUE)) { return; } // If BSV is disabled, do not execute....

        _executeShift(Opcode.SHIFT_LEFT, "FFFF", 0L, "FFFF");
        _executeShift(Opcode.SHIFT_LEFT, "FFFF", 1L, "FFFE");
        _executeShift(Opcode.SHIFT_LEFT, "FFFF", 2L, "FFFC");
        _executeShift(Opcode.SHIFT_LEFT, "FFFF", 3L, "FFF8");
        _executeShift(Opcode.SHIFT_LEFT, "FFFF", 4L, "FFF0");
        _executeShift(Opcode.SHIFT_LEFT, "FFFF", 5L, "FFE0");
        _executeShift(Opcode.SHIFT_LEFT, "FFFF", 7L, "FF80");
        _executeShift(Opcode.SHIFT_LEFT, "FFFF", 8L, "FF00");
        _executeShift(Opcode.SHIFT_LEFT, "FFFF", 11L, "F800");
        _executeShift(Opcode.SHIFT_LEFT, "FFFF", 12L, "F000");
        _executeShift(Opcode.SHIFT_LEFT, "FFFF", 15L, "8000");
        _executeShift(Opcode.SHIFT_LEFT, "FFFF", 16L, "0000");
        _executeShift(Opcode.SHIFT_LEFT, "FFFF", 17L, "0000");
        _executeShift(Opcode.SHIFT_LEFT, "FFFF", (long) Integer.MAX_VALUE, "0000");
        _executeShift(Opcode.SHIFT_LEFT, "AAAAAAAAAAAAAAAAAAAAAAAA", 48L, "AAAAAAAAAAAA000000000000");
    }

    @Test
    public void should_shift_bits_right() {
        if (! HF20181115SV.isEnabled(Long.MAX_VALUE)) { return; } // If BSV is disabled, do not execute....

        _executeShift(Opcode.SHIFT_RIGHT, "FFFF", 0L, "FFFF");
        _executeShift(Opcode.SHIFT_RIGHT, "FFFF", 1L, "7FFF");
        _executeShift(Opcode.SHIFT_RIGHT, "FFFF", 2L, "3FFF");
        _executeShift(Opcode.SHIFT_RIGHT, "FFFF", 3L, "1FFF");
        _executeShift(Opcode.SHIFT_RIGHT, "FFFF", 4L, "0FFF");
        _executeShift(Opcode.SHIFT_RIGHT, "FFFF", 5L, "07FF");
        _executeShift(Opcode.SHIFT_RIGHT, "FFFF", 7L, "01FF");
        _executeShift(Opcode.SHIFT_RIGHT, "FFFF", 8L, "00FF");
        _executeShift(Opcode.SHIFT_RIGHT, "FFFF", 11L, "001F");
        _executeShift(Opcode.SHIFT_RIGHT, "FFFF", 12L, "000F");
        _executeShift(Opcode.SHIFT_RIGHT, "FFFF", 15L, "0001");
        _executeShift(Opcode.SHIFT_RIGHT, "FFFF", 16L, "0000");
        _executeShift(Opcode.SHIFT_RIGHT, "FFFF", 17L, "0000");
        _executeShift(Opcode.SHIFT_RIGHT, "FFFF", (long) Integer.MAX_VALUE, "0000");
        _executeShift(Opcode.SHIFT_RIGHT, "AAAAAAAAAAAAAAAAAAAAAAAA", 48L, "000000000000AAAAAAAAAAAA");
    }
}
