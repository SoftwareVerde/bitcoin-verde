package com.softwareverde.util;

import org.junit.Assert;
import org.junit.Test;

public class CircleBufferTests {
    @Test
    public void should_append_items_until_full_then_get_items_then_return_null() {
        // Setup
        final CircleBuffer<Integer> circleBuffer = new CircleBuffer<Integer>(3);

        final Integer[] poppedItems = new Integer[4];
        final Integer size;

        // Action
        circleBuffer.pushItem(0);
        circleBuffer.pushItem(1);
        circleBuffer.pushItem(2);
        size = circleBuffer.getItemCount();

        poppedItems[0] = circleBuffer.popItem();
        poppedItems[1] = circleBuffer.popItem();
        poppedItems[2] = circleBuffer.popItem();
        poppedItems[3] = circleBuffer.popItem(); // Should be null...

        // Assert
        Assert.assertEquals(0, poppedItems[0].intValue());
        Assert.assertEquals(1, poppedItems[1].intValue());
        Assert.assertEquals(2, poppedItems[2].intValue());
        Assert.assertNull(poppedItems[3]);
        Assert.assertEquals(3, size.intValue());
    }

    @Test
    public void should_overwrite_oldest_value_after_overflow() {
        // Setup
        final CircleBuffer<Integer> circleBuffer = new CircleBuffer<Integer>(3);

        final Integer[] poppedItems = new Integer[4];
        final Integer size;

        // Action
        circleBuffer.pushItem(0);
        circleBuffer.pushItem(1);
        circleBuffer.pushItem(2);
        circleBuffer.pushItem(3); // Overflows and overwrites item at index 0...
        size = circleBuffer.getItemCount();

        poppedItems[0] = circleBuffer.popItem();
        poppedItems[1] = circleBuffer.popItem();
        poppedItems[2] = circleBuffer.popItem();
        poppedItems[3] = circleBuffer.popItem(); // Should be null...

        // Assert
        Assert.assertEquals(1, poppedItems[0].intValue());
        Assert.assertEquals(2, poppedItems[1].intValue());
        Assert.assertEquals(3, poppedItems[2].intValue());
        Assert.assertNull(poppedItems[3]);
        Assert.assertEquals(3, size.intValue());
    }

    @Test
    public void should_overwrite_oldest_value_after_lapped_overflow() {
        // Setup
        final CircleBuffer<Integer> circleBuffer = new CircleBuffer<Integer>(3);

        final Integer[] poppedItems = new Integer[4];
        final Integer size;

        // Action
        circleBuffer.pushItem(0);
        circleBuffer.pushItem(1);
        circleBuffer.pushItem(2);
        circleBuffer.pushItem(3); // Overflows and overwrites item at index 0...
        circleBuffer.pushItem(4); // Overwrites item at index 1...
        circleBuffer.pushItem(5); // Overwrites item at index 2...
        circleBuffer.pushItem(6); // Overwrites item at index 0...
        circleBuffer.pushItem(7); // Overwrites item at index 1...
        circleBuffer.pushItem(8); // Overwrites item at index 2...
        size = circleBuffer.getItemCount();

        poppedItems[0] = circleBuffer.popItem();
        poppedItems[1] = circleBuffer.popItem();
        poppedItems[2] = circleBuffer.popItem();
        poppedItems[3] = circleBuffer.popItem(); // Should be null...

        // Assert
        Assert.assertEquals(6, poppedItems[0].intValue());
        Assert.assertEquals(7, poppedItems[1].intValue());
        Assert.assertEquals(8, poppedItems[2].intValue());
        Assert.assertNull(poppedItems[3]);
        Assert.assertEquals(3, size.intValue());
    }

    @Test
    public void should_report_correct_size_when_less_than_full() {
        // Setup
        final CircleBuffer<Integer> circleBuffer = new CircleBuffer<Integer>(3);

        final Integer[] poppedItems = new Integer[4];
        final Integer size;

        // Action
        circleBuffer.pushItem(0);
        circleBuffer.pushItem(1);
        size = circleBuffer.getItemCount();

        // Assert
        Assert.assertEquals(2, size.intValue());
    }

    @Test
    public void should_return_correct_values_when_reading_before_overwriting() {
        // Setup
        final CircleBuffer<Integer> circleBuffer = new CircleBuffer<Integer>(3);

        final Integer[] poppedItems = new Integer[4];
        final Integer size;

        // Action
        circleBuffer.pushItem(0);
        poppedItems[0] = circleBuffer.popItem();
        circleBuffer.pushItem(1);
        poppedItems[1] = circleBuffer.popItem();
        circleBuffer.pushItem(2);
        poppedItems[2] = circleBuffer.popItem();
        circleBuffer.pushItem(3);
        size = circleBuffer.getItemCount();
        poppedItems[3] = circleBuffer.popItem();


        // Assert
        Assert.assertEquals(0, poppedItems[0].intValue());
        Assert.assertEquals(1, poppedItems[1].intValue());
        Assert.assertEquals(2, poppedItems[2].intValue());
        Assert.assertEquals(3, poppedItems[3].intValue());
        Assert.assertEquals(1, size.intValue());
    }
}
