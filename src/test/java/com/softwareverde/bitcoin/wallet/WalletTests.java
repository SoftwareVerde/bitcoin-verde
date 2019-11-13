package com.softwareverde.bitcoin.wallet;

import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.Tuple;
import org.junit.Assert;
import org.junit.Test;

public class WalletTests {

    protected MutableList<Tuple<String, Long>> _setupTuples() {
        final MutableList<Tuple<String, Long>> sortedTuples = new MutableList<Tuple<String, Long>>();
        sortedTuples.add(new Tuple<String, Long>("One", 1L));
        sortedTuples.add(new Tuple<String, Long>("Two", 2L));
        sortedTuples.add(new Tuple<String, Long>("Three", 3L));
        sortedTuples.add(new Tuple<String, Long>("Four", 4L));
        sortedTuples.add(new Tuple<String, Long>("Five", 5L));
        sortedTuples.add(new Tuple<String, Long>("Six", 6L));
        sortedTuples.add(new Tuple<String, Long>("Seven", 7L));
        sortedTuples.add(new Tuple<String, Long>("Eight", 8L));
        sortedTuples.add(new Tuple<String, Long>("Nine", 9L));
        sortedTuples.add(new Tuple<String, Long>("Ten", 10L));
        return sortedTuples;
    }

    @Test
    public void should_select_closest_tuple_from_list_0() {
        // Setup
        final MutableList<Tuple<String, Long>> sortedTuples = _setupTuples();
        final Long desiredResult = 0L;

        // Action
        final Tuple<String, Long> selectedTuple = Wallet.removeClosestTupleAmount(sortedTuples, desiredResult);

        // Assert
        Assert.assertEquals(Long.valueOf(1L), selectedTuple.second);
        Assert.assertEquals(9, sortedTuples.getSize());
    }

    @Test
    public void should_select_closest_tuple_from_list_6() {
        // Setup
        final MutableList<Tuple<String, Long>> sortedTuples = _setupTuples();
        final Long desiredResult = 6L;

        // Action
        final Tuple<String, Long> selectedTuple = Wallet.removeClosestTupleAmount(sortedTuples, desiredResult);

        // Assert
        Assert.assertEquals(desiredResult, selectedTuple.second);
        Assert.assertEquals(9, sortedTuples.getSize());
    }

    @Test
    public void should_select_closest_tuple_from_list_7() {
        // Setup
        final MutableList<Tuple<String, Long>> sortedTuples = _setupTuples();
        final Long desiredResult = 7L;

        // Action
        final Tuple<String, Long> selectedTuple = Wallet.removeClosestTupleAmount(sortedTuples, desiredResult);

        // Assert
        Assert.assertEquals(desiredResult, selectedTuple.second);
        Assert.assertEquals(9, sortedTuples.getSize());
    }

    @Test
    public void should_select_closest_tuple_from_list_8() {
        // Setup
        final MutableList<Tuple<String, Long>> sortedTuples = _setupTuples();
        final Long desiredResult = 8L;

        // Action
        final Tuple<String, Long> selectedTuple = Wallet.removeClosestTupleAmount(sortedTuples, desiredResult);

        // Assert
        Assert.assertEquals(desiredResult, selectedTuple.second);
        Assert.assertEquals(9, sortedTuples.getSize());
    }

    @Test
    public void should_select_closest_tuple_from_list_20() {
        // Setup
        final MutableList<Tuple<String, Long>> sortedTuples = _setupTuples();
        final Long desiredResult = 20L;

        // Action
        final Tuple<String, Long> selectedTuple = Wallet.removeClosestTupleAmount(sortedTuples, desiredResult);

        // Assert
        Assert.assertEquals(Long.valueOf(10L), selectedTuple.second);
        Assert.assertEquals(9, sortedTuples.getSize());
    }

    @Test
    public void should_select_two_closest_tuples_from_list() {
        // Setup
        final MutableList<Tuple<String, Long>> sortedTuples = _setupTuples();

        // Action
        final Tuple<String, Long> selectedTuple0 = Wallet.removeClosestTupleAmount(sortedTuples, 5L);
        final Tuple<String, Long> selectedTuple1 = Wallet.removeClosestTupleAmount(sortedTuples, 5L);

        // Assert
        Assert.assertEquals(Long.valueOf(5L), selectedTuple0.second);
        Assert.assertEquals(Long.valueOf(6L), selectedTuple1.second);
        Assert.assertEquals(8, sortedTuples.getSize());
    }
}
