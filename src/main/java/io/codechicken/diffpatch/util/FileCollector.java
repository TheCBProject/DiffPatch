package io.codechicken.diffpatch.util;

import net.covers1624.quack.collection.ColUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Created by covers1624 on 11/8/20.
 */
public class FileCollector {

    private final Map<String, CollectedEntry> files = new LinkedHashMap<>();

    /**
     * Adds a List of lines to the collector.
     *
     * @param name  The file name.
     * @param lines The lines in the file.
     * @return Returns true if lines were added.
     */
    public boolean consume(String name, List<String> lines) {
        if (files.containsKey(name)) return false;

        files.put(name, new LinesCollectedEntry(lines));
        return true;
    }

    /**
     * Add a binary file to the collector.
     *
     * @param name  The file name.
     * @param bytes The bytes to add.
     * @return Returns true if the file was added.
     */
    public boolean consume(String name, byte[] bytes) {
        if (files.containsKey(name)) return false;

        files.put(name, new BinaryCollectedEntry(bytes));
        return true;
    }

    public Map<String, CollectedEntry> get() {
        return Collections.unmodifiableMap(files);
    }

    public Set<String> keySet() {
        return get().keySet();
    }

    public Collection<CollectedEntry> values() {
        return get().values();
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    public @Nullable CollectedEntry getSingleFile() {
        if (files.isEmpty()) return null;

        if (files.size() != 1) {
            throw new IllegalStateException("Expected 1 file in FileCollector, got: " + files.size());
        }

        return ColUtils.only(files.values());
    }

    public abstract static class CollectedEntry {

        public abstract byte[] toBytes(String lineEnding, boolean emptyNewline) throws IOException;
    }

    public static class LinesCollectedEntry extends CollectedEntry {

        public final List<String> lines;

        public LinesCollectedEntry(List<String> lines) {
            this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        }

        @Override
        public byte[] toBytes(String lineEnding, boolean emptyNewline) throws IOException {
            String file = String.join(lineEnding, lines);
            if (emptyNewline) {
                file += lineEnding;
            }
            return file.getBytes(StandardCharsets.UTF_8);
        }
    }

    public static class BinaryCollectedEntry extends CollectedEntry {

        public final byte[] bytes;

        public BinaryCollectedEntry(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public byte[] toBytes(String lineEnding, boolean emptyNewline) throws IOException {
            return bytes;
        }
    }
}
