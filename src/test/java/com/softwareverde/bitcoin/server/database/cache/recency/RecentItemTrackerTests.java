package com.softwareverde.bitcoin.server.database.cache.recency;

import org.junit.Assert;
import org.junit.Test;

public class RecentItemTrackerTests {

    @Test
    public void should_return_null_when_empty() {
        // Setup
        final RecentItemTracker<String> recentItemTracker = new RecentItemTracker<String>(1024);

        // Action
        final String item = recentItemTracker.getOldestItem();

        // Assert
        Assert.assertNull(item);
    }

    @Test
    public void should_return_item_when_it_is_the_only_item() {
        // Setup
        final RecentItemTracker<String> recentItemTracker = new RecentItemTracker<String>(1024);
        recentItemTracker.markRecent("0");

        // Action
        final String item = recentItemTracker.getOldestItem();

        // Assert
        Assert.assertEquals("0", item);
        Assert.assertEquals(0, recentItemTracker.getSize().intValue());
    }

    @Test
    public void should_return_first_item_when_two_items_inserted() {
        // Setup
        final RecentItemTracker<String> recentItemTracker = new RecentItemTracker<String>(1024);
        recentItemTracker.markRecent("0");
        recentItemTracker.markRecent("1");

        // Action
        final String item = recentItemTracker.getOldestItem();

        // Assert
        Assert.assertEquals("0", item);
        Assert.assertEquals(1, recentItemTracker.getSize().intValue());
    }

    @Test
    public void should_return_second_item_when_first_is_inserted_again() {
        // Setup
        final RecentItemTracker<String> recentItemTracker = new RecentItemTracker<String>(1024);
        recentItemTracker.markRecent("0");
        recentItemTracker.markRecent("1");
        recentItemTracker.markRecent("0");

        // Action
        final String item = recentItemTracker.getOldestItem();

        // Assert
        Assert.assertEquals("1", item);
        Assert.assertEquals(1, recentItemTracker.getSize().intValue());
    }

    @Test
    public void should_return_second_item_when_first_is_inserted_again_2() {
        // Setup
        final RecentItemTracker<String> recentItemTracker = new RecentItemTracker<String>(1024);
        recentItemTracker.markRecent("0");
        recentItemTracker.markRecent("1");
        recentItemTracker.markRecent("2");
        recentItemTracker.markRecent("0");

        // Action
        final String item0 = recentItemTracker.getOldestItem();
        final String item1 = recentItemTracker.getOldestItem();

        // Assert
        Assert.assertEquals("1", item0);
        Assert.assertEquals("2", item1);
        Assert.assertEquals(1, recentItemTracker.getSize().intValue());
    }

    @Test
    public void should_return_oldest_item_after_adding_then_removing_then_adding_items() {
        // Setup
        final RecentItemTracker<String> recentItemTracker = new RecentItemTracker<String>(1024);

        // Action
        recentItemTracker.markRecent("0");  // 0
        recentItemTracker.markRecent("1");  // 0, 1
        recentItemTracker.markRecent("2");  // 0, 1, 2
        recentItemTracker.getOldestItem();        // 1, 2
        recentItemTracker.markRecent("0");  // 1, 2, 0
        recentItemTracker.markRecent("2");  // 1, 0, 2

        // Assert
        Assert.assertEquals("1", recentItemTracker.getOldestItem());
        Assert.assertEquals("0", recentItemTracker.getOldestItem());
        Assert.assertEquals("2", recentItemTracker.getOldestItem());
        Assert.assertEquals(0, recentItemTracker.getSize().intValue());
    }

    @Test
    public void should_return_oldest_item_after_adding_then_removing_then_adding_items_2() {
        // Setup
        final RecentItemTracker<String> recentItemTracker = new RecentItemTracker<String>(1024);

        // Action
        recentItemTracker.markRecent("0");  // 0
        recentItemTracker.markRecent("1");  // 0, 1
        recentItemTracker.markRecent("2");  // 0, 1, 2
        recentItemTracker.getOldestItem();        // 1, 2
        recentItemTracker.markRecent("1");  // 2, 1
        recentItemTracker.markRecent("3");  // 2, 1, 3

        // Assert
        Assert.assertEquals("2", recentItemTracker.getOldestItem());
        Assert.assertEquals("1", recentItemTracker.getOldestItem());
        Assert.assertEquals("3", recentItemTracker.getOldestItem());
        Assert.assertEquals(0, recentItemTracker.getSize().intValue());
    }

}
