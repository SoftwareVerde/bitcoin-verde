package com.softwareverde.bitcoin.transaction.script.runner;

import org.junit.Assert;
import org.junit.Test;

public class ControlStateTests {

    @Test
    public void should_execute_initially() {
        // Setup
        final ControlState controlState = new ControlState();

        // Action
        final Boolean shouldExecute = controlState.shouldExecute();

        // Assert
        Assert.assertTrue(shouldExecute);
    }

    @Test
    public void should_execute_if_in_true_if() {
        /*
            IF TRUE
                // Should execute...
            ENDIF
         */

        // Setup
        final ControlState controlState = new ControlState();
        controlState.enteredIfBlock(true);

        // Action
        final Boolean shouldExecute = controlState.shouldExecute();

        // Assert
        Assert.assertTrue(shouldExecute);
    }

    @Test
    public void should_not_execute_if_in_false_if() {
        /*
            IF FALSE
                // ...
            ENDIF
         */

        // Setup
        final ControlState controlState = new ControlState();
        controlState.enteredIfBlock(false);

        // Action
        final Boolean shouldExecute = controlState.shouldExecute();

        // Assert
        Assert.assertFalse(shouldExecute);
    }

    @Test
    public void should_execute_if_in_else() {
        /*
            IF FALSE
                // ...
            ELSE
                // Should execute...
            ENDIF
         */

        // Setup
        final ControlState controlState = new ControlState();
        controlState.enteredIfBlock(false);
        controlState.enteredElseBlock();

        // Action
        final Boolean shouldExecute = controlState.shouldExecute();

        // Assert
        Assert.assertTrue(shouldExecute);
    }

    @Test
    public void should_not_execute_nested_else_when_outer_condition_is_false() {
        /*
            IF FALSE
                IF FALSE
                    // ...
                ELSE
                    // ...
                ENDIF
            ENDIF
         */

        // Setup
        final ControlState controlState = new ControlState();
        controlState.enteredIfBlock(false);
        controlState.enteredIfBlock(false); // NOTE: This value should not matter...
        Assert.assertNull(controlState._codeBlock.condition);
        controlState.enteredElseBlock();

        // Action
        final Boolean shouldExecute = controlState.shouldExecute();

        // Assert
        Assert.assertFalse(shouldExecute);
    }

    @Test
    public void should_execute_nested_if_when_outer_condition_is_true() {
        /*
            IF TRUE
                IF TRUE
                    // Should execute...
                ENDIF
            ENDIF
         */

        // Setup
        final ControlState controlState = new ControlState();
        controlState.enteredIfBlock(true);
        controlState.enteredIfBlock(true);

        // Action
        final Boolean shouldExecute = controlState.shouldExecute();

        // Assert
        Assert.assertTrue(shouldExecute);
    }

    @Test
    public void should_execute_nested_else_when_outer_condition_is_true() {
        /*
            IF TRUE
                IF FALSE
                    // ...
                ELSE
                    // Should execute...
                ENDIF
            ENDIF
         */

        // Setup
        final ControlState controlState = new ControlState();
        controlState.enteredIfBlock(true);
        controlState.enteredIfBlock(false);
        controlState.enteredElseBlock();

        // Action
        final Boolean shouldExecute = controlState.shouldExecute();

        // Assert
        Assert.assertTrue(shouldExecute);
    }

    @Test
    public void should_not_execute_doubly_nested_if_when_super_outer_condition_is_false() {
        /*
            IF FALSE
                IF FALSE
                    IF FALSE
                        // ...
                    ENDIF
                ENDIF
            ENDIF
         */

        // Setup
        final ControlState controlState = new ControlState();
        controlState.enteredIfBlock(false);
        controlState.enteredIfBlock(false); // NOTE: This value should not matter...
        controlState.enteredIfBlock(false); // NOTE: This value should not matter...

        // Action
        final Boolean shouldExecute = controlState.shouldExecute();

        // Assert
        Assert.assertFalse(shouldExecute);
    }

    @Test
    public void should_not_execute_doubly_nested_else_when_super_outer_condition_is_false() {
        /*
            IF FALSE
                IF FALSE
                    IF FALSE
                        // ...
                    ELSE
                        // ...
                    ENDIF
                ENDIF
            ENDIF
         */

        // Setup
        final ControlState controlState = new ControlState();
        controlState.enteredIfBlock(false);
        controlState.enteredIfBlock(false); // NOTE: This value should not matter...
        controlState.enteredIfBlock(false); // NOTE: This value should not matter...
        controlState.enteredElseBlock();

        // Action
        final Boolean shouldExecute = controlState.shouldExecute();

        // Assert
        Assert.assertFalse(shouldExecute);
    }

    @Test
    public void should_execute_else_after_doubly_nested_if_super_outer_condition_is_false() {
        /*
            IF FALSE
                IF FALSE
                    IF FALSE
                        // ...
                    ELSE
                        // ...
                    ENDIF
                ENDIF
            ELSE
                // Should execute...
            ENDIF
         */

        // Setup
        final ControlState controlState = new ControlState();
        controlState.enteredIfBlock(false);
        controlState.enteredIfBlock(false); // NOTE: This value should not matter...
        controlState.enteredIfBlock(false); // NOTE: This value should not matter...
        controlState.enteredElseBlock();
        controlState.exitedCodeBlock();
        controlState.exitedCodeBlock();
        controlState.enteredElseBlock();

        // Action
        final Boolean shouldExecute = controlState.shouldExecute();

        // Assert
        Assert.assertTrue(shouldExecute);
    }
}
