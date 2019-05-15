package com.softwareverde.bitcoin.server.message.type.query.response.hash;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import org.junit.Assert;
import org.junit.Test;

public class InventoryItemTests {
    @Test
    public void identical_inventory_items_should_be_equal() {
        final InventoryItem item0 = new InventoryItem(InventoryItemType.BLOCK, Sha256Hash.fromHexString("000000000000000001EA1109A7F613BC23C4FD83C504FE8110B892551ACF3761"));
        final InventoryItem item1 = new InventoryItem(InventoryItemType.BLOCK, Sha256Hash.fromHexString("000000000000000001EA1109A7F613BC23C4FD83C504FE8110B892551ACF3761"));

        Assert.assertEquals(item0, item1);
        Assert.assertEquals(item0.hashCode(), item1.hashCode());
    }

    @Test
    public void inventory_items_of_different_type_should_not_be_equal() {
        final InventoryItem item0 = new InventoryItem(InventoryItemType.MERKLE_BLOCK, Sha256Hash.fromHexString("000000000000000001EA1109A7F613BC23C4FD83C504FE8110B892551ACF3761"));
        final InventoryItem item1 = new InventoryItem(InventoryItemType.BLOCK, Sha256Hash.fromHexString("000000000000000001EA1109A7F613BC23C4FD83C504FE8110B892551ACF3761"));

        Assert.assertNotEquals(item0, item1);
        Assert.assertNotEquals(item0.hashCode(), item1.hashCode()); // Technically possible to have a collision, but unlikely...
    }

    @Test
    public void inventory_items_of_value_should_not_be_equal() {
        final InventoryItem item0 = new InventoryItem(InventoryItemType.TRANSACTION, Sha256Hash.fromHexString("000000000000000001EA1109A7F613BC23C4FD83C504FE8110B892551ACF3761"));
        final InventoryItem item1 = new InventoryItem(InventoryItemType.TRANSACTION, Sha256Hash.fromHexString("0000000000000000000000000000000000000000000000000000000000000000"));

        Assert.assertNotEquals(item0, item1);
        Assert.assertNotEquals(item0.hashCode(), item1.hashCode()); // Technically possible to have a collision, but unlikely...
    }
}
