package io.codechicken.diffpatch.util;

import net.covers1624.quack.collection.FastStream;
import net.covers1624.quack.util.SneakyUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public static void deleteFolder(Path folder) throws IOException {
        try (Stream<Path> stream = Files.walk(folder)) {
            stream.sorted(Comparator.reverseOrder()).forEach(SneakyUtils.sneak(Files::delete));
        }
    }

    public static Map<String, Path> indexChildren(Path toIndex) throws IOException {
        return indexChildren(toIndex, "");
    }

    public static Map<String, Path> indexChildren(Path toIndex, String prefix) throws IOException {
        if (!prefix.isEmpty()) {
            toIndex = toIndex.resolve(prefix);
        }
        try (Stream<Path> stream = Files.walk(toIndex)) {
            Path finalToIndex = toIndex;
            return stream.filter(Files::isRegularFile)
                    .collect(Collectors.toMap(e -> stripStart('/', finalToIndex.relativize(e).toString().replace("\\", "/")), Function.identity()));
        }
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
