package codechicken.diffpatch.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by covers1624 on 19/7/20.
 */
public class Utils {

    //32k buffer.
    private static final ThreadLocal<byte[]> bufferCache = ThreadLocal.withInitial(() -> new byte[32 * 1024]);

    /**
     * Copies the content of an InputStream to an OutputStream.
     *
     * @param is The InputStream.
     * @param os The OutputStream.
     * @throws IOException If something is bork.
     */
    public static void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = bufferCache.get();
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
    }

    /**
     * Reads an InputStream to a byte array.
     *
     * @param is The InputStream.
     * @return The bytes.
     * @throws IOException If something is bork.
     */
    public static byte[] toBytes(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        copy(is, os);
        return os.toByteArray();
    }

    public static List<String> readAll(byte[] bytes) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes)))) {
            return reader.lines().collect(Collectors.toList());
        }
    }

    public static String stripStart(char start, String str) {
        if (!str.isEmpty() && str.charAt(0) == start) {
            return str.substring(1);
        }
        return str;
    }

    public static String stripStart(String start, String str) {
        if (str.startsWith(start)) {
            return str.substring(str.length());
        }
        return str;
    }

    public static String ensureStartsWith(char start, String str) {
        if (str.isEmpty() || str.charAt(0) != start) {
            return start + str;
        }
        return str;
    }

    public static String ensureStartsWith(String start, String str) {
        if (!str.startsWith(start)) {
            return start + str;
        }
        return str;
    }

    public static OutputStream protectClose(OutputStream os) {
        return new FilterOutputStream(os) {
            @Override
            public void close() {
            }
        };
    }

    /**
     * Deletes the given directory and all containing files.
     *
     * @param directory The directory to delete.
     */
    public static void deleteFolder(File directory) {
        Deque<File> toDelete = new ArrayDeque<>();
        toDelete.push(directory);
        while (!toDelete.isEmpty()) {
            File peek = toDelete.peek();
            if (peek.isDirectory()) {
                File[] list = peek.listFiles();
                if (list == null || list.length == 0) {
                    toDelete.pop().delete();
                } else if (list != null) {
                    for (File f : list) {
                        toDelete.push(f);
                    }
                }
            } else {
                toDelete.pop().delete();
            }
        }
    }

    public static void deleteFolder(Path folder) throws IOException {
        try (Stream<Path> stream = Files.walk(folder)) {
            stream.sorted(Comparator.reverseOrder()).forEach(sneakC(Files::delete));
        }
    }

    public static Path makeParentDirs(Path path) {
        if (Files.notExists(path.getParent())) {
            sneaky(() -> {
                Files.createDirectories(path.getParent());
            });
        }
        return path;
    }

    public static Runnable sneakR(ThrowingRunnable<Throwable> tr) {
        return () -> sneaky(tr);
    }

    public static <T, R> Function<T, R> sneakF(ThrowingFunction<T, R, Throwable> tf) {
        return e -> sneaky(() -> tf.apply(e));
    }

    public static <T> Consumer<T> sneakC(ThrowingConsumer<T, Throwable> tc) {
        return t -> {
            try {
                tc.accept(t);
            } catch (Throwable th) {
                throwUnchecked(th);
            }
        };
    }

    public static void sneaky(ThrowingRunnable<Throwable> tr) {
        try {
            tr.run();
        } catch (Throwable t) {
            throwUnchecked(t);
        }
    }

    public static <T> T sneaky(ThrowingProducer<T, Throwable> tp) {
        try {
            return tp.get();
        } catch (Throwable t) {
            throwUnchecked(t);
            return null;//Un possible
        }
    }

    /**
     * Throws an exception without compiler warnings.
     */
    @SuppressWarnings ("unchecked")
    public static <T extends Throwable> void throwUnchecked(Throwable t) throws T {
        throw (T) t;
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
            return stream.filter(Files::isRegularFile)//
                    .collect(Collectors.toMap(e -> stripStart('/', finalToIndex.relativize(e).toString()), Function.identity()));
        }
    }

    public interface ThrowingConsumer<T, E extends Throwable> {

        void accept(T thing) throws E;
    }

    public interface ThrowingFunction<T, R, E extends Throwable> {

        R apply(T thing) throws E;
    }

    public interface ThrowingProducer<T, E extends Throwable> {

        T get() throws E;
    }

    public interface ThrowingRunnable<E extends Throwable> {

        void run() throws E;
    }
}
