package com.softwareverde.bitcoin.transaction.script.opcode;

import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.chain.time.ImmutableMedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeUpgradeSchedule;
import com.softwareverde.bitcoin.transaction.script.runner.ControlState;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.stack.Stack;
import com.softwareverde.bitcoin.transaction.script.stack.Value;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import org.junit.Assert;
import org.junit.Test;

public class StringOperationTests extends UnitTest {

    @Test
    public void OP_REVERSEBYTES_should_fail_if_HF20200515_not_active() {
        // Setup
        final StringOperation opReverseBytes = StringOperation.REVERSE_BYTES;

        final Stack stack = new Stack();
        final ControlState controlState = new ControlState();
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
        final MutableTransactionContext context = new MutableTransactionContext(upgradeSchedule);

        context.setMedianBlockTime(ImmutableMedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        stack.push(Value.ZERO);

        // Action
        final Boolean result = opReverseBytes.applyTo(stack, controlState, context);

        // Assert
        Assert.assertFalse(result);
    }

    @Test
    public void OP_REVERSEBYTES_should_fail_if_stack_is_empty() {
        // Setup
        final StringOperation opReverseBytes = StringOperation.REVERSE_BYTES;

        final Stack stack = new Stack();
        final ControlState controlState = new ControlState();
        final FakeUpgradeSchedule upgradeSchedule = new FakeUpgradeSchedule(new CoreUpgradeSchedule());
        upgradeSchedule.setReverseBytesOperationEnabled(true);
        final MutableTransactionContext context = new MutableTransactionContext(upgradeSchedule);

        // Action
        final Boolean result = opReverseBytes.applyTo(stack, controlState, context);

        // Assert
        Assert.assertFalse(result);
    }

    @Test
    public void OP_REVERSEBYTES_should_pass_with_value_zero() {
        // Setup
        final StringOperation opReverseBytes = StringOperation.REVERSE_BYTES;

        final Stack stack = new Stack();
        final ControlState controlState = new ControlState();
        final FakeUpgradeSchedule upgradeSchedule = new FakeUpgradeSchedule(new CoreUpgradeSchedule());
        upgradeSchedule.setReverseBytesOperationEnabled(true);
        final MutableTransactionContext context = new MutableTransactionContext(upgradeSchedule);

        stack.push(Value.ZERO);

        // Action
        final Boolean result = opReverseBytes.applyTo(stack, controlState, context);

        // Assert
        Assert.assertTrue(result);
        Assert.assertEquals(Value.ZERO, stack.pop());
    }


    @Test
    public void OP_REVERSEBYTES_should_pass_with_value_0xDEAD() {
        // Setup
        final StringOperation opReverseBytes = StringOperation.REVERSE_BYTES;

        final Stack stack = new Stack();
        final ControlState controlState = new ControlState();
        final FakeUpgradeSchedule upgradeSchedule = new FakeUpgradeSchedule(new CoreUpgradeSchedule());
        upgradeSchedule.setReverseBytesOperationEnabled(true);
        final MutableTransactionContext context = new MutableTransactionContext(upgradeSchedule);

        stack.push(Value.fromBytes(ByteArray.fromHexString("DEAD")));

        // Action
        final Boolean result = opReverseBytes.applyTo(stack, controlState, context);

        // Assert
        Assert.assertTrue(result);
        Assert.assertEquals(Value.fromBytes(ByteArray.fromHexString("ADDE")), stack.pop());
    }

    @Test
    public void OP_REVERSEBYTES_should_pass_with_value_0xDEADA1() {
        // Setup
        final StringOperation opReverseBytes = StringOperation.REVERSE_BYTES;

        final Stack stack = new Stack();
        final ControlState controlState = new ControlState();
        final FakeUpgradeSchedule upgradeSchedule = new FakeUpgradeSchedule(new CoreUpgradeSchedule());
        upgradeSchedule.setReverseBytesOperationEnabled(true);
        final MutableTransactionContext context = new MutableTransactionContext(upgradeSchedule);

        stack.push(Value.fromBytes(ByteArray.fromHexString("DEADA1")));

        // Action
        final Boolean result = opReverseBytes.applyTo(stack, controlState, context);

        // Assert
        Assert.assertTrue(result);
        Assert.assertEquals(Value.fromBytes(ByteArray.fromHexString("A1ADDE")), stack.pop());
    }

    @Test
    public void OP_REVERSEBYTES_should_pass_with_value_0xDEADBEEF() {
        // Setup
        final StringOperation opReverseBytes = StringOperation.REVERSE_BYTES;

        final Stack stack = new Stack();
        final ControlState controlState = new ControlState();
        final FakeUpgradeSchedule upgradeSchedule = new FakeUpgradeSchedule(new CoreUpgradeSchedule());
        upgradeSchedule.setReverseBytesOperationEnabled(true);
        final MutableTransactionContext context = new MutableTransactionContext(upgradeSchedule);

        stack.push(Value.fromBytes(ByteArray.fromHexString("DEADBEEF")));

        // Action
        final Boolean result = opReverseBytes.applyTo(stack, controlState, context);

        // Assert
        Assert.assertTrue(result);
        Assert.assertEquals(Value.fromBytes(ByteArray.fromHexString("EFBEADDE")), stack.pop());
    }

    @Test
    public void OP_REVERSEBYTES_should_pass_with_value_0x123456() {
        // Setup
        final StringOperation opReverseBytes = StringOperation.REVERSE_BYTES;

        final Stack stack = new Stack();
        final ControlState controlState = new ControlState();
        final FakeUpgradeSchedule upgradeSchedule = new FakeUpgradeSchedule(new CoreUpgradeSchedule());
        upgradeSchedule.setReverseBytesOperationEnabled(true);
        final MutableTransactionContext context = new MutableTransactionContext(upgradeSchedule);

        stack.push(Value.fromBytes(ByteArray.fromHexString("123456")));

        // Action
        final Boolean result = opReverseBytes.applyTo(stack, controlState, context);

        // Assert
        Assert.assertTrue(result);
        Assert.assertEquals(Value.fromBytes(ByteArray.fromHexString("563412")), stack.pop());
    }

    @Test
    public void OP_REVERSEBYTES_should_pass_with_values_for_range_0_520() {
        // for all n in [0; 520]: {i mod 256 | i < n} OP_REVERSEBYTES -> {(n - i - 1) mod 256 | i < n}

        // Setup
        final StringOperation opReverseBytes = StringOperation.REVERSE_BYTES;

        final Stack stack = new Stack();
        final ControlState controlState = new ControlState();
        final FakeUpgradeSchedule upgradeSchedule = new FakeUpgradeSchedule(new CoreUpgradeSchedule());
        upgradeSchedule.setReverseBytesOperationEnabled(true);
        final MutableTransactionContext context = new MutableTransactionContext(upgradeSchedule);

        for (int n = 0; n < 520; ++n) {
            stack.clearStack();

            final MutableByteArray pushedValue = new MutableByteArray(n);
            for (int i = 0; i < n; ++i) {
                pushedValue.setByte(i, (byte) (i % 256L));
            }

            final MutableByteArray expectedValue = new MutableByteArray(n);
            for (int i = 0; i < n; ++i) {
                expectedValue.setByte(i, (byte) ((n - i  - 1)% 256L));
            }

            stack.push(Value.fromBytes(pushedValue));

            // Action
            final Boolean result = opReverseBytes.applyTo(stack, controlState, context);
            final Value actualValue = stack.pop();

            Assert.assertTrue(result);
            Assert.assertEquals(Value.fromBytes(expectedValue), actualValue);
        }
    }

    @Test
    public void OP_REVERSEBYTES_should_pass_with_palindrome_values_for_range_0_520() {
        // for all n in [0; 520]: {(if (i < (n + 1) / 2) then (i) else (n - i - 1)) % 256) | i < n} OP_DUP OP_REVERSEBYTES OP_EQUAL -> OP_TRUE

        // Setup
        final StringOperation opReverseBytes = StringOperation.REVERSE_BYTES;

        final Stack stack = new Stack();
        final ControlState controlState = new ControlState();
        final FakeUpgradeSchedule upgradeSchedule = new FakeUpgradeSchedule(new CoreUpgradeSchedule());
        upgradeSchedule.setReverseBytesOperationEnabled(true);
        final MutableTransactionContext context = new MutableTransactionContext(upgradeSchedule);

        for (int n = 0; n < 520; ++n) {
            stack.clearStack();

            final MutableByteArray pushedValue = new MutableByteArray(n);
            for (int i = 0; i < n; ++i) {
                final int value;
                if (i < (n + 1L) / 2L) {
                    value = i;
                }
                else {
                    value = ((n - i - 1) % 256);
                }
                pushedValue.setByte(i, (byte) value);
            }

            final ByteArray expectedValue = pushedValue.asConst();

            stack.push(Value.fromBytes(pushedValue));

            // Action
            final Boolean result = opReverseBytes.applyTo(stack, controlState, context);
            final Value actualValue = stack.pop();

            Assert.assertTrue(result);
            Assert.assertEquals(Value.fromBytes(expectedValue), actualValue);
        }
    }
}
