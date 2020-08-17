package codechicken.diffpatch.util.archiver;

import codechicken.diffpatch.util.Utils;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A wrapper for an {@link ArchiveInputStream} that indexes and stores
 * each entries content.
 * <p>
 * Created by covers1624 on 19/7/20.
 */
public class ArchiveInputStreamReader implements ArchiveReader {

    private final Map<String, byte[]> archiveIndex = new HashMap<>();
    private final ArchiveInputStream is;

    public ArchiveInputStreamReader(ArchiveInputStream is, String prefix) {
        this.is = is;
        try {
            ArchiveEntry entry;
            while ((entry = is.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String name = Utils.stripStart('/', entry.getName());
                    if (prefix.isEmpty() || entry.getName().startsWith(prefix)) {
                        archiveIndex.put(Utils.stripStart('/', name.substring(prefix.length())), Utils.toBytes(is));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to index archive", e);
        }
    }

    @Override
    public Set<String> getEntries() {
        return Collections.unmodifiableSet(archiveIndex.keySet());
    }

    @Override
    public byte[] getBytes(String entry) {
        return archiveIndex.get(entry);
    }

    @Override
    public void close() throws IOException {
        is.close();
    }
}
