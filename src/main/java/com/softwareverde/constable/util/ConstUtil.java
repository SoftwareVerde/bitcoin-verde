package com.softwareverde.constable.util;

import com.softwareverde.constable.list.List;

public class ConstUtil {

    /**
     * Casts a list of child-types to a list of parent-types.
     *  NOTE: This covariance is normally a dangerous thing, however List is an immutable interface.
     *      The immutability of List ensures that the recipient of this down-casted list cannot add invalid types to
     *      the original instance.
     *
     *  Example:
     *      interface ChildType extends ParentType { }
     *      final MutableList<ChildType> mutableList = new MutableList<ChildType>();
     *      final List<ParentType> list = ConstUtil.downcastList(mutableList); // NOTE: list is effectively read-only.
     */
    @SuppressWarnings("unchecked")
    public static <S, T extends S> List<S> downcastList(final List<T> list) {
        return (List<S>) (list);
    }

    protected ConstUtil() { }
}
