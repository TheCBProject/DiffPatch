package codechicken.diffpatch.util;

import java.util.*;

/**
 * Created by covers1624 on 11/8/20.
 */
public class FileCollector {

    private final Map<String, List<String>> files = new HashMap<>();

    /**
     * Adds a List of lines to the collector.
     *
     * @param name  The file name.
     * @param lines The lines in the file.
     * @return Returns true if lines were added.
     */
    public boolean consume(String name, List<String> lines) {
        return files.put(name, Collections.unmodifiableList(lines)) == null;
    }

    public Map<String, List<String>> get() {
        return Collections.unmodifiableMap(files);
    }

    public Set<String> keySet() {
        return get().keySet();
    }

    public Collection<List<String>> values() {
        return get().values();
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    public List<String> getSingleFile() {
        if (files.isEmpty()) {
            return Collections.emptyList();
        }

        if (files.size() != 1) {
            throw new IllegalStateException("Expected 1 file in FileCollector, got: " + files.size());
        }
        return files.entrySet().iterator().next().getValue();
    }

}
