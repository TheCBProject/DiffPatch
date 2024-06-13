package io.codechicken.diffpatch.util;

import net.covers1624.quack.collection.FastStream;

import java.util.Set;

/**
 * Created by covers1624 on 19/7/20.
 */
public class Utils {

    public static String stripStart(char start, String str) {
        if (!str.isEmpty() && str.charAt(0) == start) {
            return str.substring(1);
        }
        return str;
    }

    public static Set<String> filterPrefixed(Set<String> toFilter, String[] filters) {
        if (filters.length == 0) return toFilter;

        return FastStream.of(toFilter)
                .filterNot(e -> {
                    for (String s : filters) {
                        if (e.startsWith(s)) {
                            return true;
                        }
                    }
                    return false;
                })
                .toSet();
    }
}
